package be.cap.backend.accounts;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiCoachGatewayTests {
    @Test void requestPinsModelTokenCapNoStorageNoToolsAndNoKeyInPayload(){
        var gateway=new OpenAiCoachGateway("test-key-never-send");
        var payload=gateway.payload(List.of(new AiCoachGateway.Turn("user","Hello")));
        assertEquals("gpt-6-luna",payload.get("model"));assertEquals(false,payload.get("store"));assertEquals(800,payload.get("max_output_tokens"));assertEquals("default",payload.get("service_tier"));
        assertFalse(payload.containsKey("tools"));assertFalse(payload.toString().contains("test-key-never-send"));assertTrue(OpenAiCoachGateway.INSTRUCTIONS.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<4000);
        assertFalse(new OpenAiCoachGateway("").configured());
    }
}
