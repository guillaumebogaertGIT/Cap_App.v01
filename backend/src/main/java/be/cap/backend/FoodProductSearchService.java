package be.cap.backend;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Name/brand discovery only; nutrition is fetched separately after selection. */
@Service
public class FoodProductSearchService {
    public record Hit(String barcode, String name, String brand,String source) {
        public Hit(String barcode,String name,String brand){this(barcode,name,brand,"openfoodfacts");}
    }
    public record Results(List<Hit> products, int page, boolean hasMore) {}
    record ProviderResponse(int status, String body) {}
    @FunctionalInterface interface Gateway { ProviderResponse fetch(String query, int page) throws IOException, InterruptedException; }
    private record Cached(Results results, Instant expires) {}
    private final Gateway gateway;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Map<String,Cached> cache = new LinkedHashMap<>();
    private final Deque<Instant> requests = new ArrayDeque<>();
    private Instant blockedUntil = Instant.MIN;

    @org.springframework.beans.factory.annotation.Autowired
    public FoodProductSearchService(@Value("${cap.food-products.user-agent:CAP-Fitness/0.1 (local development)}") String userAgent) {
        var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        gateway=(query,page)->{
            String q=URLEncoder.encode(providerQuery(query),StandardCharsets.UTF_8);
            var uri=URI.create("https://search.openfoodfacts.org/search?q="+q+"&page_size=12&page="+page
                +"&langs=nl,fr,en&fields=code,product_name,product_name_nl,brands,countries_tags");
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("User-Agent",userAgent).header("Accept","application/json").GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ProviderResponse(response.statusCode(),response.body());
        };
        clock=Clock.systemUTC();
    }
    FoodProductSearchService(Gateway gateway,Clock clock){this.gateway=gateway;this.clock=clock;}
    public static String query(String raw) {
        if(raw==null || raw.length()>120)throw invalid();
        String value=raw.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\p{M}\\s]"," ").trim().replaceAll("\\s+"," ");
        if(value.length()<2 || value.length()>80)throw invalid();
        return value;
    }
    // Store names need not occur in product names: search the retailer and its own brands.
    private static final Map<String,List<String>> RETAILERS=Map.ofEntries(
        Map.entry("lidl",List.of("lidl","milbona","lovilio","italiamo","deluxe")),
        Map.entry("aldi",List.of("aldi","milsani")),
        Map.entry("colruyt",List.of("colruyt","boni","everyday")),
        Map.entry("okay",List.of("okay","boni","everyday")),
        Map.entry("spar",List.of("spar","boni","everyday")),
        Map.entry("delhaize",List.of("delhaize","365")),
        Map.entry("carrefour",List.of("carrefour","simpl")),
        Map.entry("albert heijn",List.of("albert heijn","ah")),
        Map.entry("ah",List.of("albert heijn","ah")),
        Map.entry("jumbo",List.of("jumbo")));
    private static final Map<String,String> FOOD_TERMS=Map.ofEntries(
        Map.entry("kaas","(kaas OR fromage OR cheese OR categories_tags:\"en:cheeses\")"),
        Map.entry("melk","(melk OR lait OR milk)"),
        Map.entry("brood","(brood OR pain OR bread)"),
        Map.entry("kip","(kip OR poulet OR chicken)"),
        Map.entry("yoghurt","(yoghurt OR yogurt OR yaourt)"),
        Map.entry("eiwitpoeder","(eiwitpoeder OR proteine OR protein OR whey)"),
        Map.entry("creatine","(creatine OR creatin OR kreatin)"));
    static String providerQuery(String normalized) {
        var terms=new ArrayList<String>();
        var words=new ArrayList<>(List.of(normalized.split(" ")));
        for(int i=0;i<words.size();i++) {
            String token=words.get(i);
            if(token.equals("albert")&&i+1<words.size()&&words.get(i+1).equals("heijn")){token="albert heijn";i++;}
            List<String> brands=RETAILERS.get(token);
            if(brands!=null){
                var alternatives=new ArrayList<String>();
                String store=token.equals("ah")?"albert-heijn":token.replace(' ','-');
                alternatives.add("stores_tags:\""+store+"\"");
                for(String brand:brands)alternatives.add("brands:\""+brand+"\"");
                terms.add("("+String.join(" OR ",alternatives)+")");
            }else terms.add(FOOD_TERMS.getOrDefault(token,token));
        }
        return "countries_tags:\"en:belgium\" AND "+String.join(" AND ",terms);
    }
    private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vul 2 tot 80 tekens van een productnaam of merk in.");}
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Producten zoeken is tijdelijk niet beschikbaar. Probeer opnieuw of gebruik de barcode.");}
    private ResponseStatusException limited(){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Er zijn veel zoekopdrachten. Wacht een minuut en probeer opnieuw.");}

    public synchronized Results search(String raw,int page) {
        String query=query(raw);
        if(page<1 || page>20)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een geldige resultatenpagina.");
        String key=query+"/"+page;Instant now=clock.instant();
        Cached previous=cache.get(key);
        if(previous!=null && now.isBefore(previous.expires()))return previous.results();
        cache.remove(key);
        while(!requests.isEmpty()&&!requests.peekFirst().isAfter(now.minusSeconds(60)))requests.removeFirst();
        if(now.isBefore(blockedUntil)||requests.size()>=8)throw limited();
        requests.addLast(now);
        try {
            var response=gateway.fetch(query,page);
            if(response.status()==429){blockedUntil=now.plusSeconds(60);throw limited();}
            if(response.status()!=200 || response.body()==null || response.body().length()>262144)throw unavailable();
            JsonNode root=mapper.readTree(response.body());
            if(!root.path("hits").isArray() || root.path("timed_out").asBoolean(false)
                || root.path("errors").isArray() && !root.path("errors").isEmpty())throw unavailable();
            var found=new LinkedHashMap<String,Hit>();
            for(JsonNode hit:root.path("hits")) {
                boolean belgian=false;
                for(JsonNode country:hit.path("countries_tags"))if(country.asText("").equals("en:belgium"))belgian=true;
                if(!belgian)continue;
                String code=hit.path("code").asText("");
                try{code=FoodProductService.validBarcode(code);}catch(ResponseStatusException invalid){continue;}
                String name=text(hit.path("product_name_nl"),120);
                if(name.isBlank())name=text(hit.path("product_name"),120);
                if(name.isBlank())continue;
                String brand;
                if(hit.path("brands").isArray()) {
                    var brands=new ArrayList<String>();for(JsonNode item:hit.path("brands"))brands.add(text(item,80));
                    brand=String.join(", ",brands);
                }else brand=text(hit.path("brands"),160);
                found.putIfAbsent(code,new Hit(code,name,brand.substring(0,Math.min(160,brand.length()))));
                if(found.size()==12)break;
            }
            var results=new Results(List.copyOf(found.values()),page,page<20 && root.path("page_count").asInt(0)>page);
            cache.put(key,new Cached(results,now.plusSeconds(600)));
            while(cache.size()>200)cache.remove(cache.keySet().iterator().next());
            return results;
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw unavailable();}
        catch(IOException|tools.jackson.core.JacksonException error){throw unavailable();}
    }
    private String text(JsonNode value,int limit){String text=value.isTextual()?value.asText().trim():"";return text.substring(0,Math.min(limit,text.length()));}
}
