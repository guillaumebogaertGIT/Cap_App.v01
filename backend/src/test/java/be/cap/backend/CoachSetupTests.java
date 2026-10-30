package be.cap.backend;

import be.cap.backend.accounts.CoachProvisioner;
import be.cap.backend.accounts.CoachProvisioner.Coach;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

class CoachSetupTests extends AccountApiTests {
    @Test void passwordChangeChecksCurrentPasswordCsrfAndEndsBothSessions() throws Exception {
        var coach=client();login(coach,"coach@example.test","Coach-test-password-123");
        var owner=client();String email="password-"+UUID.randomUUID()+"@example.test";
        String id=acceptTestAthlete(coach,owner,email);
        var second=client(); login(second,email,"Boundary-test-password-25");
        String oldHash=jdbc.queryForObject("SELECT password_hash FROM cap_user WHERE id=?",String.class,UUID.fromString(id));
        String body="{\"currentPassword\":\"Boundary-test-password-25\",\"newPassword\":\"Updated-test-password-456\"}";
        var guest=client();
        assertEquals(401,request(guest,"POST","/api/accounts/password",body,csrf(guest),false).statusCode());
        assertEquals(403,request(owner,"POST","/api/accounts/password",body,null,false).statusCode());
        assertEquals(400,request(owner,"POST","/api/accounts/password",body.replace("Boundary-test-password-25","wrong"),csrf(owner),false).statusCode());
        assertEquals(400,request(owner,"POST","/api/accounts/password",body.replace("Boundary-test-password-25","x".repeat(73)),csrf(owner),false).statusCode());
        assertEquals(400,request(owner,"POST","/api/accounts/password",body.replace("Updated-test-password-456","short"),csrf(owner),false).statusCode());
        assertEquals(400,request(owner,"POST","/api/accounts/password",body.replace("Updated-test-password-456","Boundary-test-password-25"),csrf(owner),false).statusCode());
        assertEquals(oldHash,jdbc.queryForObject("SELECT password_hash FROM cap_user WHERE id=?",String.class,UUID.fromString(id)));
        assertEquals(204,request(owner,"POST","/api/accounts/password",body,csrf(owner),false).statusCode());
        assertEquals(401,request(owner,"GET","/api/accounts/me",null,null,false).statusCode());
        assertEquals(401,request(second,"GET","/api/accounts/me",null,null,false).statusCode());
        var login=client();assertEquals(401,request(login,"POST","/api/accounts/login","email="+email+"&password=Boundary-test-password-25",csrf(login),true).statusCode());
        login(login,email,"Updated-test-password-456");
        assertEquals(id,json.readTree(request(login,"GET","/api/accounts/me",null,null,false).body()).path("id").asText());
        assertEquals(200,request(coach,"GET","/api/accounts/me",null,null,false).statusCode());
        assertFalse(jdbc.queryForObject("SELECT password_hash FROM cap_user WHERE id=?",String.class,UUID.fromString(id)).contains("Updated"));
    }
    @Test void localProvisionerIsIdempotentAndRollsBackOnRoleConflicts() throws Exception {
        String first="coach-tool-"+UUID.randomUUID()+"@example.test",second="coach-tool-"+UUID.randomUUID()+"@example.test";
        var encoder=new BCryptPasswordEncoder(4);
        var inputs=List.of(new Coach(first,"Fake Coach One","Temporary-test-password-123"),new Coach(second,"Fake Coach Two","Temporary-test-password-456"));
        try(var connection=jdbc.getDataSource().getConnection()) {
            assertEquals(List.of("CREATED "+first,"CREATED "+second),CoachProvisioner.create(connection,inputs,encoder));
            String original=jdbc.queryForObject("SELECT password_hash FROM cap_user WHERE email=?",String.class,first);
            assertTrue(encoder.matches("Temporary-test-password-123",original));
            assertEquals(List.of("KEPT "+first),CoachProvisioner.create(connection,List.of(new Coach(first,"Changed name","Another-test-password-789")),encoder));
            assertEquals(original,jdbc.queryForObject("SELECT password_hash FROM cap_user WHERE email=?",String.class,first));
            assertEquals("Fake Coach One",jdbc.queryForObject("SELECT display_name FROM cap_user WHERE email=?",String.class,first));
            assertEquals("COACH",jdbc.queryForObject("SELECT role FROM cap_user WHERE email=?",String.class,first));
            var coach=client();login(coach,"coach@example.test","Coach-test-password-123");
            String athleteEmail="conflict-"+UUID.randomUUID()+"@example.test";
            acceptTestAthlete(coach,client(),athleteEmail);
            String fresh="rollback-"+UUID.randomUUID()+"@example.test";
            assertThrows(IllegalStateException.class,()->CoachProvisioner.create(connection,List.of(new Coach(fresh,"Fake rollback","Temporary-test-password-123"),new Coach(athleteEmail,"Fake collision","Temporary-test-password-123")),encoder));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE email=?",Integer.class,fresh));
            assertEquals("ATHLETE",jdbc.queryForObject("SELECT role FROM cap_user WHERE email=?",String.class,athleteEmail));
        }
    }
}
