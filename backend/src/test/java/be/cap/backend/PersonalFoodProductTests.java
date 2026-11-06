package be.cap.backend;

import java.util.UUID;
import be.cap.backend.accounts.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalFoodProductTests extends AccountApiTests {
    @MockitoBean FoodProductService publicProducts;
    @Autowired AccountService accounts;

    @Test void personalProductsRemainPrivatePersistAndRespectCsrfAndLabels() throws Exception {
        String path="/api/food-products/personal/036000291452";
        var guest=client();var first=client();var second=client();var coach=client();
        assertEquals(401,request(guest,"GET","/api/food-products/personal",null,null,false).statusCode());
        for(String email:new String[]{"first-food@example.test","second-food@example.test"}) {
            jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at) SELECT ?,?,? ,password_hash,'ATHLETE',CURRENT_TIMESTAMP FROM cap_user WHERE email='coach@example.test'",UUID.randomUUID(),email,"Food test");
        }
        login(first,"first-food@example.test","Coach-test-password-123");
        login(second,"second-food@example.test","Coach-test-password-123");
        login(coach,"coach@example.test","Coach-test-password-123");
        String label="""
            {"name":"Protein powder","brand":"Test","unit":"g","baseAmount":30,"calories":120,"proteinG":24,"carbsG":3,"fatG":1.5}
            """;
        assertEquals(403,request(first,"PUT",path,label,null,false).statusCode());
        assertEquals(200,request(coach,"PUT",path,label,csrf(coach),false).statusCode());
        assertTrue(request(coach,"GET","/api/food-products/personal",null,null,false).body().contains("Protein powder"));
        var saved=request(first,"PUT",path,label,csrf(first),false);assertEquals(200,saved.statusCode());
        assertEquals(80,json.readTree(saved.body()).path("proteinG").asDouble(),.0001);
        assertEquals("personal",json.readTree(saved.body()).path("source").asText());
        assertEquals("[]",request(second,"GET","/api/food-products/personal",null,null,false).body());
        when(publicProducts.lookup("0036000291452")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEquals(404,request(second,"GET","/api/food-products/0036000291452",null,null,false).statusCode());
        var own=request(first,"GET","/api/food-products/0036000291452",null,null,false);
        assertEquals(200,own.statusCode());assertEquals("0036000291452",json.readTree(own.body()).path("barcode").asText());
        var reloaded=new PersonalFoodProductService(jdbc,accounts).find(()->"first-food@example.test","036000291452");
        assertEquals(400,reloaded.calories(),.0001);
        String day=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).toString();
        var portion=new java.util.HashMap<String,Object>();
        portion.putAll(java.util.Map.of("id",UUID.randomUUID().toString(),"date",day,"meal","breakfast","name","Protein powder","amount",30,"unit","g","calories",120,"proteinG",24,"carbsG",3,"fatG",1.5));
        portion.put("foodId","personal:0036000291452");
        assertEquals(201,request(first,"POST","/api/food-diary",json.writeValueAsString(portion),csrf(first),false).statusCode());
        String history=request(first,"GET","/api/food-diary?date="+day,null,null,false).body();
        assertEquals(200,request(second,"PUT",path,label.replace("Protein powder","Other label"),csrf(second),false).statusCode());
        assertEquals("Protein powder",json.readTree(request(first,"GET","/api/food-products/036000291452",null,null,false).body()).path("name").asText());
        assertEquals(400,request(first,"PUT",path,label.replace("\"baseAmount\":30","\"baseAmount\":0"),csrf(first),false).statusCode());
        assertEquals(400,request(first,"PUT",path,label.replace("\"proteinG\":24","\"proteinG\":31"),csrf(first),false).statusCode());
        var partial=request(first,"PUT",path,label.replace("\"proteinG\":24","\"proteinG\":null"),csrf(first),false);
        assertEquals(200,partial.statusCode());assertFalse(json.readTree(partial.body()).path("complete").asBoolean());
        assertTrue(json.readTree(partial.body()).path("proteinG").isNull());
        assertEquals(204,request(first,"DELETE",path,null,csrf(first),false).statusCode());
        assertEquals("[]",request(first,"GET","/api/food-products/personal",null,null,false).body());
        assertEquals(history,request(first,"GET","/api/food-diary?date="+day,null,null,false).body());
        assertEquals("Other label",json.readTree(request(second,"GET","/api/food-products/036000291452",null,null,false).body()).path("name").asText());
    }
}
