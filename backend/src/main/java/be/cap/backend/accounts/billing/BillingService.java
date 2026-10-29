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
        Instant cancelAt, Instant canceledAt, Instant syncedAt, boolean hasCustomer,
        List<BillingCatalog.Offer> offers, int credits, Instant termEnd, boolean canCancel) {}
    public record Link(String url) {}
    public record AthleteSummary(UUID athleteId, String plan, String status, boolean membershipAccess,
        Instant paidUntil, int credits, boolean cancellationPlanned, Instant termEnd) {}
    private record State(UUID owner,String customer,UUID customerKey,Instant customerStarted,
        UUID checkoutKey,String checkoutPrice,Instant checkoutStarted,String checkoutSession) {}
    private final JdbcTemplate jdbc;
    private final StripeGateway stripe;
    private final BillingSettings settings;
    private final BillingCatalog catalog;
    private final CatalogOrders orders;
    private final be.cap.backend.accounts.MembershipReviewAccess membershipReview;
    private final TransactionTemplate tx;
    public BillingService(JdbcTemplate jdbc,StripeGateway stripe,BillingSettings settings,BillingCatalog catalog,CatalogOrders orders,PlatformTransactionManager manager,be.cap.backend.accounts.MembershipReviewAccess membershipReview) {
        this.jdbc=jdbc; this.stripe=stripe; this.settings=settings; this.catalog=catalog; this.orders=orders; this.tx=new TransactionTemplate(manager); this.membershipReview=membershipReview;
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
    private void approvedPrice(String price, BillingCatalog.Offer offer) {
        var p=stripe.get("/v1/prices/"+StripeHttpGateway.encode(price)); testObject(p);
        boolean recurrence=offer.recurring() ? "month".equals(p.path("recurring").path("interval").asText())
            && p.path("recurring").path("interval_count").asInt()==1
            && !"metered".equals(p.path("recurring").path("usage_type").asText()) : !p.hasNonNull("recurring");
        if (!p.path("active").asBoolean() || !offer.currency().equalsIgnoreCase(p.path("currency").asText())
            || p.path("unit_amount").asLong(-1)!=offer.cents() || !recurrence
            || !"per_unit".equals(p.path("billing_scheme").asText()) || p.hasNonNull("transform_quantity"))
            throw conflict("De Stripe TEST-prijs past niet bij dit CAP-aanbod. Controleer de testconfiguratie.");
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
    public Link checkout(Account user) { return checkout(user,"legacy_test"); }
    public Link checkout(Account user, String offerCode) {
        settings.requireEnabled();
        var offer=catalog.require(offerCode);
        String selectedPrice=catalog.price(offerCode);
        if (!offer.available()) throw conflict("Dit aanbod is nog niet ingesteld in Stripe TEST. Laat de testcatalogus instellen.");
        approvedPrice(selectedPrice,offer); ensure(user);
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
            if (offer.credits()==0 && subscriptions(state.customer()).stream().anyMatch(s->MembershipPolicy.blocksAnotherSubscription(s.path("status").asText())))
                throw conflict("Je hebt al een lopend of onvoltooid testabonnement. Vernieuw de status of beheer je abonnement.");
            orders.synchronize(state.owner(),state.customer());
            if (offer.credits()==0 && orders.hasPrepaidAccess(user.id(),Instant.now()))
                throw conflict("Je hebt al een betaald lidmaatschap. Wacht tot het eindigt voordat je een nieuw koopt.");
            if (state.checkoutSession()!=null) {
                var session=stripe.get("/v1/checkout/sessions/"+StripeHttpGateway.encode(state.checkoutSession())); testObject(session);
                String status=session.path("status").asText();
                if ("open".equals(status)) {
                    if (!selectedPrice.equals(state.checkoutPrice())) throw conflict("Er staat nog een andere betaling open. Rond die af of wacht tot de betaallink verlopen is.");
                    return;
                }
                if (!("expired".equals(status) || "complete".equals(status))) throw conflict("Controleer eerst je vorige betaling.");
                jdbc.update("UPDATE billing_account SET checkout_key=NULL,checkout_session_id=NULL,checkout_started_at=NULL,checkout_price=NULL,checkout_offer=NULL WHERE user_id=?",user.id());
                state=lock(user.id());
            }
            if (state.checkoutKey()==null) {
                UUID key=UUID.randomUUID(); Instant started=Instant.now();
                jdbc.update("UPDATE billing_account SET checkout_key=?,checkout_price=?,checkout_started_at=?,checkout_offer=? WHERE user_id=?",
                    key,selectedPrice,sql(started),offerCode,user.id());
                if (!offerCode.equals("legacy_test")) orders.reserve(key,user.id(),offerCode,selectedPrice,started);
            } else if (!selectedPrice.equals(state.checkoutPrice())) throw conflict("Herstel eerst je vorige betaling voordat je een ander aanbod kiest.");
        });
        return tx.execute(ignored->{
            var state=lock(user.id());
            if (state.checkoutSession()!=null) {
                var session=stripe.get("/v1/checkout/sessions/"+StripeHttpGateway.encode(state.checkoutSession())); testObject(session);
                if (!"open".equals(session.path("status").asText())) throw conflict("Je betaling wordt verwerkt. Vernieuw de abonnementsstatus.");
                return new Link(hostedUrl(session,"checkout.stripe.com"));
            }
            recent(state.checkoutStarted());
            if (!selectedPrice.equals(state.checkoutPrice())) throw conflict("Los de vorige testaanvraag op voordat je de prijs wijzigt.");
            approvedPrice(state.checkoutPrice(),offer);
            var parameters=new LinkedHashMap<String,String>();
            parameters.put("mode",offer.recurring()?"subscription":"payment"); parameters.put("customer",state.customer());
            parameters.put("line_items[0][price]",state.checkoutPrice()); parameters.put("line_items[0][quantity]","1");
            parameters.put("payment_method_types[0]","card");
            parameters.put("success_url",settings.baseUrl+"/app/index.html?billing=success");
            parameters.put("cancel_url",settings.baseUrl+"/app/index.html?billing=cancel");
            parameters.put("client_reference_id",user.id().toString());
            parameters.put("metadata[cap_order_id]",state.checkoutKey().toString());
            parameters.put("metadata[cap_offer]",offerCode);
            if (offer.recurring()) {
                parameters.put("subscription_data[metadata][cap_account_id]",user.id().toString());
                parameters.put("subscription_data[metadata][cap_order_id]",state.checkoutKey().toString());
            }
            parameters.put("custom_text[submit][message]","TEST MODE - "+offer.name()+". "+offer.terms());
            var session=stripe.post("/v1/checkout/sessions",parameters,"cap-test-checkout-"+state.checkoutKey()); testObject(session);
            String sessionId=session.path("id").asText();
            if (!sessionId.startsWith("cs_test_") || !state.customer().equals(id(session.path("customer")))) throw conflict("Ongeldige Stripe TEST-sessie.");
            String url=hostedUrl(session,"checkout.stripe.com");
            jdbc.update("UPDATE billing_account SET checkout_session_id=? WHERE user_id=?",sessionId,user.id());
            if (!offerCode.equals("legacy_test")) orders.attach(state.checkoutKey(),sessionId);
            return new Link(url);
        });
    }
    public Link portal(Account user) {
        settings.requireEnabled();
        String customer=jdbc.query("SELECT customer_id FROM billing_account WHERE user_id=?",(r,i)->r.getString(1),user.id()).stream().filter(Objects::nonNull).findFirst()
            .orElseThrow(()->conflict("Start eerst een testabonnement."));
        var configuration=stripe.get("/v1/billing_portal/configurations/"+settings.portal); testObject(configuration);
        var features=configuration.path("features");
        boolean catalogCustomer=orders.hasOrders(user.id());
        boolean cancellation=features.path("subscription_cancel").path("enabled").asBoolean();
        if (!configuration.path("active").asBoolean() || features.path("subscription_update").path("enabled").asBoolean()
            || features.path("subscription_pause").path("enabled").asBoolean()
            || (catalogCustomer && cancellation) || (cancellation && !"at_period_end".equals(features.path("subscription_cancel").path("mode").asText())))
            throw conflict("Gebruik het juiste TEST-portaal: voor de CAP-catalogus zonder opzeggen, pauzeren of planwijzigingen. Opzeggen doe je in CAP.");
        var session=stripe.post("/v1/billing_portal/sessions",Map.of("customer",customer,"configuration",settings.portal,"return_url",settings.baseUrl+"/app/index.html?billing=return"),null);
        return new Link(hostedUrl(session,"billing.stripe.com"));
    }
    /** Fetch canonical Stripe state under the owner lock: old events cannot roll back newer state. */
    private void synchronize(State state) {
        if (state.customer()==null) return;
        orders.synchronize(state.owner(),state.customer());
        var all=subscriptions(state.customer());
        jdbc.update("UPDATE billing_subscription SET status='missing',payment_status='unknown' WHERE user_id=?",state.owner());
        for (var original:all) {
            var sub=orders.enforceTerm(state.owner(),state.customer(),original);
            var items=sub.path("items").path("data");
            String price=items.size()==1?id(items.get(0).path("price")):"unsupported";
            var item=items.size()==1?items.get(0):sub;
            Instant start=instant(item.path("current_period_start")),end=instant(item.path("current_period_end"));
            String status=sub.path("status").asText("unknown");
            String payment=sub.path("latest_invoice").path("status").asText("unknown");
            if (sub.hasNonNull("pause_collection") || catalog.byPrice(price).isEmpty()
                || (catalog.byPrice(price).isPresent() && !catalog.byPrice(price).get().code().equals("legacy_test") && item.path("quantity").asInt()!=1)) payment="unsupported";
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
            var offer=catalog.byPrice(price).orElse(null);
            Instant term=orders.termEnd(state.owner(),subscriptionId);
            if (offer!=null && !offer.code().equals("legacy_test") && term==null)
                jdbc.update("UPDATE billing_subscription SET payment_status='unsupported' WHERE subscription_id=?",subscriptionId);
            jdbc.update("UPDATE billing_subscription SET offer_code=?,term_end=? WHERE subscription_id=?",offer==null?null:offer.code(),sql(term),subscriptionId);
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
        int balance=orders.balance(user.id());
        var summaries=jdbc.query("""
            SELECT s.*, a.synced_at AS account_synced FROM billing_subscription s
            JOIN billing_account a ON a.user_id=s.user_id WHERE s.user_id=?
            ORDER BY CASE WHEN s.status='active' THEN 0 WHEN s.status IN ('canceled','incomplete_expired') THEN 2 ELSE 1 END,
                s.period_end DESC NULLS LAST
            """,(r,i)-> {
                var offer=catalog.byPrice(r.getString("price_id")).orElse(catalog.require("legacy_test"));
                Instant term=timestamp(r,"term_end"),end=timestamp(r,"period_end"),cancel=timestamp(r,"cancel_at");
                boolean access=settings.enabled && catalog.byPrice(r.getString("price_id")).isPresent()
                    && (term==null || term.isAfter(Instant.now()))
                    && MembershipPolicy.grantsAccess(r.getString("status"),r.getString("payment_status"),end,Instant.now());
                boolean ending=r.getBoolean("cancel_at_period_end") || (cancel!=null && end!=null && !cancel.isAfter(end));
                return new Summary(settings.enabled,true,offer.name(),offer.cents(),"EUR",r.getString("status"),access,
                    timestamp(r,"period_start"),end,ending,cancel,timestamp(r,"canceled_at"),timestamp(r,"account_synced"),true,
                    catalog.offers(),balance,term,access && offer.code().equals("monthly_flexible") && !ending);
            },user.id());
        var prepaid=orders.prepaid(user.id());
        if (prepaid!=null) return new Summary(settings.enabled,true,catalog.require(prepaid.offer()).name(),catalog.require(prepaid.offer()).cents(),"EUR","active",settings.enabled,
            prepaid.paid(),prepaid.end(),false,null,null,null,true,catalog.offers(),balance,prepaid.end(),false);
        if (!summaries.isEmpty()) return summaries.stream().filter(Summary::membershipAccess).findFirst().orElse(summaries.getFirst());
        boolean customer=jdbc.queryForObject("SELECT COUNT(*) FROM billing_account WHERE user_id=? AND customer_id IS NOT NULL",Integer.class,user.id())>0;
        return new Summary(settings.enabled,true,"CAP lidmaatschap",0,"EUR","none",false,null,null,false,null,null,null,customer,catalog.offers(),balance,null,false);
    }
    /** Coaches inspect local records only; this never creates a Stripe customer or payment link. */
    public AthleteSummary athleteSummary(Account coach, UUID athleteId) {
        if (!membershipReview.allows(coach)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Je hebt geen toestemming om lidmaatschappen van sporters te bekijken.");
        var athlete=jdbc.query("SELECT id,email,display_name,role FROM cap_user WHERE id=? AND role='ATHLETE'",
            (r,i)->new Account(r.getObject("id",UUID.class),r.getString("email"),r.getString("display_name"),r.getString("role")),athleteId)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Sporter niet gevonden."));
        var value=summary(athlete);
        return new AthleteSummary(athlete.id(),value.plan(),value.status(),value.membershipAccess(),value.periodEnd(),
            value.credits(),value.cancelAtPeriodEnd(),value.termEnd());
    }
    public void event(String eventId,String type,String customer,String sessionId) {
        var owners=jdbc.query("SELECT user_id FROM billing_account WHERE customer_id=?",(r,i)->r.getObject(1,UUID.class),customer);
        if (owners.isEmpty()) return;
        tx.executeWithoutResult(ignored->{
            var state=lock(owners.getFirst());
            if (jdbc.queryForObject("SELECT COUNT(*) FROM billing_event WHERE event_id=?",Integer.class,eventId)>0) return;
            if (sessionId!=null && !sessionId.isBlank()) orders.recover(state.owner(),state.customer(),sessionId);
            synchronize(state);
            jdbc.update("INSERT INTO billing_event(event_id,event_type,user_id,processed_at) VALUES(?,?,?,?)",eventId,type,state.owner(),sql(Instant.now()));
        });
        LOG.info("CAP Stripe TEST event handled: type={}",type);
    }
    public Summary cancel(Account user) {
        settings.requireEnabled();
        if (!orders.hasOrders(user.id())) throw conflict("Geen opzegbaar CAP-abonnement gevonden.");
        tx.executeWithoutResult(ignored->{
            var state=lock(user.id()); synchronize(state);
            var eligible=jdbc.query("SELECT subscription_id,period_end FROM billing_subscription WHERE user_id=? AND offer_code='monthly_flexible' AND status='active' AND payment_status='paid' AND period_end>CURRENT_TIMESTAMP",
                (r,i)->Map.entry(r.getString("subscription_id"),timestamp(r,"period_end")),user.id());
            if (eligible.size()!=1) throw conflict("Alleen het maandelijkse opzegbare abonnement kan hier worden opgezegd.");
            var chosen=eligible.getFirst();
            var updated=stripe.post("/v1/subscriptions/"+chosen.getKey(),Map.of("cancel_at",Long.toString(chosen.getValue().getEpochSecond()),"proration_behavior","none"),
                null); // A fixed date assignment is safe to retry, including after an external portal change.
            testObject(updated);
            if (!chosen.getValue().equals(instant(updated.path("cancel_at")))) throw conflict("Opzegging is nog niet bevestigd. Werk de status opnieuw bij.");
            synchronize(state);
        });
        return summary(user);
    }
}
