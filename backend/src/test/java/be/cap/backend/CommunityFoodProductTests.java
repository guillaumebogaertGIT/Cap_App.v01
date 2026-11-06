package be.cap.backend;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityFoodProductTests extends AccountApiTests {
    @MockitoBean FoodProductService publicProducts;
    @MockitoBean FoodProductSearchService publicSearch;
    @Test void sharedLabelsAreDiscoverableAcrossAccountsWithoutExposingOwnersOrOverwriting() throws Exception {
        var first=client();var second=client();var guest=client();
        String one="shared-one-"+UUID.randomUUID()+"@example.test",two="shared-two-"+UUID.randomUUID()+"@example.test";
        for(String email:List.of(one,two))jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at) SELECT ?,?,? ,password_hash,'ATHLETE',CURRENT_TIMESTAMP FROM cap_user WHERE email='coach@example.test'",UUID.randomUUID(),email,"Private contributor name");
        login(first,one,"Coach-test-password-123");login(second,two,"Coach-test-password-123");
        String path="/api/food-products/community/036000291452";
        String label="""
            {"name":"Community cheese fixture","brand":"Test","unit":"g","baseAmount":20,"calories":80,"proteinG":5,"carbsG":0,"fatG":6}
            """;
        assertEquals(401,request(guest,"GET","/api/food-products/036000291452",null,null,false).statusCode());
        assertEquals(403,request(first,"PUT",path,label,null,false).statusCode());
        assertEquals(400,request(first,"PUT",path,label.replace("\"proteinG\":5","\"proteinG\":null"),csrf(first),false).statusCode());
        assertEquals(200,request(first,"PUT",path,label,csrf(first),false).statusCode());
        var shared=request(second,"GET","/api/food-products/0036000291452",null,null,false);
        assertEquals(200,shared.statusCode());var product=json.readTree(shared.body());
        assertEquals("community",product.path("source").asText());assertEquals(400,product.path("calories").asDouble());
        assertFalse(shared.body().contains(one));assertFalse(shared.body().contains("Private contributor"));
        verifyNoInteractions(publicProducts);
        when(publicSearch.search("community cheese",1)).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));
        var search=request(second,"GET","/api/food-products/search?query=community%20cheese",null,null,false);
        assertEquals(200,search.statusCode());assertTrue(search.body().contains("Community cheese fixture"));assertTrue(search.body().contains("community"));assertFalse(search.body().contains(one));
        assertEquals(409,request(second,"PUT","/api/food-products/community/0036000291452",label.replace("fixture","changed"),csrf(second),false).statusCode());
        assertEquals(403,request(first,"DELETE",path,null,csrf(first),false).statusCode());
        assertEquals(shared.body(),request(second,"GET","/api/food-products/0036000291452",null,null,false).body());
        assertEquals(200,request(second,"PUT","/api/food-products/personal/036000291452",label.replace("Community cheese fixture","My private label"),csrf(second),false).statusCode());
        assertTrue(request(second,"GET","/api/food-products/0036000291452",null,null,false).body().contains("My private label"));
        assertTrue(request(first,"GET","/api/food-products/0036000291452",null,null,false).body().contains("Community cheese fixture"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM community_food_product WHERE barcode='20359942'",Integer.class));
        assertEquals(200,request(first,"PUT","/api/food-products/personal/20359942",label,csrf(first),false).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM community_food_product WHERE barcode='20359942'",Integer.class));
    }
}
