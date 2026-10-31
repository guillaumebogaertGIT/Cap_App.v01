package be.cap.backend;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:coach-directory;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class CoachDirectoryTests extends AccountApiTests {
    @Test void coachDirectoryRestrictsFieldsRolesAndWrites() throws Exception {
        var guest=client();assertEquals(401,request(guest,"GET","/api/coaches",null,null,false).statusCode());
        var coach=client();login(coach,"coach@example.test","Coach-test-password-123");
        var athlete=client();String athleteId=acceptTestAthlete(coach,athlete,"directory-athlete-"+UUID.randomUUID()+"@example.test");
        UUID id=UUID.randomUUID();String email="directory-coach-"+UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,coach_title) SELECT ?,?,'Fake colleague',password_hash,'COACH',?,'Admin · Trainer' FROM cap_user WHERE email='coach@example.test'",
            id,email,Timestamp.from(Instant.now()));
        assertEquals(403,request(athlete,"GET","/api/coaches",null,null,false).statusCode());
        assertEquals(403,request(athlete,"GET","/api/coaches/"+id,null,null,false).statusCode());
        var response=request(coach,"GET","/api/coaches",null,null,false);
        assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var members=json.readTree(response.body());
        assertFalse(response.body().contains(athleteId));assertFalse(response.body().contains(email));
        assertFalse(response.body().contains("password"));assertFalse(response.body().contains("birthDate"));
        assertTrue(response.body().contains("Admin · Trainer"));
        var detail=request(coach,"GET","/api/coaches/"+id,null,null,false);
        assertEquals(200,detail.statusCode());assertEquals(email,json.readTree(detail.body()).path("email").asText());
        assertEquals("",json.readTree(detail.body()).path("phone").asText());
        assertEquals(404,request(coach,"GET","/api/coaches/"+athleteId,null,null,false).statusCode());
        assertEquals(404,request(coach,"GET","/api/coaches/"+UUID.randomUUID(),null,null,false).statusCode());
        assertEquals(400,request(coach,"GET","/api/coaches/not-a-uuid",null,null,false).statusCode());
        assertEquals(403,request(coach,"PUT","/api/coaches/"+id,"{}",csrf(coach),false).statusCode());
        jdbc.update("UPDATE cap_user SET staff_role='INSTRUCTOR' WHERE id=?",id);var colleague=client();login(colleague,email,"Coach-test-password-123");
        var input=new HashMap<String,Object>();input.put("name","Fake colleague");input.put("birthDate","1995-02-03");
        input.put("heightCm",180);input.put("weightKg",75);input.put("phone","+32 470 12 34 56");input.put("coach_title","Owner");
        assertEquals(200,request(colleague,"PUT","/api/accounts/profile",json.writeValueAsString(input),csrf(colleague),false).statusCode());
        detail=request(coach,"GET","/api/coaches/"+id,null,null,false);
        var contact=json.readTree(detail.body());
        assertEquals("+32 470 12 34 56",contact.path("phone").asText());assertEquals("Admin · Trainer",contact.path("title").asText());
        String photoPath="/api/coaches/"+id+"/photo";
        assertEquals(404,request(coach,"GET",photoPath,null,null,false).statusCode());
        byte[] photo=new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xd9};
        jdbc.update("UPDATE account_profile SET photo=? WHERE user_id=?",photo,id);
        var picture=request(coach,"GET",photoPath,null,null,false);
        assertEquals(200,picture.statusCode());
        assertEquals("image/jpeg",picture.headers().firstValue("content-type").orElse(""));
        assertTrue(picture.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertEquals(401,request(guest,"GET",photoPath,null,null,false).statusCode());
        assertEquals(403,request(athlete,"GET",photoPath,null,null,false).statusCode());
        assertEquals(404,request(coach,"GET","/api/coaches/"+athleteId+"/photo",null,null,false).statusCode());
        jdbc.update("UPDATE cap_user SET active=FALSE WHERE id=?",id);
        assertEquals(200,request(coach,"GET",photoPath,null,null,false).statusCode());
        jdbc.update("UPDATE cap_user SET active=TRUE WHERE id=?",id);
        assertFalse(detail.body().contains("1995-02-03"));assertFalse(detail.body().contains("heightCm"));assertFalse(detail.body().contains("password"));
        input.put("phone","javascript:alert(1)");
        assertEquals(400,request(colleague,"PUT","/api/accounts/profile",json.writeValueAsString(input),csrf(colleague),false).statusCode());
        input.put("phone","");
        assertEquals(200,request(colleague,"PUT","/api/accounts/profile",json.writeValueAsString(input),csrf(colleague),false).statusCode());
        assertEquals("",json.readTree(request(coach,"GET","/api/coaches/"+id,null,null,false).body()).path("phone").asText());
        assertEquals(204,request(coach,"POST","/api/accounts/logout","",csrf(coach),false).statusCode());
        assertEquals(401,request(coach,"GET","/api/coaches/"+id,null,null,false).statusCode());
    }
}
