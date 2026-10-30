package be.cap.backend.accounts.billing;

import java.security.Principal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import be.cap.backend.accounts.AccountService;
import be.cap.backend.accounts.FinanceAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** On-demand, read-only report. No payment creation, state changes or raw Stripe data. */
@RestController
@Profile("accounts")
public class FinanceController {
    public record Payment(Instant date, String athlete, long receivedCents, long refundedCents, boolean disputed) {}
    public record Report(String month, boolean testMode, Instant checkedAt, long receivedCents,
        long refundedCents, long remainingCents, int excludedCurrencyCount, List<Payment> payments) {}
    private final AccountService accounts;
    private final FinanceAccess access;
    private final StripeGateway stripe;
    private final BillingSettings settings;
    private final JdbcTemplate jdbc;
    public FinanceController(AccountService accounts, FinanceAccess access, StripeGateway stripe, BillingSettings settings, JdbcTemplate jdbc) {
        this.accounts=accounts; this.access=access; this.stripe=stripe; this.settings=settings; this.jdbc=jdbc;
    }
    @GetMapping("/api/finance")
    public ResponseEntity<Report> report(Principal principal, @RequestParam String month) {
        if (!access.allows(accounts.account(principal.getName())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Je hebt geen toegang tot Financiën.");
        YearMonth selected;
        try {
            if (!month.matches("[0-9]{4}-[0-9]{2}")) throw new DateTimeParseException("month",month,0);
            selected=YearMonth.parse(month);
            if (selected.getYear()<2020 || selected.getYear()>2100) throw new DateTimeParseException("range",month,0);
        } catch (DateTimeParseException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een geldige maand en jaar.");
        }
        settings.requireEnabled();
        var customers=new HashMap<String,String>();
        jdbc.query("SELECT a.customer_id,u.display_name FROM billing_account a JOIN cap_user u ON u.id=a.user_id WHERE a.customer_id IS NOT NULL",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> customers.put(row.getString(1),row.getString(2)));
        var zone=ZoneId.of("Europe/Brussels");
        Instant start=selected.atDay(1).atStartOfDay(zone).toInstant();
        Instant end=selected.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        var rows=new ArrayList<Payment>();
        var seen=new HashSet<String>();
        long received=0,refunded=0; int excluded=0;
        String cursor="";
        for (int pageNumber=0; pageNumber<20; pageNumber++) {
            var page=stripe.get("/v1/charges?limit=100&created%5Bgte%5D="+start.getEpochSecond()+"&created%5Blt%5D="+end.getEpochSecond()+cursor);
            if (!page.path("data").isArray() || !page.path("has_more").isBoolean()) throw invalid();
            String last="";
            for (var charge:page.path("data")) {
                last=charge.path("id").asText();
                if (!last.matches("ch_[A-Za-z0-9]+") || !seen.add(last) || !charge.has("livemode") || charge.path("livemode").asBoolean(true)) throw invalid();
                var customer=charge.path("customer");
                String name=customers.get(customer.isTextual()?customer.asText():customer.path("id").asText());
                if (name==null || !charge.path("paid").asBoolean() || !charge.path("captured").asBoolean()
                    || !"succeeded".equals(charge.path("status").asText())) continue;
                if (!"eur".equals(charge.path("currency").asText())) { excluded++; continue; }
                if (!charge.path("created").isIntegralNumber() || !charge.path("amount_captured").isIntegralNumber()
                    || !charge.path("amount_refunded").isIntegralNumber()) throw invalid();
                Instant date=Instant.ofEpochSecond(charge.path("created").asLong());
                long amount=charge.path("amount_captured").asLong(),refund=charge.path("amount_refunded").asLong();
                if (date.isBefore(start) || !date.isBefore(end) || amount<0 || amount>99999999 || refund<0 || refund>amount) throw invalid();
                received+=amount; refunded+=refund;
                rows.add(new Payment(date,name,amount,refund,charge.path("disputed").asBoolean()));
            }
            if (!page.path("has_more").asBoolean()) {
                rows.sort(Comparator.comparing(Payment::date).reversed());
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                    new Report(month,true,Instant.now(),received,refunded,received-refunded,excluded,List.copyOf(rows)));
            }
            if (last.isEmpty()) throw invalid();
            cursor="&starting_after="+StripeHttpGateway.encode(last);
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Te veel betalingen voor dit overzicht. Bekijk deze maand in Stripe; er wordt geen onvolledig totaal getoond.");
    }
    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Het financiële testoverzicht kon niet volledig worden gecontroleerd. Probeer opnieuw of controleer Stripe.");
    }
}
