package be.cap.backend;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class FoodProductSearchTests {
    static final String RESULT="""
        {"hits":[
          {"code":"3017620422003","product_name":"Yoghurt","product_name_nl":"Boni yoghurt","brands":["Boni"],"countries_tags":["en:belgium","en:france"]},
          {"code":"96385074","product_name":"Other country","countries_tags":["en:france"]},
          {"code":"3017620422003","product_name":"Duplicate","countries_tags":["en:belgium"]},
          {"code":"bad","product_name":"Bad barcode","countries_tags":["en:belgium"]},
          {"code":"96385074","countries_tags":["en:belgium"]}
        ],"page_count":3,"timed_out":false}
        """;
    @Test void keepsOnlyVerifiedBelgianProductsAndCachesNormalizedQueries() {
        var calls=new AtomicInteger();
        var service=new FoodProductSearchService((query,page)->{calls.incrementAndGet();assertEquals("boni yoghurt",query);return new FoodProductSearchService.ProviderResponse(200,RESULT);},Clock.systemUTC());
        var found=service.search("  BONI   yoghurt  ",1);
        assertEquals(1,found.products().size());assertEquals("Boni yoghurt",found.products().getFirst().name());
        assertEquals("Boni",found.products().getFirst().brand());assertTrue(found.hasMore());
        assertEquals(found,service.search("boni yoghurt",1));assertEquals(1,calls.get());
        assertEquals(2,service.search("boni yoghurt",2).page());assertEquals(2,calls.get());
        assertEquals("countries_tags:\"en:belgium\" AND boni AND yoghurt",FoodProductSearchService.providerQuery("boni yoghurt"));
    }
    @Test void validatesQueriesPagesAndPreventsLuceneScopeInjection() {
        var calls=new AtomicInteger();
        var service=new FoodProductSearchService((query,page)->{calls.incrementAndGet();return new FoodProductSearchService.ProviderResponse(200,RESULT);},Clock.systemUTC());
        for(String query:new String[]{"", "x", "!", "x".repeat(81)})assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.search(query,1)).getStatusCode());
        for(int page:new int[]{0,21})assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.search("boni",page)).getStatusCode());
        assertEquals(0,calls.get());
        String query=FoodProductSearchService.query("BONI) OR countries_tags:\"en:france\"");
        assertEquals("boni or countries tags en france",query);
        assertEquals("countries_tags:\"en:belgium\" AND boni AND or AND countries AND tags AND en AND france",FoodProductSearchService.providerQuery(query));
    }
    @Test void emptyResultsDifferFromProviderFailureAndRateLimit() {
        var empty=new FoodProductSearchService((q,p)->new FoodProductSearchService.ProviderResponse(200,"{\"hits\":[],\"page_count\":0}"),Clock.systemUTC());
        assertTrue(empty.search("unknown",1).products().isEmpty());assertFalse(empty.search("unknown",1).hasMore());
        for(String body:new String[]{"bad json","{}","{\"hits\":[],\"timed_out\":true}","{\"hits\":[],\"errors\":[{}]}"}) {
            var broken=new FoodProductSearchService((q,p)->new FoodProductSearchService.ProviderResponse(200,body),Clock.systemUTC());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,assertThrows(ResponseStatusException.class,()->broken.search("boni",1)).getStatusCode());
        }
        var calls=new AtomicInteger();
        var limited=new FoodProductSearchService((q,p)->{calls.incrementAndGet();return new FoodProductSearchService.ProviderResponse(429,"");},Clock.systemUTC());
        for(int i=0;i<2;i++)assertEquals(HttpStatus.TOO_MANY_REQUESTS,assertThrows(ResponseStatusException.class,()->limited.search("boni",1)).getStatusCode());
        assertEquals(1,calls.get());
    }
    @Test void searchBudgetAndTtlAreSeparateFromProductLookups() {
        var calls=new AtomicInteger();var clock=new FoodProductServiceTests.TestClock();
        var service=new FoodProductSearchService((q,p)->{calls.incrementAndGet();return new FoodProductSearchService.ProviderResponse(200,RESULT);},clock);
        for(int i=1;i<=8;i++)service.search("query "+i,1);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,assertThrows(ResponseStatusException.class,()->service.search("query 9",1)).getStatusCode());
        service.search("query 1",1);assertEquals(8,calls.get());
        clock.now=clock.now.plusSeconds(61);service.search("query 9",1);assertEquals(9,calls.get());
        clock.now=clock.now.plusSeconds(601);service.search("query 1",1);assertEquals(10,calls.get());
    }
}
