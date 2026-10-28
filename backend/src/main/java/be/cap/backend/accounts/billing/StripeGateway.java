package be.cap.backend.accounts.billing;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Small REST boundary, replaced with a fake only in automated tests. */
public interface StripeGateway {
    JsonNode get(String path);
    JsonNode post(String path, Map<String,String> parameters, String idempotencyKey);
}
