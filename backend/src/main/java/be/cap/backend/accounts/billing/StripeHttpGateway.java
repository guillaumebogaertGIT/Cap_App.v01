package be.cap.backend.accounts.billing;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("accounts")
public class StripeHttpGateway implements StripeGateway {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(StripeHttpGateway.class);
    private final BillingSettings settings;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json=JsonMapper.builder().build();
    public StripeHttpGateway(BillingSettings settings) { this.settings=settings; }
    public static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    @Override public JsonNode get(String path) { return call(path,null,null); }
    @Override public JsonNode post(String path, Map<String,String> parameters, String key) { return call(path,parameters,key); }
    private JsonNode call(String path,Map<String,String> parameters,String key) {
        settings.requireEnabled();
        if (!path.startsWith("/v1/")) throw new IllegalArgumentException("Invalid Stripe API path");
        var request=HttpRequest.newBuilder(URI.create("https://api.stripe.com"+path))
            .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+settings.secret)
            .header("Stripe-Version","2025-06-30.basil");
        if (parameters!=null) {
            request.header("Content-Type","application/x-www-form-urlencoded");
            if (key!=null) request.header("Idempotency-Key",key);
            request.POST(HttpRequest.BodyPublishers.ofString(parameters.entrySet().stream()
                .map(e->encode(e.getKey())+"="+encode(e.getValue())).collect(Collectors.joining("&"))));
        }
        try {
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()<200 || response.statusCode()>=300) {
                LOG.warn("Stripe TEST request rejected: status={}",response.statusCode());
                throw unavailable();
            }
            return json.readTree(response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (java.io.IOException e) { LOG.warn("Stripe TEST connection failed: category={}",e.getClass().getSimpleName()); throw unavailable(); }
    }
    private ResponseStatusException unavailable() {
        // Stripe error bodies can contain customer information. Never return or log them.
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Stripe TEST is tijdelijk niet bereikbaar of niet correct ingesteld. Probeer opnieuw of controleer de testconfiguratie.");
    }
}
