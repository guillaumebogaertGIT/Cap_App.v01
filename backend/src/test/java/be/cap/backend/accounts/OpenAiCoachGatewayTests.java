package be.cap.backend.accounts;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiCoachGatewayTests {
    @Test void nutritionRequestUsesDocumentedSingleTextInput(){var payload=new OpenAiCoachGateway("secret").nutritionPayload("Maak een weekplan",Map.of("scope","week"));assertEquals("Maak een weekplan",payload.get("input"));assertEquals(false,payload.get("store"));assertTrue(payload.get("text").toString().contains("json_object"));assertFalse(payload.toString().contains("secret"));}
    @Test void actionPayloadRequiresReviewAndRetainsBudgetBounds(){var p=new OpenAiCoachGateway("secret-key").actionPayload(List.of(new AiCoachGateway.Turn("user","Maak workout")),Map.of("matchingClients",List.of()),"");assertEquals(6000,p.get("max_output_tokens"));assertEquals(false,p.get("store"));assertTrue(p.get("text").toString().contains("json_object"));assertTrue(p.get("instructions").toString().contains("human must confirm"));assertFalse(p.toString().contains("secret-key"));assertFalse(p.containsKey("tools"));}

    @Test void personalSnapshotIsSeparateFromUserTextAndBoundedToExplicitContext(){
        var gateway=new OpenAiCoachGateway("private-key");var plain=gateway.payload(List.of(new AiCoachGateway.Turn("user","Mijn voeding?")));
        assertFalse(plain.toString().contains("<own_data>"));
        var enriched=gateway.withContext(plain,"{\"proteinG\":120}");assertTrue(enriched.get("instructions").toString().contains("<own_data>"));assertTrue(enriched.get("instructions").toString().contains("untrusted data"));assertEquals(plain.get("input"),enriched.get("input"));assertFalse(enriched.toString().contains("private-key"));
    }
    @Test void providerErrorsUseSafeCodesWithoutLeakingResponseText(){
        var gateway=new OpenAiCoachGateway("private-key");
        assertEquals("provider_quota",gateway.providerFailure(429,"{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"private chat\"}}").code());
        assertEquals("provider_rate",gateway.providerFailure(429,"not JSON").code());
        assertEquals("provider_request",gateway.providerFailure(400,"private provider details").code());
        assertEquals("provider_key",gateway.providerFailure(401,"secret").code());
        assertFalse(gateway.providerFailure(400,"private provider details").userMessage().contains("private"));
        assertEquals("provider_request/http400/invalid_json_schema/text.format.schema",gateway.providerFailure(400,"{\"error\":{\"code\":\"invalid_json_schema\",\"param\":\"text.format.schema\",\"message\":\"private input\"}}").diagnostic());
        assertEquals("provider_request/http400",gateway.providerFailure(400,"{\"error\":{\"code\":\"private input\",\"param\":\"secret-key\"}}").diagnostic());
    }
    @Test void draftSchemaUsesOnlyCatalogAndKeepsPrivacyAndTokenCaps(){
        var payload=new OpenAiCoachGateway("secret").draftPayload(List.of(new AiCoachGateway.Turn("user","Full body")),List.of("Squat","Push-up"));
        assertEquals(1800,payload.get("max_output_tokens"));assertEquals(false,payload.get("store"));assertFalse(payload.containsKey("tools"));
        String schema=payload.get("text").toString();assertTrue(schema.contains("json_schema"));assertTrue(payload.get("instructions").toString().contains("Squat"));assertTrue(schema.contains("additionalProperties=false"));
        assertFalse(schema.contains("minimum"));assertFalse(schema.contains("maximum"));assertFalse(schema.contains("maxItems"));
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
