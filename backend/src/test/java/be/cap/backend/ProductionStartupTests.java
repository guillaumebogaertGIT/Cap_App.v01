package be.cap.backend;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Boot the real gateway bean and production profiles without making an AI request. */
@ActiveProfiles({"accounts","production"})
@TestPropertySource(properties={"CAP_OPEN_AI_KEY=not-a-real-api-key","cap.ai-coach.enabled=true","cap.ai-coach.testers=coach@example.test"})
class ProductionStartupTests extends AccountApiTests {
    @Test void realGatewayAndKeyAliasDoNotBlockServerStartup() throws Exception {
        var owner=client();
        assertEquals(200,request(owner,"GET","/api/health",null,null,false).statusCode());
        login(owner,"coach@example.test","Coach-test-password-123");
        var state=request(owner,"GET","/api/ai-coach",null,null,false);
        assertEquals(200,state.statusCode());assertTrue(json.readTree(state.body()).path("available").asBoolean());
    }
}
