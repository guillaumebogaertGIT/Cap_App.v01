package be.cap.backend;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="spring.datasource.url=jdbc:h2:mem:measurements;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class MeasurementTests extends AccountApiTests {
    @Test void profileChangesAndMeasurementFormsSharePersistentHistoryWithoutDuplicates() throws Exception {
        var coach=client();login(coach,"coach@example.test","Coach-test-password-123");
        var athlete=client();String id=acceptTestAthlete(coach,athlete,"metrics-"+UUID.randomUUID()+"@example.test");
        String profile="{\"name\":\"Fake metric athlete\",\"heightCm\":180,\"weightKg\":75}";
        var saved=request(athlete,"PUT","/api/accounts/profile",profile,csrf(athlete),false);
        assertEquals(200,saved.statusCode(),saved.body());
        var result=json.readTree(saved.body());assertEquals(1,result.path("measurements").size());
        assertEquals(180,result.path("measurements").path(0).path("values").path("heightCm").asInt());
        assertEquals(75,result.path("measurements").path(0).path("values").path("weightKg").asInt());
        assertFalse(result.path("measurements").path(0).path("recordedAt").isNull());
        request(athlete,"PUT","/api/accounts/profile",profile.replace("Fake metric athlete","Renamed fake athlete"),csrf(athlete),false);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM account_measurement WHERE user_id=?",Integer.class,UUID.fromString(id)));
        var logged=request(athlete,"POST","/api/measurements","{\"weightKg\":76.5,\"bodyFatPercent\":20}",csrf(athlete),false);
        assertEquals(200,logged.statusCode(),logged.body());
        result=json.readTree(logged.body());assertEquals(76.5,result.path("values").path("weightKg").asDouble());
        assertEquals(2,result.path("measurements").size());assertEquals(20,result.path("measurements").path(0).path("values").path("bodyFatPercent").asInt());
        var shared=request(coach,"GET","/api/measurements/athletes/"+id,null,null,false);
        assertEquals(200,shared.statusCode());assertEquals(76.5,json.readTree(shared.body()).path("profile").path("weightKg").asDouble());
        assertFalse(shared.body().contains("phone"));assertFalse(shared.body().contains("birthDate"));assertFalse(shared.body().contains("password"));
        assertEquals(200,request(coach,"POST","/api/measurements/athletes/"+id,"{\"heightCm\":181,\"weightKg\":77}",csrf(coach),false).statusCode());
        result=json.readTree(request(athlete,"GET","/api/accounts/profile",null,null,false).body());
        assertEquals(181,result.path("values").path("heightCm").asInt());assertEquals(77,result.path("values").path("weightKg").asInt());
        assertEquals(3,result.path("measurements").size());
        for(String invalid:List.of("{}","{\"heightCm\":20}","{\"weightKg\":-1}","{\"bodyFatPercent\":101}","{\"userId\":1}","{\"weightKg\":null}"))
            assertEquals(400,request(athlete,"POST","/api/measurements",invalid,csrf(athlete),false).statusCode());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM account_measurement WHERE user_id=?",Integer.class,UUID.fromString(id)));
        var other=client();String otherId=acceptTestAthlete(coach,other,"metric-peer-"+UUID.randomUUID()+"@example.test");
        assertEquals(403,request(other,"GET","/api/measurements/athletes/"+id,null,null,false).statusCode());
        assertEquals(403,request(other,"POST","/api/measurements/athletes/"+id,"{\"weightKg\":70}",csrf(other),false).statusCode());
        assertEquals(0,json.readTree(request(other,"GET","/api/measurements?athleteId="+id,null,null,false).body()).path("measurements").size());
        assertEquals(401,request(client(),"GET","/api/measurements",null,null,false).statusCode());
        assertEquals(403,request(athlete,"POST","/api/measurements","{\"weightKg\":70}",null,false).statusCode());
        String coachId=json.readTree(request(coach,"GET","/api/accounts/me",null,null,false).body()).path("id").asText();
        assertEquals(404,request(coach,"GET","/api/measurements/athletes/"+coachId,null,null,false).statusCode());
        assertEquals(200,request(coach,"PUT","/api/accounts/profile","{\"name\":\"Fake coach\",\"heightCm\":175,\"weightKg\":80}",csrf(coach),false).statusCode());
        assertTrue(json.readTree(request(coach,"GET","/api/measurements",null,null,false).body()).path("measurements").size()>0);
    }
    @Test void existingProfileHasUndatedBaselineAndDataSurvivesNewLogin() throws Exception {
        var coach=client();login(coach,"coach@example.test","Coach-test-password-123");
        var owner=client();String email="metric-baseline-"+UUID.randomUUID()+"@example.test";
        String id=acceptTestAthlete(coach,owner,email);
        jdbc.update("INSERT INTO account_profile(user_id,values_json) VALUES(?,?)",UUID.fromString(id),"{\"name\":\"Fake existing profile\",\"heightCm\":180,\"weightKg\":75}");
        var values=json.readTree(request(owner,"GET","/api/accounts/profile",null,null,false).body()).path("measurements");
        assertEquals(1,values.size());assertTrue(values.path(0).path("recordedAt").isNull());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM account_measurement WHERE user_id=?",Integer.class,UUID.fromString(id)));
        assertEquals(200,request(owner,"POST","/api/measurements","{\"weightKg\":76}",csrf(owner),false).statusCode());
        request(owner,"POST","/api/accounts/logout","",csrf(owner),false);
        var fresh=client();login(fresh,email,"Boundary-test-password-25");
        assertEquals(76,json.readTree(request(fresh,"GET","/api/accounts/profile",null,null,false).body()).path("values").path("weightKg").asInt());
        assertEquals(1,json.readTree(request(fresh,"GET","/api/measurements",null,null,false).body()).path("measurements").size());
    }
}
