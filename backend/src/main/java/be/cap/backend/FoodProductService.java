package be.cap.backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Read-only Open Food Facts boundary. No diary, personal data or provider writes. */
@Service
public class FoodProductService {
    public record Product(String barcode, String name, String brand, String unit, Double calories,
                          Double proteinG, Double carbsG, Double fatG, boolean complete, String sourceUrl, String source) {
        public Product(String barcode,String name,String brand,String unit,Double calories,Double proteinG,Double carbsG,Double fatG,boolean complete,String sourceUrl) {
            this(barcode,name,brand,unit,calories,proteinG,carbsG,fatG,complete,sourceUrl,"openfoodfacts");
        }
    }
    record ProviderResponse(int status, String body) {}
    @FunctionalInterface interface Gateway { ProviderResponse fetch(String barcode) throws IOException, InterruptedException; }
    private record Cached(Product product, Instant expires) {}
    private final Gateway gateway;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(32, .75f, true);
    private final Deque<Instant> requests = new ArrayDeque<>();
    private Instant blockedUntil = Instant.MIN;

    @org.springframework.beans.factory.annotation.Autowired
    public FoodProductService(@Value("${cap.food-products.user-agent:CAP-Fitness/0.1 (local development)}") String userAgent) {
        var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
        gateway = barcode -> {
            var uri = URI.create("https://world.openfoodfacts.org/api/v3.6/product/" + barcode
                + ".json?cc=be&lc=nl&product_type=food&fields=code,product_name,product_name_nl,brands,nutrition");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("User-Agent", userAgent).header("Accept", "application/json").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            return new ProviderResponse(response.statusCode(), response.body());
        };
        clock = Clock.systemUTC();
    }
    FoodProductService(Gateway gateway, Clock clock) { this.gateway = gateway; this.clock = clock; }

    public static String validBarcode(String raw) {
        String code = raw == null ? "" : raw.trim().replaceAll("[ -]", "");
        if (!code.matches("(?:[0-9]{8}|[0-9]{12,14})")) throw invalid();
        int sum = 0;
        for (int i = code.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight) sum += (code.charAt(i) - '0') * weight;
        if ((10 - sum % 10) % 10 != code.charAt(code.length() - 1) - '0') throw invalid();
        return code;
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Controleer de barcode: gebruik de 8, 12, 13 of 14 cijfers onder de streepjescode.");
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Dit product is niet gevonden. Controleer de barcode of vul je product zelf in."); }
    private ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "De productdatabase is tijdelijk niet bereikbaar. Probeer opnieuw of vul je product zelf in."); }
    private ResponseStatusException limited() { return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Er zijn veel producten opgezocht. Wacht een minuut en probeer opnieuw."); }

    // Serialize cache misses to coalesce repeats and keep the shared provider budget bounded.
    public synchronized Product lookup(String raw) {
        String code = validBarcode(raw);
        Instant now = clock.instant();
        Cached previous = cache.get(code);
        if (previous != null && now.isBefore(previous.expires())) {
            if (previous.product() == null) throw missing();
            return previous.product();
        }
        cache.remove(code);
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(now.minusSeconds(60))) requests.removeFirst();
        if (now.isBefore(blockedUntil) || requests.size() >= 12) throw limited();
        requests.addLast(now);
        try {
            ProviderResponse response = gateway.fetch(code);
            if (response.status() == 429) { blockedUntil = now.plusSeconds(60); throw limited(); }
            if (response.status() == 404) { remember(code, null, now.plusSeconds(600)); throw missing(); }
            if (response.status() != 200 || response.body() == null || response.body().length() > 262144) throw unavailable();
            JsonNode root = mapper.readTree(response.body());
            String result = root.path("result").path("id").asText("");
            if (result.equals("product_not_found") || root.path("status").asText("").equals("0")) {
                remember(code, null, now.plusSeconds(600)); throw missing();
            }
            JsonNode product = root.path("product");
            String providerCode = product.path("code").asText("");
            if (!product.isObject() || !providerCode.matches("(?:[0-9]{8}|[0-9]{12,14})")
                || !code.replaceFirst("^0+", "").equals(providerCode.replaceFirst("^0+", ""))) throw unavailable();
            Product value = normalize(code, product);
            remember(code, value, now.plusSeconds(86400));
            return value;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (IOException | tools.jackson.core.JacksonException error) { throw unavailable(); }
    }
    private void remember(String code, Product product, Instant expires) {
        cache.put(code, new Cached(product, expires));
        while (cache.size() > 500) cache.remove(cache.keySet().iterator().next());
    }
    private String text(JsonNode value, int limit) {
        String text = value.asText("").trim();
        return text.length() > limit ? text.substring(0, limit) : text;
    }
    private Double nutrient(JsonNode set, String name, String unit, double maximum, double factor) {
        JsonNode value = set.path("nutrients").path(name);
        String modifier = value.path("modifier").asText("");
        String providedUnit = value.path("unit").asText("");
        double conversion = unit.equals(providedUnit) ? 1 : unit.equals("g") ? switch (providedUnit) {
            case "mg" -> .001;
            case "µg", "μg", "ug" -> .000001;
            case "kg" -> 1000;
            default -> Double.NaN;
        } : Double.NaN;
        if (!value.path("value").isNumber() || !Double.isFinite(conversion)
            || "estimate".equals(value.path("source").asText("")) || !(modifier.isEmpty() || modifier.equals("="))) return null;
        double number = value.path("value").asDouble() * conversion * factor;
        return Double.isFinite(number) && number >= 0 && number <= maximum ? number : null;
    }
    private Product normalize(String code, JsonNode product) {
        String name = text(product.path("product_name_nl"), 120);
        if (name.isBlank()) name = text(product.path("product_name"), 120);
        String brand = text(product.path("brands"), 160);
        Product best = fromSet(code, name, brand, product.path("nutrition").path("aggregated_set"));
        // A prepared summary must not hide explicitly labelled powder/as-sold data.
        for (JsonNode set : product.path("nutrition").path("input_sets")) {
            String source = set.path("source").asText("");
            if (!source.equals("manufacturer") && !source.equals("packaging")) continue;
            Product candidate = fromSet(code, name, brand, set);
            if (quality(candidate) > quality(best)) best = candidate;
        }
        return best;
    }
    private int quality(Product product) {
        int score = product.complete() ? 100 : 0;
        for (Double value : new Double[]{product.calories(), product.proteinG(), product.carbsG(), product.fatG()}) if (value != null) score++;
        return score;
    }
    private Product fromSet(String code, String name, String brand, JsonNode set) {
        String per = set.path("per").asText("");
        boolean sold = set.path("preparation").asText("").equals("as_sold");
        String unit = !sold ? null : per.equals("100g") ? "g" : per.equals("100ml") ? "ml" : null;
        double factor = 1;
        if (sold && per.equals("serving") && set.path("per_quantity").isNumber()) {
            double quantity = set.path("per_quantity").asDouble();
            String servingUnit = set.path("per_unit").asText("");
            if (Double.isFinite(quantity) && quantity > 0 && quantity <= 100000 && List.of("g", "ml").contains(servingUnit)) {
                unit = servingUnit; factor = 100 / quantity;
            }
        }
        boolean basis = unit != null;
        Double calories = basis ? nutrient(set, "energy-kcal", "kcal", 1500, factor) : null;
        if (calories == null && basis) {
            Double kj = nutrient(set, "energy-kj", "kJ", 6276, factor);
            if (kj == null) kj = nutrient(set, "energy", "kJ", 6276, factor);
            if (kj != null) calories = kj / 4.184;
        }
        Double protein = basis ? nutrient(set, "proteins", "g", 100, factor) : null;
        Double carbs = basis ? nutrient(set, "carbohydrates", "g", 100, factor) : null;
        Double fat = basis ? nutrient(set, "fat", "g", 100, factor) : null;
        boolean complete = !name.isBlank() && calories != null && protein != null && carbs != null && fat != null
            && protein + carbs + fat <= 110;
        return new Product(code, name.isBlank() ? "Product " + code : name, brand, unit,
            calories, protein, carbs, fat, complete, "https://world.openfoodfacts.org/product/" + code);
    }
}
