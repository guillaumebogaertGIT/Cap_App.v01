package be.cap.backend;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in staging smoke check; ordinary tests never contact Open Food Facts. */
@EnabledIfSystemProperty(named="cap.food.live-test",matches="true")
class FoodProductLiveTests {
    @Test void stagingResponseMapsToPer100Nutrition() {
        var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(4)).build();
        var service=new FoodProductService(code->{
            var request=HttpRequest.newBuilder(URI.create("https://world.openfoodfacts.net/api/v3.6/product/"+code+".json?fields=code,product_name,product_name_nl,brands,nutrition"))
                .header("User-Agent","CAP-Fitness/0.1 (local development staging test)")
                .header("Accept","application/json")
                .header("Authorization","Basic b2ZmOm9mZg==").timeout(Duration.ofSeconds(12)).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode(),()->"Staging response: "+response.headers().firstValue("location").orElse("")+" "+response.body().substring(0,Math.min(160,response.body().length())));
            return new FoodProductService.ProviderResponse(response.statusCode(),response.body());
        },Clock.systemUTC());
        var product=service.lookup("3017620422003");
        assertTrue(product.complete()); assertEquals("g",product.unit());
        assertTrue(product.calories()>0); assertTrue(product.proteinG()>0);
    }
}
