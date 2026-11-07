package be.cap.backend.accounts;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiCoachGatewayTests {
    @Test void providerErrorsUseSafeCodesWithoutLeakingResponseText(){
        var gateway=new OpenAiCoachGateway("private-key");
        assertEquals("provider_quota",gateway.providerFailure(429,"{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"private chat\"}}").code());
        assertEquals("provider_rate",gateway.providerFailure(429,"not JSON").code());
        assertEquals("provider_request",gateway.providerFailure(400,"private provider details").code());
        assertEquals("provider_key",gateway.providerFailure(401,"secret").code());
        assertFalse(gateway.providerFailure(400,"private provider details").userMessage().contains("private"));
    }
    @Test void draftSchemaUsesOnlyCatalogAndKeepsPrivacyAndTokenCaps(){
        var payload=new OpenAiCoachGateway("secret").draftPayload(List.of(new AiCoachGateway.Turn("user","Full body")),List.of("Squat","Push-up"));
        assertEquals(1800,payload.get("max_output_tokens"));assertEquals(false,payload.get("store"));assertFalse(payload.containsKey("tools"));
        String schema=payload.get("text").toString();assertTrue(schema.contains("json_schema"));assertTrue(schema.contains("Squat"));assertTrue(schema.contains("additionalProperties=false"));
        assertTrue(payload.get("instructions").toString().contains("Unknown/unconfirmed"));assertFalse(payload.toString().contains("secret"));
    }
    @Test void requestPinsModelTokenCapNoStorageNoToolsAndNoKeyInPayload(){
        var gateway=new OpenAiCoachGateway("test-key-never-send");
        var payload=gateway.payload(List.of(new AiCoachGateway.Turn("user","Hello")));
        assertEquals("gpt-6-luna",payload.get("model"));assertEquals(false,payload.get("store"));assertEquals(800,payload.get("max_output_tokens"));assertEquals("default",payload.get("service_tier"));
        assertFalse(payload.containsKey("tools"));assertFalse(payload.toString().contains("test-key-never-send"));assertTrue(OpenAiCoachGateway.INSTRUCTIONS.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<4000);
        assertFalse(new OpenAiCoachGateway("").configured());
    }
}
