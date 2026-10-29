package be.cap.backend.accounts.billing;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Called within BillingService's owner lock/transaction: fulfilment is exactly once. */
@Service
@Profile("accounts")
public class CatalogOrders {
    public record Order(UUID id,UUID owner,String offer,String price,String session,Instant paid,Instant end) {}
    private final JdbcTemplate jdbc;
    private final StripeGateway stripe;
    private final BillingCatalog catalog;
    public CatalogOrders(JdbcTemplate jdbc,StripeGateway stripe,BillingCatalog catalog) { this.jdbc=jdbc;this.stripe=stripe;this.catalog=catalog; }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT,text); }
    private Instant time(JsonNode n) { return n.isNumber()?Instant.ofEpochSecond(n.asLong()):null; }
    private Timestamp sql(Instant n) { return n==null?null:Timestamp.from(n); }
    private Instant timestamp(java.sql.ResultSet r,String name) throws java.sql.SQLException { var t=r.getTimestamp(name); return t==null?null:t.toInstant(); }
    private String id(JsonNode n) { return n.isTextual()?n.asText():n.path("id").asText(); }
    private void test(JsonNode n) { if (!n.has("livemode") || n.path("livemode").asBoolean(true)) throw conflict("Alleen Stripe TEST-objecten zijn toegestaan."); }
    private Order row(java.sql.ResultSet r,int i) throws java.sql.SQLException {
        return new Order(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("offer_code"),r.getString("price_id"),r.getString("session_id"),timestamp(r,"paid_at"),timestamp(r,"period_end"));
    }
    public void reserve(UUID key,UUID owner,String offer,String price,Instant created) {
        jdbc.update("INSERT INTO billing_order(id,user_id,offer_code,price_id,created_at) VALUES(?,?,?,?,?)",key,owner,offer,price,sql(created));
    }
    public void attach(UUID key,String session) { jdbc.update("UPDATE billing_order SET session_id=? WHERE id=?",session,key); }
    public boolean hasOrders(UUID owner) { return jdbc.queryForObject("SELECT COUNT(*) FROM billing_order WHERE user_id=?",Integer.class,owner)>0; }
    public int balance(UUID owner) { return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_credit WHERE user_id=? AND session_id IS NULL",Integer.class,owner); }
    public boolean hasPrepaidAccess(UUID owner,Instant when) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billing_order WHERE user_id=? AND status='paid' AND paid_at<=? AND period_end>? AND subscription_id IS NULL",Integer.class,owner,sql(when),sql(when))>0;
    }
    public Order prepaid(UUID owner) {
        return jdbc.query("SELECT * FROM billing_order WHERE user_id=? AND status='paid' AND subscription_id IS NULL AND paid_at<=CURRENT_TIMESTAMP AND period_end>CURRENT_TIMESTAMP ORDER BY period_end DESC",this::row,owner).stream().findFirst().orElse(null);
    }
    public Instant termEnd(UUID owner,String sub) {
        var ends=jdbc.query("SELECT period_end FROM billing_order WHERE user_id=? AND subscription_id=?",(r,i)->timestamp(r,"period_end"),owner,sub);
        return ends.isEmpty()?null:ends.getFirst();
    }
    private List<Order> pending(UUID owner) { return jdbc.query("SELECT * FROM billing_order WHERE user_id=? AND status='pending'",this::row,owner); }
    private JsonNode session(String session) {
        if (!session.matches("cs_test_[A-Za-z0-9]+")) throw conflict("Ongeldige TEST-betaalsessie.");
        var value=stripe.get("/v1/checkout/sessions/"+session+"?expand%5B%5D=line_items&expand%5B%5D=payment_intent.latest_charge");
        test(value); return value;
    }
    public void recover(UUID owner,String customer,String sessionId) {
        if (pending(owner).isEmpty()) return;
        var value=session(sessionId);
        String key=value.path("metadata").path("cap_order_id").asText();
        for (var order:pending(owner)) if (order.id().toString().equals(key)) {
            validate(customer,order,value);
            if (order.session()!=null && !order.session().equals(sessionId)) throw conflict("Betaalsessie komt niet overeen met de bestelling.");
            attach(order.id(),sessionId);
            jdbc.update("UPDATE billing_account SET checkout_session_id=? WHERE user_id=? AND checkout_key=?",sessionId,owner,order.id());
        }
    }
    private void validate(String customer,Order order,JsonNode value) {
        var offer=catalog.require(order.offer()); var items=value.path("line_items").path("data");
        if (!customer.equals(id(value.path("customer"))) || !order.owner().toString().equals(value.path("client_reference_id").asText())
            || !order.id().toString().equals(value.path("metadata").path("cap_order_id").asText())
            || !order.offer().equals(value.path("metadata").path("cap_offer").asText())
            || !"eur".equals(value.path("currency").asText()) || value.path("amount_total").asInt(-1)!=offer.cents()
            || !(offer.recurring()?"subscription":"payment").equals(value.path("mode").asText())
            || items.size()!=1 || value.path("line_items").path("has_more").asBoolean()
            || !order.price().equals(id(items.path(0).path("price"))) || items.path(0).path("quantity").asInt()!=1)
            throw conflict("De betaling past niet bij deze CAP-bestelling. Laat de testconfiguratie controleren.");
    }
    public void synchronize(UUID owner,String customer) {
        for (var order:pending(owner)) {
            if (order.session()==null) continue;
            var value=session(order.session()); validate(customer,order,value);
            if ("expired".equals(value.path("status").asText())) {
                jdbc.update("UPDATE billing_order SET status='expired' WHERE id=?",order.id()); continue;
            }
            if (!"complete".equals(value.path("status").asText()) || !"paid".equals(value.path("payment_status").asText())) continue;
            var offer=catalog.require(order.offer());
            if (offer.recurring()) {
                String sub=id(value.path("subscription"));
                if (!sub.matches("sub_[A-Za-z0-9]+")) throw conflict("Abonnement wordt nog bevestigd.");
                jdbc.update("UPDATE billing_order SET subscription_id=?,status='paid' WHERE id=?",sub,order.id());
            } else {
                var payment=value.path("payment_intent"); test(payment);
                var charge=payment.path("latest_charge"); test(charge);
                Instant paid=time(charge.path("created"));
                if (!"succeeded".equals(payment.path("status").asText()) || !charge.path("paid").asBoolean() || charge.path("refunded").asBoolean()
                    || charge.path("amount_refunded").asLong()!=0 || paid==null) throw conflict("De testbetaling is nog niet definitief bevestigd.");
                Instant end=offer.months()>0?paid.atZone(ZoneId.of("Europe/Brussels")).plusMonths(offer.months()).toInstant():null;
                jdbc.update("UPDATE billing_order SET status='paid',paid_at=?,period_end=? WHERE id=?",sql(paid),sql(end),order.id());
                for(int n=1;n<=offer.credits();n++) jdbc.update("INSERT INTO lesson_credit(id,user_id,order_id,credit_number) VALUES(?,?,?,?)",UUID.randomUUID(),owner,order.id(),n);
            }
        }
    }
    public JsonNode enforceTerm(UUID owner,String customer,JsonNode sub) {
        var items=sub.path("items").path("data");
        if (items.size()!=1) return sub;
        var offer=catalog.byPrice(id(items.path(0).path("price"))).orElse(null);
        if (offer==null || offer.code().equals("legacy_test") || !offer.recurring()) return sub;
        String key=sub.path("metadata").path("cap_order_id").asText();
        var matching=jdbc.query("SELECT * FROM billing_order WHERE user_id=? AND offer_code=? AND CAST(id AS VARCHAR)=?",this::row,owner,offer.code(),key);
        if (matching.isEmpty()) return sub;
        Instant anchor=time(sub.path("billing_cycle_anchor"));
        if (anchor==null || !owner.toString().equals(sub.path("metadata").path("cap_account_id").asText()) || !customer.equals(id(sub.path("customer"))))
            throw conflict("Abonnement is nog niet aan de juiste bestelling gekoppeld.");
        var order=matching.getFirst();
        if (!order.price().equals(id(items.path(0).path("price"))) || items.path(0).path("quantity").asInt()!=1)
            throw conflict("Abonnement komt niet overeen met de gekozen CAP-bestelling.");
        Instant term=order.end()==null?anchor.atZone(ZoneOffset.UTC).plusMonths(12).toInstant():order.end();
        jdbc.update("UPDATE billing_order SET subscription_id=?,period_end=? WHERE id=?",sub.path("id").asText(),sql(term),order.id());
        Instant cancel=time(sub.path("cancel_at"));
        if (MembershipPolicy.blocksAnotherSubscription(sub.path("status").asText()) && term.isAfter(Instant.now()) && (cancel==null || cancel.isAfter(term))) {
            var updated=stripe.post("/v1/subscriptions/"+sub.path("id").asText(),Map.of("cancel_at",Long.toString(term.getEpochSecond()),"proration_behavior","none"),
                null); // Setting a fixed cancel_at with no proration is itself idempotent; don't replay a cached response after external changes.
            test(updated);
            if (!term.equals(time(updated.path("cancel_at")))) throw conflict("De einddatum kon nog niet bevestigd worden. Werk de status opnieuw bij.");
            ((tools.jackson.databind.node.ObjectNode)sub).put("cancel_at",term.getEpochSecond());
        }
        return sub;
    }
}
