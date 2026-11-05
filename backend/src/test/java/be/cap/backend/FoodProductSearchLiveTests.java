package be.cap.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit single read-only query against the public search API; no writes or diary data. */
@EnabledIfSystemProperty(named="cap.food.search-live-test",matches="true")
class FoodProductSearchLiveTests {
    @Test void realBelgianMarketSearchReturnsNamedProducts() {
        var service=new FoodProductSearchService("CAP-Fitness/0.1 (local development search check)");
        var results=service.search("boni spirelli",1);
        assertFalse(results.products().isEmpty());
        assertTrue(results.products().stream().allMatch(hit->!hit.name().isBlank()));
        assertTrue(results.products().stream().anyMatch(hit->hit.name().toLowerCase().contains("spirelli")));
    }
}
