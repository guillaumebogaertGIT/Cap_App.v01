package be.cap.backend;

import be.cap.backend.accounts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;

class NutritionPreferencesTests extends AccountApiTests {
    @Autowired AccountService accounts;
    @Test void selfOwnershipScopedCoachAccessAndRevocation() throws Exception {
        var coach=accounts.account("coach@example.test");var one=accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",coach.email()).token(),"Food Client One","Athlete-test-password-123");var two=accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",coach.email()).token(),"Food Client Two","Athlete-test-password-123");
        var user=client();login(user,one.email(),"Athlete-test-password-123");var other=client();login(other,two.email(),"Athlete-test-password-123");var staff=client();login(staff,coach.email(),"Coach-test-password-123");
        var initial=json.readTree(request(user,"GET","/api/nutrition-preferences/self",null,null,false).body());assertFalse(initial.path("aiShare").asBoolean());assertEquals(0,initial.path("version").asInt());
        String input="{\"allergies\":\"pinda\",\"avoidedFoods\":\"vis\",\"aiShare\":true,\"version\":0}";
        assertEquals(403,request(user,"PUT","/api/nutrition-preferences/self",input,null,false).statusCode());assertEquals(200,request(user,"PUT","/api/nutrition-preferences/self",input,csrf(user),false).statusCode());assertEquals(409,request(user,"PUT","/api/nutrition-preferences/self",input,csrf(user),false).statusCode());
        assertEquals(403,request(other,"GET","/api/nutrition-preferences?athleteId="+one.id(),null,null,false).statusCode());assertFalse(request(other,"GET","/api/nutrition-preferences/self",null,null,false).body().contains("pinda"));
        assertTrue(request(staff,"GET","/api/nutrition-preferences?athleteId="+one.id(),null,null,false).body().contains("pinda"));
        String revoked=input.replace("\"aiShare\":true","\"aiShare\":false").replace("\"version\":0","\"version\":1");assertEquals(200,request(user,"PUT","/api/nutrition-preferences/self",revoked,csrf(user),false).statusCode());assertNull(jdbc.queryForObject("SELECT ai_consent_at FROM nutrition_preference WHERE athlete_id=?",java.sql.Timestamp.class,one.id()));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM nutrition_ai_consent_event WHERE athlete_id=?",Integer.class,one.id()));
        jdbc.update("UPDATE cap_user SET staff_role='INSTRUCTOR' WHERE id=?",coach.id());try{assertEquals(403,request(staff,"GET","/api/nutrition-preferences?athleteId="+one.id(),null,null,false).statusCode());}finally{jdbc.update("UPDATE cap_user SET staff_role='OWNER' WHERE id=?",coach.id());}
    }
}
