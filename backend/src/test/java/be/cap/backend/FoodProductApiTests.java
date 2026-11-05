package be.cap.backend;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class FoodProductApiTests {
    @TempDir static Path directory;
    @DynamicPropertySource static void files(DynamicPropertyRegistry properties) {
        properties.add("cap.workouts.file",()->directory.resolve("workouts.json").toString());
        properties.add("cap.training.file",()->directory.resolve("training.json").toString());
        properties.add("cap.exercises.file",()->directory.resolve("exercises.json").toString());
    }
    @MockitoBean FoodProductService products;
    @MockitoBean FoodProductSearchService searches;
    @Value("${local.server.port}") int port;
    HttpResponse<String> get() throws Exception {
        return get("/api/food-products/3017620422003");
    }
    HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void searchHasItsOwnRouteAndPageParameter() throws Exception {
        when(searches.search("boni",2)).thenReturn(new FoodProductSearchService.Results(java.util.List.of(new FoodProductSearchService.Hit("3017620422003","Boni yoghurt","Boni")),2,true));
        var response=get("/api/food-products/search?query=boni&page=2");
        assertEquals(200,response.statusCode());assertTrue(response.body().contains("Boni yoghurt"));
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        verify(searches).search("boni",2);verifyNoInteractions(products);
    }
    @Test void returnsProductWithoutCachingBrowserResponse() throws Exception {
        when(products.lookup("3017620422003")).thenReturn(new FoodProductService.Product("3017620422003","Yoghurt","Test","g",60.,10.,4.,0.,true,"https://world.openfoodfacts.org/product/3017620422003"));
        var response=get();
        assertEquals(200,response.statusCode()); assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(response.body().contains("\"complete\":true")); assertTrue(response.body().contains("\"fatG\":0.0"));
    }
    @Test void preservesNotFoundAndProviderUnavailableStatuses() throws Exception {
        for(var status:new HttpStatus[]{HttpStatus.BAD_REQUEST,HttpStatus.NOT_FOUND,HttpStatus.TOO_MANY_REQUESTS,HttpStatus.SERVICE_UNAVAILABLE}) {
            reset(products); when(products.lookup("3017620422003")).thenThrow(new ResponseStatusException(status,"Probeer opnieuw."));
            var response=get(); assertEquals(status.value(),response.statusCode()); assertTrue(response.body().contains("Probeer opnieuw."));
        }
    }
}
