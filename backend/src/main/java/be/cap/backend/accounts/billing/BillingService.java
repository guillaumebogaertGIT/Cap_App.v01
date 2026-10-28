package be.cap.backend.accounts.billing;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import be.cap.backend.accounts.AccountService.Account;
import tools.jackson.databind.JsonNode;

@Service
@Profile("accounts")
public class BillingService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BillingService.class);
    public record Summary(boolean configured, boolean testMode, String plan, int monthlyCents, String currency,
        String status, boolean membershipAccess, Instant periodStart, Instant periodEnd, boolean cancelAtPeriodEnd,
        Instant cancelAt, Instant canceledAt, Instant syncedAt, boolean hasCustomer) {}
    public record Link(String url) {}
    private record State(UUID owner,String customer,UUID customerKey,Instant customerStarted,
        UUID checkoutKey,String checkoutPrice,Instant checkoutStarted,String checkoutSession) {}
    private final JdbcTemplate jdbc;
    private final StripeGateway stripe;
    private final BillingSettings settings;
    private final TransactionTemplate tx;
    public BillingService(JdbcTemplate jdbc,StripeGateway stripe,BillingSettings settings,PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.stripe=stripe; this.settings=settings; this.tx=new TransactionTemplate(manager);
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private void testObject(JsonNode object) {
        if (object==null || !object.has("livemode") || object.path("livemode").asBoolean(true))
            throw conflict("Alleen Stripe TEST-objecten zijn toegestaan.");
    }
    private String id(JsonNode value) { return value.isTextual()?value.asText():value.path("id").asText(); }
    private Instant instant(JsonNode value) { return value.isNumber()?Instant.ofEpochSecond(value.asLong()):null; }
    private Instant timestamp(java.sql.ResultSet row,String key) throws java.sql.SQLException {
        var value=row.getTimestamp(key); return value==null?null:value.toInstant();
    }
    private Timestamp sql(Instant value) { return value==null?null:Timestamp.from(value); }
    private State lock(UUID owner) {
        return jdbc.queryForObject("SELECT * FROM billing_account WHERE user_id=? FOR UPDATE",(r,i)->new State(owner,
            r.getString("customer_id"),r.getObject("customer_key",UUID.class),timestamp(r,"customer_started_at"),
            r.getObject("checkout_key",UUID.class),r.getString("checkout_price"),timestamp(r,"checkout_started_at"),r.getString("checkout_session_id")),owner);
    }
    private void ensure(Account user) {
        tx.executeWithoutResult(ignored->{
            jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,user.id());
            if (jdbc.queryForObject("SELECT COUNT(*) FROM billing_account WHERE user_id=?",Integer.class,user.id())==0)
                jdbc.update("INSERT INTO billing_account(user_id,customer_key,customer_started_at) VALUES(?,?,?)",user.id(),UUID.randomUUID(),sql(Instant.now()));
        });
    }
    private void recent(Instant started) {
        if (started==null || started.isBefore(Instant.now().minus(Duration.ofHours(23))))
            throw conflict("Een eerdere Stripe-aanvraag moet eerst worden gecontroleerd. Start geen tweede abonnement; volg de herstelstappen in de testhandleiding.");
    }
    private String hostedUrl(JsonNode object,String host) {
        String value=object.path("url").asText();
        try { var uri=java.net.URI.create(value); if ("https".equals(uri.getScheme()) && host.equals(uri.getHost()) && uri.getUserInfo()==null) return value; }
        catch (IllegalArgumentException ignored) { }
        throw conflict("Stripe gaf geen geldige TEST-betaallink terug.");
    }
    private void approvedPrice(String price) {
        var p=stripe.get("/v1/prices/"+StripeHttpGateway.encode(price)); testObject(p);
        if (!p.path("active").asBoolean() || !MembershipPolicy.CURRENCY.equalsIgnoreCase(p.path("currency").asText()) || p.path("unit_amount").asLong(-1)!=MembershipPolicy.MONTHLY_CENTS
            || !"month".equals(p.path("recurring").path("interval").asText()) || p.path("recurring").path("interval_count").asInt()!=1
            || !"per_unit".equals(p.path("billing_scheme").asText()) || p.hasNonNull("transform_quantity"))
            throw conflict("Configureer de tijdelijke TEST-prijs van 10 EUR per maand, zonder staffels of hoeveelheidsaanpassing.");
    }
    private List<JsonNode> subscriptions(String customer) {
        var page=stripe.get("/v1/subscriptions?customer="+StripeHttpGateway.encode(customer)+"&status=all&limit=100&expand%5B%5D=data.latest_invoice");
        if (page.path("has_more").asBoolean() || !page.path("data").isArray()) throw conflict("Controleer dit testabonnement met de beheerder voordat je doorgaat.");
        var result=new ArrayList<JsonNode>();
        for (var sub:page.path("data")) {
            testObject(sub);
            if (!customer.equals(id(sub.path("customer")))) throw conflict("Stripe-klant komt niet overeen.");
            result.add(sub);
        }
        return result;
    }
    public Link checkout(Account user) {
        settings.requireEnabled(); approvedPrice(settings.price); ensure(user);
        // Customer key is already committed: a lost API response can be retried safely.
        tx.executeWithoutResult(ignored->{
            var state=lock(user.id());
            if (state.customer()==null) {
                recent(state.customerStarted());
                var customer=stripe.post("/v1/customers",Map.of("metadata[cap_account_id]",user.id().toString()),"cap-test-customer-"+state.customerKey());
                testObject(customer);
                String customerId=customer.path("id").asText();
                if (!customerId.matches("cus_[A-Za-z0-9]+")) throw conflict("Ongeldige Stripe TEST-klant.");
                jdbc.update("UPDATE billing_account SET customer_id=? WHERE user_id=?",customerId,user.id());
            }
        });
        // Reserve the attempt in a separate committed transaction before contacting Checkout.
        tx.executeWithoutResult(ignored->{
            var state=lock(user.id());
            if (subscriptions(state.customer()).stream().anyMatch(s->MembershipPolicy.blocksAnotherSubscription(s.path("status").asText())))
                throw conflict("Je hebt al een lopend of onvoltooid testabonnement. Vernieuw de status of beheer je abonnement.");
            if (state.checkoutSession()!=null) {
                var session=stripe.get("/v1/checkout/sessions/"+StripeHttpGateway.encode(state.checkoutSession())); testObject(session);
                String status=session.path("status").asText();
                if ("open".equals(status)) return;
                if (!("expired".equals(status) || "complete".equals(status))) throw conflict("Controleer eerst je vorige betaling.");
                jdbc.update("UPDATE billing_account SET checkout_key=NULL,checkout_session_id=NULL,checkout_started_at=NULL,checkout_price=NULL WHERE user_id=?",user.id());
                state=lock(user.id());
            }
            if (state.checkoutKey()==null) jdbc.update("UPDATE billing_account SET checkout_key=?,checkout_price=?,checkout_started_at=? WHERE user_id=?",
                UUID.randomUUID(),settings.price,sql(Instant.now()),user.id());
        });
        return tx.execute(ignored->{
            var state=lock(user.id());
            if (state.checkoutSession()!=null) {
                var session=stripe.get("/v1/checkout/sessions/"+StripeHttpGateway.encode(state.checkoutSession())); testObject(session);
                if (!"open".equals(session.path("status").asText())) throw conflict("Je betaling wordt verwerkt. Vernieuw de abonnementsstatus.");
                return new Link(hostedUrl(session,"checkout.stripe.com"));
            }
            recent(state.checkoutStarted());
            if (!settings.price.equals(state.checkoutPrice())) throw conflict("Los de vorige testaanvraag op voordat je de prijs wijzigt.");
            approvedPrice(state.checkoutPrice());
            var parameters=new LinkedHashMap<String,String>();
            parameters.put("mode","subscription"); parameters.put("customer",state.customer());
            parameters.put("line_items[0][price]",state.checkoutPrice()); parameters.put("line_items[0][quantity]","1");
            parameters.put("payment_method_types[0]","card");
            parameters.put("success_url",settings.baseUrl+"/app/index.html?billing=success");
            parameters.put("cancel_url",settings.baseUrl+"/app/index.html?billing=cancel");
            parameters.put("client_reference_id",user.id().toString());
            parameters.put("subscription_data[metadata][cap_account_id]",user.id().toString());
            parameters.put("custom_text[submit][message]","TEST MODE - "+MembershipPolicy.PLAN+", "+(MembershipPolicy.MONTHLY_CENTS/100)+" "+MembershipPolicy.CURRENCY+"/maand. Geen echt CAP-aanbod. Geen proefperiode.");
            var session=stripe.post("/v1/checkout/sessions",parameters,"cap-test-checkout-"+state.checkoutKey()); testObject(session);
            String sessionId=session.path("id").asText();
            if (!sessionId.startsWith("cs_test_") || !state.customer().equals(id(session.path("customer")))) throw conflict("Ongeldige Stripe TEST-sessie.");
            String url=hostedUrl(session,"checkout.stripe.com");
            jdbc.update("UPDATE billing_account SET checkout_session_id=? WHERE user_id=?",sessionId,user.id());
            return new Link(url);
        });
    }
    public Link portal(Account user) {
        settings.requireEnabled();
        String customer=jdbc.query("SELECT customer_id FROM billing_account WHERE user_id=?",(r,i)->r.getString(1),user.id()).stream().filter(Objects::nonNull).findFirst()
            .orElseThrow(()->conflict("Start eerst een testabonnement."));
        var configuration=stripe.get("/v1/billing_portal/configurations/"+settings.portal); testObject(configuration);
        var features=configuration.path("features");
        if (!configuration.path("active").asBoolean() || !features.path("subscription_cancel").path("enabled").asBoolean()
            || !"at_period_end".equals(features.path("subscription_cancel").path("mode").asText()) || features.path("subscription_update").path("enabled").asBoolean())
            throw conflict("Configureer het TEST-portaal: opzeggen op periode-einde, zonder planwijzigingen.");
        var session=stripe.post("/v1/billing_portal/sessions",Map.of("customer",customer,"configuration",settings.portal,"return_url",settings.baseUrl+"/app/index.html?billing=return"),null);
        return new Link(hostedUrl(session,"billing.stripe.com"));
    }
    /** Fetch canonical Stripe state under the owner lock: old events cannot roll back newer state. */
    private void synchronize(State state) {
        if (state.customer()==null) return;
        var all=subscriptions(state.customer());
        jdbc.update("UPDATE billing_subscription SET status='missing',payment_status='unknown' WHERE user_id=?",state.owner());
        for (var sub:all) {
            var items=sub.path("items").path("data");
            String price=items.size()==1?id(items.get(0).path("price")):"unsupported";
            var item=items.size()==1?items.get(0):sub;
            Instant start=instant(item.path("current_period_start")),end=instant(item.path("current_period_end"));
            String status=sub.path("status").asText("unknown");
            String payment=sub.path("latest_invoice").path("status").asText("unknown");
            if (sub.hasNonNull("pause_collection") || !settings.price.equals(price)) payment="unsupported";
            String subscriptionId=sub.path("id").asText();
            if (!subscriptionId.matches("sub_[A-Za-z0-9]+")) throw conflict("Ongeldig Stripe-abonnement.");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM billing_subscription WHERE subscription_id=? AND user_id<>?",Integer.class,subscriptionId,state.owner())>0)
                throw conflict("Abonnement is aan een ander account gekoppeld.");
            int updated=jdbc.update("""
                UPDATE billing_subscription SET customer_id=?,price_id=?,status=?,payment_status=?,period_start=?,period_end=?,
                cancel_at_period_end=?,cancel_at=?,canceled_at=?,ended_at=?,synced_at=? WHERE subscription_id=? AND user_id=?
                """,state.customer(),price,status,payment,sql(start),sql(end),sub.path("cancel_at_period_end").asBoolean(),
                sql(instant(sub.path("cancel_at"))),sql(instant(sub.path("canceled_at"))),sql(instant(sub.path("ended_at"))),sql(Instant.now()),subscriptionId,state.owner());
            if (updated==0) jdbc.update("""
                INSERT INTO billing_subscription(subscription_id,user_id,customer_id,price_id,status,payment_status,
                period_start,period_end,cancel_at_period_end,cancel_at,canceled_at,ended_at,synced_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,subscriptionId,state.owner(),state.customer(),price,status,payment,sql(start),sql(end),sub.path("cancel_at_period_end").asBoolean(),
                sql(instant(sub.path("cancel_at"))),sql(instant(sub.path("canceled_at"))),sql(instant(sub.path("ended_at"))),sql(Instant.now()));
        }
        jdbc.update("UPDATE billing_account SET synced_at=? WHERE user_id=?",sql(Instant.now()),state.owner());
    }
    public Summary reconcile(Account user) {
        settings.requireEnabled();
        // Reading an unpaid account must not start the 23-hour remote-request retry window.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM billing_account WHERE user_id=?",Integer.class,user.id())==0) return summary(user);
        tx.executeWithoutResult(ignored->synchronize(lock(user.id())));
        return summary(user);
    }
    public Summary summary(Account user) {
        var summaries=jdbc.query("""
            SELECT s.*, a.synced_at AS account_synced FROM billing_subscription s
            JOIN billing_account a ON a.user_id=s.user_id WHERE s.user_id=?
            ORDER BY CASE WHEN s.status='active' THEN 0 WHEN s.status IN ('canceled','incomplete_expired') THEN 2 ELSE 1 END,
                s.period_end DESC NULLS LAST
            """,(r,i)->new Summary(settings.enabled,true,MembershipPolicy.PLAN,MembershipPolicy.MONTHLY_CENTS,MembershipPolicy.CURRENCY,r.getString("status"),
                settings.enabled && settings.price.equals(r.getString("price_id")) && MembershipPolicy.grantsAccess(r.getString("status"),r.getString("payment_status"),timestamp(r,"period_end"),Instant.now()),
                timestamp(r,"period_start"),timestamp(r,"period_end"),r.getBoolean("cancel_at_period_end"),timestamp(r,"cancel_at"),timestamp(r,"canceled_at"),timestamp(r,"account_synced"),true),user.id());
        if (!summaries.isEmpty()) return summaries.stream().filter(Summary::membershipAccess).findFirst().orElse(summaries.getFirst());
        return jdbc.query("SELECT customer_id,synced_at FROM billing_account WHERE user_id=?",(r,i)->new Summary(settings.enabled,true,MembershipPolicy.PLAN,MembershipPolicy.MONTHLY_CENTS,MembershipPolicy.CURRENCY,"none",false,null,null,false,null,null,timestamp(r,"synced_at"),r.getString("customer_id")!=null),user.id())
            .stream().findFirst().orElse(new Summary(settings.enabled,true,MembershipPolicy.PLAN,MembershipPolicy.MONTHLY_CENTS,MembershipPolicy.CURRENCY,"none",false,null,null,false,null,null,null,false));
    }
    public void event(String eventId,String type,String customer) {
        var owners=jdbc.query("SELECT user_id FROM billing_account WHERE customer_id=?",(r,i)->r.getObject(1,UUID.class),customer);
        // Events for unrelated Stripe customers do not belong to CAP.
        if (owners.isEmpty()) return;
        tx.executeWithoutResult(ignored->{
            var state=lock(owners.getFirst());
            if (jdbc.queryForObject("SELECT COUNT(*) FROM billing_event WHERE event_id=?",Integer.class,eventId)>0) return;
            synchronize(state);
            jdbc.update("INSERT INTO billing_event(event_id,event_type,user_id,processed_at) VALUES(?,?,?,?)",eventId,type,state.owner(),sql(Instant.now()));
        });
        LOG.info("CAP Stripe TEST event handled: type={}",type);
    }
}
