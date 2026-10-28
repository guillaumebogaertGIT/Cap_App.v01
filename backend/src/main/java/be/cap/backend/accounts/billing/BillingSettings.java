package be.cap.backend.accounts.billing;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Deliberately test-only: there is no live-mode switch. Never expose this bean. */
@Component
@Profile("accounts")
public class BillingSettings {
    final boolean enabled;
    final String secret, webhookSecret, price, portal, baseUrl;

    public BillingSettings(@Value("${cap.billing.enabled:false}") boolean enabled,
        @Value("${cap.billing.secret:}") String secret, @Value("${cap.billing.webhook-secret:}") String webhookSecret,
        @Value("${cap.billing.price:}") String price, @Value("${cap.billing.portal:}") String portal,
        @Value("${cap.billing.base-url:http://127.0.0.1:8081}") String baseUrl) {
        this.enabled=enabled; this.secret=secret; this.webhookSecret=webhookSecret;
        this.price=price; this.portal=portal; this.baseUrl=baseUrl;
        if (enabled) {
            if (!secret.startsWith("sk_test_") || !webhookSecret.startsWith("whsec_") || !price.matches("price_[A-Za-z0-9]+") || !portal.matches("bpc_[A-Za-z0-9]+"))
                throw new IllegalStateException("Configure Stripe TEST secret, webhook secret, price and portal IDs. Live keys are not supported.");
            URI uri=URI.create(baseUrl);
            if (!"http".equals(uri.getScheme()) || !("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty()))
                throw new IllegalStateException("Stripe TEST billing is restricted to a localhost base URL without a path.");
        }
    }
    void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe TEST MODE is nog niet ingesteld. Je account blijft beschikbaar.");
    }
}
