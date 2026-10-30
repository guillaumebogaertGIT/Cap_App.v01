package be.cap.backend;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="cap.accounts.staff-manager=coach@example.test")
class StaffManagementTests extends AccountApiTests {
    @Test void onlyManagerCreatesAndSafelyDisablesAndReactivatesCoaches() throws Exception {
        var manager=client();login(manager,"coach@example.test","Coach-test-password-123");
        String email="managed-"+UUID.randomUUID()+"@example.test",password="Staff-temporary-password-123";
        var input=new HashMap<String,Object>();input.put("name","Fake managed coach");input.put("email",email.toUpperCase(Locale.ROOT));
        input.put("title","Manager · Trainer");input.put("password",password);input.put("role","ADMIN");
        String payload=json.writeValueAsString(input);
        var guest=client();assertEquals(401,request(guest,"POST","/api/coaches",payload,csrf(guest),false).statusCode());
        assertEquals(403,request(manager,"POST","/api/coaches",payload,null,false).statusCode());
        var created=request(manager,"POST","/api/coaches",payload,csrf(manager),false);
        assertEquals(201,created.statusCode(),created.body());assertFalse(created.body().contains(password));
        String id=json.readTree(created.body()).path("id").asText();
        assertEquals("COACH",jdbc.queryForObject("SELECT role FROM cap_user WHERE id=?",String.class,UUID.fromString(id)));
        assertFalse(jdbc.queryForObject("SELECT password_hash FROM cap_user WHERE id=?",String.class,UUID.fromString(id)).equals(password));
        assertEquals(409,request(manager,"POST","/api/coaches",payload,csrf(manager),false).statusCode());
        var target=client();var second=client();login(target,email,password);login(second,email,password);
        assertTrue(request(manager,"GET","/app/index.html",null,null,false).body().contains("\"manageCoaches\":true"));
        assertTrue(request(target,"GET","/app/index.html",null,null,false).body().contains("\"manageCoaches\":false"));
        assertEquals(403,request(target,"POST","/api/coaches",payload,csrf(target),false).statusCode());
        assertEquals(403,request(target,"PUT","/api/coaches/"+id+"/active","{\"active\":false}",csrf(target),false).statusCode());
        var athlete=client();String athleteId=acceptTestAthlete(manager,athlete,"managed-athlete-"+UUID.randomUUID()+"@example.test");
        assertEquals(403,request(athlete,"POST","/api/coaches",payload,csrf(athlete),false).statusCode());
        assertEquals(403,request(athlete,"PUT","/api/coaches/"+id+"/active","{\"active\":false}",csrf(athlete),false).statusCode());
        assertEquals(404,request(manager,"PUT","/api/coaches/"+athleteId+"/active","{\"active\":false}",csrf(manager),false).statusCode());
        String managerId=json.readTree(request(manager,"GET","/api/accounts/me",null,null,false).body()).path("id").asText();
        assertEquals(409,request(manager,"PUT","/api/coaches/"+managerId+"/active","{\"active\":false}",csrf(manager),false).statusCode());
        assertEquals(400,request(manager,"PUT","/api/coaches/"+id+"/active","{}",csrf(manager),false).statusCode());
        assertEquals(204,request(manager,"PUT","/api/coaches/"+id+"/active","{\"active\":false}",csrf(manager),false).statusCode());
        assertFalse(jdbc.queryForObject("SELECT active FROM cap_user WHERE id=?",Boolean.class,UUID.fromString(id)));
        assertEquals(401,request(target,"GET","/api/accounts/me",null,null,false).statusCode());
        var disabled=client();assertEquals(401,request(disabled,"POST","/api/accounts/login","email="+email+"&password="+password,csrf(disabled),true).statusCode());
        assertTrue(request(manager,"GET","/api/coaches",null,null,false).body().contains(id));
        assertFalse(request(manager,"GET","/api/club/people",null,null,false).body().contains(id));
        assertEquals(204,request(manager,"PUT","/api/coaches/"+id+"/active","{\"active\":true}",csrf(manager),false).statusCode());
        assertEquals(401,request(second,"GET","/api/workouts",null,null,false).statusCode()); // Never requested during deactivation; must not revive.
        assertEquals(401,request(target,"GET","/api/accounts/me",null,null,false).statusCode());
        login(target,email,password);
        assertTrue(request(target,"GET","/api/coaches",null,null,false).body().contains(id));
        // A fresh login racing deactivation must also be blocked by the current database flag.
        jdbc.update("UPDATE cap_user SET active=FALSE WHERE id=?",UUID.fromString(id));
        assertEquals(401,request(target,"GET","/api/records",null,null,false).statusCode());
        jdbc.update("UPDATE cap_user SET active=TRUE WHERE id=?",UUID.fromString(id));
        assertEquals(401,request(target,"GET","/api/records",null,null,false).statusCode());
        input.put("email",athleteId+"@example.test");input.put("password","short");
        assertEquals(400,request(manager,"POST","/api/coaches",json.writeValueAsString(input),csrf(manager),false).statusCode());
    }
    @Test void inactiveCoachRetainsLessonsAndIsHiddenFromOrdinaryDirectory() throws Exception {
        var manager=client();login(manager,"coach@example.test","Coach-test-password-123");
        String email="historical-"+UUID.randomUUID()+"@example.test";
        String payload=json.writeValueAsString(Map.of("name","Fake historical coach","email",email,"title","Trainer","password","Staff-temporary-password-123"));
        String id=json.readTree(request(manager,"POST","/api/coaches",payload,csrf(manager),false).body()).path("id").asText();
        var lesson=new HashMap<String,Object>();lesson.put("title","Fake retained lesson");lesson.put("date","2090-01-01");lesson.put("start","10:00");lesson.put("end","11:00");
        lesson.put("coachId",id);lesson.put("location","Test");lesson.put("capacity",8);lesson.put("type","Groep");lesson.put("category","CAP HYROX");lesson.put("repeat",1);
        var session=request(manager,"POST","/api/club/sessions",json.writeValueAsString(lesson),csrf(manager),false);
        assertEquals(201,session.statusCode(),session.body());
        assertEquals(204,request(manager,"PUT","/api/coaches/"+id+"/active","{\"active\":false}",csrf(manager),false).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM club_session WHERE coach_id=?",Integer.class,UUID.fromString(id)));
        assertTrue(request(manager,"GET","/api/club/sessions",null,null,false).body().contains("Fake retained lesson"));
        assertEquals(400,request(manager,"POST","/api/club/sessions",json.writeValueAsString(lesson),csrf(manager),false).statusCode());
        String otherEmail="ordinary-"+UUID.randomUUID()+"@example.test";
        String otherId=json.readTree(request(manager,"POST","/api/coaches",payload.replace(email,otherEmail),csrf(manager),false).body()).path("id").asText();
        var ordinary=client();login(ordinary,otherEmail,"Staff-temporary-password-123");
        assertFalse(request(ordinary,"GET","/api/coaches",null,null,false).body().contains(id));
        assertEquals(404,request(ordinary,"GET","/api/coaches/"+id,null,null,false).statusCode());
        assertEquals(403,request(ordinary,"PUT","/api/coaches/"+otherId+"/active","{\"active\":false}",csrf(ordinary),false).statusCode());
    }
}
