package be.cap.backend.accounts.billing;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Server-owned prices and terms. The browser selects only a known offer code. */
@Component
@Profile("accounts")
public class BillingCatalog {
    public record Offer(String code, String name, int cents, String currency, int months,
                        int credits, boolean recurring, boolean cancellable, String terms, boolean available) {}
    private final Map<String,String> prices = new HashMap<>();
    private final BillingSettings settings;
    private final List<Offer> offers;
    public BillingCatalog(BillingSettings settings, @Value("${cap.billing.prices:{}}") String configured) {
        this.settings = settings;
        var values = JsonMapper.builder().build().readTree(configured);
        if (!values.isObject()) throw new IllegalStateException("Stripe test prices must be a JSON object.");
        values.properties().forEach(entry -> {
            String value = entry.getValue().asText();
            if (!value.matches("price_[A-Za-z0-9]+")) throw new IllegalStateException("Invalid Stripe test price ID.");
            prices.put(entry.getKey(), value);
        });
        if (new HashSet<>(prices.values()).size() != prices.size()) throw new IllegalStateException("Each offer needs its own price.");
        offers = List.of(
            offer("full_1m", "Full access · 1 maand", 9000, 1, 0, false, false, "Eenmalig betalen. Stopt na 1 maand."),
            offer("full_3m", "Full access · 3 maanden", 22500, 3, 0, false, false, "Eenmalig betalen. Stopt na 3 maanden."),
            offer("full_12m", "Full access · 1 jaar", 75000, 12, 0, false, false, "Eenmalig betalen. Stopt na 12 maanden."),
            offer("monthly_flexible", "Full access · maandelijks opzegbaar", 7500, 12, 0, true, true, "Maximaal 12 maanden. Altijd opzegbaar; toegang tot het einde van de betaalde maand."),
            offer("monthly_fixed", "Full access · 12 maanden vast", 6500, 12, 0, true, false, "12 maandbetalingen van €65 (€780 totaal). Niet tussentijds opzegbaar. Stopt automatisch."),
            offer("credits_1", "1 lescredit", 1800, 0, 1, false, false, "1 credit = 1 groepsles. Ongebruikte credits vervallen niet."),
            offer("credits_5", "5 lescredits", 8000, 0, 5, false, false, "1 credit = 1 groepsles. Ongebruikte credits vervallen niet."),
            offer("credits_10", "10 lescredits", 14000, 0, 10, false, false, "1 credit = 1 groepsles. Ongebruikte credits vervallen niet."),
            offer("credits_20", "20 lescredits", 24000, 0, 20, false, false, "1 credit = 1 groepsles. Ongebruikte credits vervallen niet.")
        );
        if (!offers.stream().map(Offer::code).toList().containsAll(prices.keySet())) throw new IllegalStateException("Unknown Stripe test offer code.");
    }
    private Offer offer(String code,String name,int cents,int months,int credits,boolean recurring,boolean cancellable,String terms) {
        return new Offer(code,name,cents,"EUR",months,credits,recurring,cancellable,terms,settings.enabled && prices.containsKey(code));
    }
    public List<Offer> offers() { return offers; }
    public Offer require(String code) {
        if ("legacy_test".equals(code)) return new Offer(code,MembershipPolicy.PLAN,1000,"EUR",0,0,true,true,"Tijdelijk oud testabonnement.",settings.enabled && !settings.price.isBlank());
        return offers.stream().filter(o -> o.code().equals(code)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een aanbod uit de CAP Shop."));
    }
    public String price(String code) { return "legacy_test".equals(code) ? settings.price : prices.get(code); }
    public Optional<Offer> byPrice(String price) {
        if (!settings.price.isBlank() && settings.price.equals(price)) return Optional.of(require("legacy_test"));
        return offers.stream().filter(o -> price.equals(prices.get(o.code()))).findFirst();
    }
}
