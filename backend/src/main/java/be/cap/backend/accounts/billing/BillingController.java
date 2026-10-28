package be.cap.backend.accounts.billing;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import be.cap.backend.accounts.AccountService;
import tools.jackson.databind.json.JsonMapper;

@RestController
@Profile("accounts")
@RequestMapping("/api/billing")
public class BillingController {
    private static final Set<String> EVENTS=Set.of("checkout.session.completed","checkout.session.expired",
        "checkout.session.async_payment_succeeded","checkout.session.async_payment_failed",
        "invoice.paid","invoice.payment_succeeded","invoice.payment_failed","invoice.payment_action_required","invoice.finalization_failed",
        "customer.subscription.created","customer.subscription.updated","customer.subscription.deleted",
        "customer.subscription.paused","customer.subscription.resumed");
    private final AccountService accounts;
    private final BillingService billing;
    private final BillingSettings settings;
    public BillingController(AccountService accounts,BillingService billing,BillingSettings settings) {
        this.accounts=accounts; this.billing=billing; this.settings=settings;
    }
    private void noInput(Map<String,Object> input) {
        if (input!=null && !input.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Account en TEST-prijs worden door de server bepaald.");
    }
    @GetMapping
    public ResponseEntity<BillingService.Summary> summary(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(billing.summary(accounts.account(principal.getName())));
    }
    @PostMapping("/checkout")
    public BillingService.Link checkout(Principal principal,@RequestBody(required=false) Map<String,Object> input) {
        noInput(input); return billing.checkout(accounts.account(principal.getName()));
    }
    @PostMapping("/portal")
    public BillingService.Link portal(Principal principal,@RequestBody(required=false) Map<String,Object> input) {
        noInput(input); return billing.portal(accounts.account(principal.getName()));
    }
    @PostMapping("/reconcile")
    public BillingService.Summary reconcile(Principal principal,@RequestBody(required=false) Map<String,Object> input) {
        noInput(input); return billing.reconcile(accounts.account(principal.getName()));
    }
    @PostMapping(value="/webhook",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> webhook(@RequestBody byte[] raw,@RequestHeader(value="Stripe-Signature",required=false) String signature) {
        settings.requireEnabled();
        verify(raw,signature);
        tools.jackson.databind.JsonNode event;
        try { event=JsonMapper.builder().build().readTree(raw); }
        catch (tools.jackson.core.JacksonException error) { throw invalid(); }
        if (event==null) throw invalid();
        if (!event.has("livemode") || event.path("livemode").asBoolean(true)) throw invalid();
        String eventId=event.path("id").asText(),type=event.path("type").asText();
        if (!eventId.matches("evt_[A-Za-z0-9]+")) throw invalid();
        if (EVENTS.contains(type)) {
            var customer=event.path("data").path("object").path("customer");
            String id=customer.isTextual()?customer.asText():customer.path("id").asText();
            if (!id.matches("cus_[A-Za-z0-9]+")) throw invalid();
            billing.event(eventId,type,id);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ongeldige Stripe TEST-webhook.");
    }
    private void verify(byte[] raw,String header) {
        if (raw.length>1000000 || header==null || header.length()>4096) throw invalid();
        try {
            Long time=null; var signatures=new ArrayList<String>();
            for (String part:header.split(",")) {
                String[] pair=part.trim().split("=",2);
                if (pair.length!=2) continue;
                if (pair[0].equals("t")) { if (time!=null) throw invalid(); time=Long.parseLong(pair[1]); }
                if (pair[0].equals("v1")) signatures.add(pair[1]);
            }
            long now=Instant.now().getEpochSecond();
            if (time==null || time<now-300 || time>now+300) throw invalid();
            var mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.webhookSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((time+".").getBytes(StandardCharsets.UTF_8));
            byte[] expected=mac.doFinal(raw);
            boolean valid=false;
            for (String value:signatures) {
                if (value.matches("[a-fA-F0-9]{64}")) valid |= MessageDigest.isEqual(expected,HexFormat.of().parseHex(value));
            }
            if (!valid) throw invalid();
        } catch (GeneralSecurityException | NumberFormatException e) { throw invalid(); }
    }
}
