package be.cap.backend;

import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class FoodProductServiceTests {
    static final String CODE = "3017620422003";
    static String body(String code, String per, String preparation, String nutrients) {
        return """
            {"status":"success","product":{"code":"%s","product_name_nl":"Yoghurt","brands":"Test merk",
             "nutrition":{"aggregated_set":{"per":"%s","preparation":"%s","nutrients":{%s}}}}}
            """.formatted(code, per, preparation, nutrients);
    }
    static final String MACROS = """
        "energy-kcal":{"value":60,"unit":"kcal","source":"packaging"},
        "proteins":{"value":10,"unit":"g","source":"manufacturer"},
        "carbohydrates":{"value":4,"unit":"g"},"fat":{"value":0,"unit":"g"}
        """;
    FoodProductService service(String body) { return new FoodProductService(code -> new FoodProductService.ProviderResponse(200, body), Clock.systemUTC()); }

    @Test void preservesZeroAndPer100BasisAndCachesProducts() {
        var calls = new AtomicInteger();
        var service = new FoodProductService(code -> { calls.incrementAndGet(); return new FoodProductService.ProviderResponse(200, body(code,"100g","as_sold",MACROS)); }, Clock.systemUTC());
        var product = service.lookup("3017 6204-22003");
        assertTrue(product.complete()); assertEquals(60,product.calories()); assertEquals(0,product.fatG());
        assertEquals("g",product.unit()); assertEquals("Yoghurt",product.name());
        assertEquals("https://world.openfoodfacts.org/product/"+CODE,product.sourceUrl());
        assertEquals(product,service.lookup(CODE)); assertEquals(1,calls.get());
    }
    @Test void missingValuesAreNotZeroAndEstimatesBoundsAndUnknownUnitsAreIncomplete() {
        for(String nutrients:new String[]{MACROS.replace("\"value\":10", "\"value\":null"),
            MACROS.replace("\"source\":\"manufacturer\"", "\"source\":\"estimate\""),
            MACROS.replace("\"value\":10", "\"value\":-1"),
            MACROS.replace("\"value\":10", "\"value\":101"),
            MACROS.replace("\"value\":10", "\"modifier\":\"<\",\"value\":10"),
            MACROS.replace("\"value\":10", "\"value\":\"10\"")}) {
            var value=service(body(CODE,"100g","as_sold",nutrients)).lookup(CODE);
            assertFalse(value.complete()); assertNull(value.proteinG());
        }
        assertFalse(service(body(CODE,"serving","as_sold",MACROS)).lookup(CODE).complete());
        assertFalse(service(body(CODE,"100g","prepared",MACROS)).lookup(CODE).complete());
    }
    @Test void convertsOnlyExplicitKilojoulesAndKeepsMillilitres() {
        String nutrients=MACROS.replace("\"energy-kcal\":{\"value\":60,\"unit\":\"kcal\"", "\"energy-kj\":{\"value\":251.04,\"unit\":\"kJ\"");
        var product=service(body(CODE,"100ml","as_sold",nutrients)).lookup(CODE);
        assertTrue(product.complete()); assertEquals("ml",product.unit()); assertEquals(60,product.calories(),.000001);
    }
    @Test void powderLabelOverridesPreparedSummaryAndScalesKnownServingMass() {
        String prepared=body(CODE,"100ml","prepared",MACROS);
        String serving="""
            {"source":"packaging","preparation":"as_sold","per":"serving","per_quantity":30,"per_unit":"g",
             "nutrients":{"energy-kcal":{"value":120,"unit":"kcal"},"proteins":{"value":24000,"unit":"mg"},
             "carbohydrates":{"value":3,"unit":"g"},"fat":{"value":1.5,"unit":"g"}}}
            """;
        var tree=tools.jackson.databind.json.JsonMapper.builder().build().readTree(prepared);
        ((tools.jackson.databind.node.ObjectNode)tree.path("product").path("nutrition")).set("input_sets",
            tools.jackson.databind.json.JsonMapper.builder().build().readTree("["+serving+"]"));
        var value=service(tree.toString()).lookup(CODE);
        assertTrue(value.complete());assertEquals("g",value.unit());
        assertEquals(400,value.calories(),.0001);assertEquals(80,value.proteinG(),.0001);
        assertEquals(10,value.carbsG(),.0001);assertEquals(5,value.fatG(),.0001);
        assertFalse(service(tree.toString().replace("\"per_quantity\":30","\"per_quantity\":null")).lookup(CODE).complete());
    }
    @Test void supplementUnknownNutritionIsNeverInventedAsZero() {
        var missing=service(body(CODE,"100g","as_sold"," ")).lookup(CODE);
        assertFalse(missing.complete());assertNull(missing.calories());assertNull(missing.proteinG());
        var explicit=service(body(CODE,"100g","as_sold",MACROS.replace("60","0").replace("10","0").replace("4,","0,"))).lookup(CODE);
        assertTrue(explicit.complete());assertEquals(0,explicit.calories());
    }
    @Test void validatesChecksumWithoutProviderCallsAndAcceptsCanonicalLeadingZero() {
        var calls=new AtomicInteger();
        var service=new FoodProductService(code->{calls.incrementAndGet();return new FoodProductService.ProviderResponse(200,body("0036000291452","100g","as_sold",MACROS));},Clock.systemUTC());
        for(String code:new String[]{"123", "3017620422004", "https://example.com", "3017620422003/", "ABC12345"})
            assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->service.lookup(code)).getStatusCode());
        assertEquals(0,calls.get()); assertTrue(service.lookup("036000291452").complete());
        assertEquals("96385074",FoodProductService.validBarcode("96385074"));
    }
    @Test void cachesNotFoundButDoesNotCacheProviderFailuresOrBadResponses() {
        var calls=new AtomicInteger();
        var service=new FoodProductService(code->{calls.incrementAndGet();return new FoodProductService.ProviderResponse(404,"{}");},Clock.systemUTC());
        for(int i=0;i<2;i++)assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,()->service.lookup(CODE)).getStatusCode());
        assertEquals(1,calls.get());
        for(var response:new FoodProductService.ProviderResponse[]{new FoodProductService.ProviderResponse(503,"{}"),
            new FoodProductService.ProviderResponse(200,"bad json"),new FoodProductService.ProviderResponse(200,"{}"),
            new FoodProductService.ProviderResponse(200,body("96385074","100g","as_sold",MACROS))}) {
            var broken=new FoodProductService(code->response,Clock.systemUTC());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,assertThrows(ResponseStatusException.class,()->broken.lookup(CODE)).getStatusCode());
        }
    }
    @Test void upstreamRateLimitHasCooldownAndCacheBudgetDoesNotCallNetwork() {
        var calls=new AtomicInteger();
        var service=new FoodProductService(code->{calls.incrementAndGet();return new FoodProductService.ProviderResponse(429,"{}");},Clock.systemUTC());
        for(int i=0;i<2;i++)assertEquals(HttpStatus.TOO_MANY_REQUESTS,assertThrows(ResponseStatusException.class,()->service.lookup(CODE)).getStatusCode());
        assertEquals(1,calls.get());
    }
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-11-02T12:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    static String code(int value) {
        String prefix="%012d".formatted(value);int sum=0;
        for(int i=11,weight=3;i>=0;i--,weight=4-weight)sum+=(prefix.charAt(i)-'0')*weight;
        return prefix+(10-sum%10)%10;
    }
    @Test void enforcesSharedBudgetAndExpiresCacheWithoutBlockingCachedReads() {
        var clock=new TestClock();var calls=new AtomicInteger();
        var service=new FoodProductService(barcode->{calls.incrementAndGet();return new FoodProductService.ProviderResponse(200,body(barcode,"100g","as_sold",MACROS));},clock);
        for(int i=1;i<=12;i++)service.lookup(code(i));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,assertThrows(ResponseStatusException.class,()->service.lookup(code(13))).getStatusCode());
        service.lookup(code(1));assertEquals(12,calls.get());
        clock.now=clock.now.plusSeconds(61);service.lookup(code(13));assertEquals(13,calls.get());
        clock.now=clock.now.plusSeconds(86400);service.lookup(code(1));assertEquals(14,calls.get());
    }
}
