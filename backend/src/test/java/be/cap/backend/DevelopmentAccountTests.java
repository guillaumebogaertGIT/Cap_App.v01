package be.cap.backend;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles({"accounts","development-account"})
@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:dev-account;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","cap.accounts.development-access=true"})
class DevelopmentAccountTests extends AccountApiTests {
    @Test void localDevelopmentLoginNeverGrantsStaffOrClientPrivileges() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,staff_role,created_at) SELECT ?,?,'Development only',password_hash,'COACH','DEVELOPMENT',CURRENT_TIMESTAMP FROM cap_user WHERE email='coach@example.test'",UUID.randomUUID(),email);
        var dev=client();login(dev,email,"Coach-test-password-123");
        var html=request(dev,"GET","/app/index.html",null,null,false);assertEquals(200,html.statusCode());assertTrue(html.body().contains("\"people\":[]"));assertTrue(html.body().contains("\"sessions\":[]"));
        assertEquals(200,request(dev,"GET","/api/accounts/profile",null,null,false).statusCode());
        for(String path:List.of("/api/workouts","/api/club/people","/api/finance?month=2026-10","/api/staff/members","/api/nutrition-targets"))assertEquals(403,request(dev,"GET",path,null,null,false).statusCode(),path);
    }
}
