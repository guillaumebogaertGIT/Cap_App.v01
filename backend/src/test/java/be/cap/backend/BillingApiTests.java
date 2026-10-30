package be.cap.backend;

import be.cap.backend.accounts.billing.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HTTP + real SQL integration with an explicitly fake Stripe boundary. Never calls Stripe. */
@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:billing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "cap.billing.enabled=true","cap.billing.secret=sk_test_FAKE","cap.billing.webhook-secret=whsec_FAKE",
    "cap.billing.price=price_CAPtest","cap.billing.portal=bpc_CAPtest"})
class BillingApiTests extends AccountApiTests {
    @MockitoBean StripeGateway stripe;
    FakeStripe fake;
    @BeforeEach void configureStripe() {
        fake=new FakeStripe();
        when(stripe.get(anyString())).thenAnswer(call->fake.get(call.getArgument(0)));
        when(stripe.post(anyString(),anyMap(),nullable(String.class))).thenAnswer(call->fake.post(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
    }
    class FakeStripe {
        final String run=UUID.randomUUID().toString().replace("-","");
        final AtomicInteger customers=new AtomicInteger(), sessions=new AtomicInteger(), syncs=new AtomicInteger();
        final Map<String,JsonNode> results=new ConcurrentHashMap<>();
        final Map<String,List<JsonNode>> subscriptions=new ConcurrentHashMap<>();
        volatile boolean loseResponse, failSync;
        volatile Map<String,String> portalParameters;
        JsonNode tree(Object value) { return json.readTree(json.writeValueAsString(value)); }
        JsonNode get(String path) {
            if(path.startsWith("/v1/prices/")) return tree(Map.of("livemode",false,"active",true,"currency","eur","unit_amount",1000,
                "billing_scheme","per_unit","recurring",Map.of("interval","month","interval_count",1)));
            if(path.startsWith("/v1/subscriptions?")) {
                syncs.incrementAndGet();
                if(failSync) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,"Mock Stripe unavailable");
                String customer=java.net.URLDecoder.decode(path.split("customer=")[1].split("&")[0],StandardCharsets.UTF_8);
                return tree(Map.of("data",subscriptions.getOrDefault(customer,List.of()),"has_more",false));
            }
            if(path.startsWith("/v1/checkout/sessions/")) return results.values().stream().filter(v->v.path("id").asText().equals(path.substring(path.lastIndexOf('/')+1))).findFirst().orElseThrow();
            if(path.startsWith("/v1/billing_portal/configurations/")) return tree(Map.of("livemode",false,"active",true,"features",Map.of(
                "subscription_cancel",Map.of("enabled",true,"mode","at_period_end"),"subscription_update",Map.of("enabled",false))));
            throw new AssertionError("Unexpected mock Stripe GET: "+path);
        }
        JsonNode post(String path,Map<String,String> parameters,String key) {
            if(path.equals("/v1/billing_portal/sessions")) { portalParameters=Map.copyOf(parameters); return tree(Map.of("url","https://billing.stripe.com/p/session/FAKE")); }
            var result=results.computeIfAbsent(key,k->{
                if(path.equals("/v1/customers")) return tree(Map.of("livemode",false,"id","cus_Mock"+run+customers.incrementAndGet()));
                assertEquals("price_CAPtest",parameters.get("line_items[0][price]"));
                assertEquals("subscription",parameters.get("mode")); assertFalse(parameters.containsKey("subscription_data[trial_period_days]"));
                return tree(Map.of("livemode",false,"id","cs_test_Mock"+sessions.incrementAndGet(),"status","open",
                    "customer",parameters.get("customer"),"url","https://checkout.stripe.com/c/pay/FAKE"));
            });
            if(path.equals("/v1/checkout/sessions") && loseResponse) { loseResponse=false; throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,"Mock lost response"); }
            return result;
        }
        void state(String customer,String status,boolean cancel,String payment,Instant end) {
            long now=Instant.now().getEpochSecond();
            var sub=new HashMap<String,Object>(); sub.put("livemode",false); sub.put("id","sub_"+customer.replace("cus_",""));
            sub.put("customer",customer); sub.put("status",status); sub.put("cancel_at_period_end",cancel);
            sub.put("latest_invoice",Map.of("status",payment));
            sub.put("items",Map.of("data",List.of(Map.of("price",Map.of("id","price_CAPtest"),"current_period_start",now-60,"current_period_end",end.getEpochSecond()))));
            if(cancel) sub.put("cancel_at",end.getEpochSecond());
            subscriptions.put(customer,List.of(tree(sub)));
        }
    }
    String customer(String owner) { return jdbc.queryForObject("SELECT customer_id FROM billing_account WHERE user_id=?",String.class,UUID.fromString(owner)); }
    JsonNode status(HttpClient client) throws Exception { return json.readTree(request(client,"GET","/api/billing",null,null,false).body()); }
    String event(String type,String customer) throws Exception {
        return json.writeValueAsString(Map.of("id","evt_"+UUID.randomUUID().toString().replace("-",""),"livemode",false,"type",type,
            "data",Map.of("object",Map.of("customer",customer,"status","stale_payload_status"))));
    }
    String signature(String body,long time) throws Exception {
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("whsec_FAKE".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return "t="+time+",v1="+HexFormat.of().formatHex(mac.doFinal((time+"."+body).getBytes(StandardCharsets.UTF_8)));
    }
    HttpResponse<String> webhook(String body,String signature) throws Exception {
        return client().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/billing/webhook"))
            .header("Content-Type","application/json").header("Stripe-Signature",signature)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    void deliver(String type,String customer) throws Exception {
        String body=event(type,customer);assertEquals(204,webhook(body,signature(body,Instant.now().getEpochSecond())).statusCode());
    }
    @Test void coachesHavePermanentFreeAccessAndCannotUseSelfServicePayments() throws Exception {
        var coach=client();login(coach,"coach@example.test","Coach-test-password-123");
        var before=jdbc.queryForObject("SELECT COUNT(*) FROM billing_account",Integer.class);
        var value=status(coach);
        assertEquals("coach",value.path("status").asText());
        assertTrue(value.path("membershipAccess").asBoolean());
        assertFalse(value.path("configured").asBoolean());
        assertTrue(value.path("periodEnd").isNull());assertTrue(value.path("termEnd").isNull());
        assertEquals(0,value.path("offers").size()); assertFalse(value.path("canCancel").asBoolean());
        for(String action:List.of("checkout","portal","reconcile","cancel"))
            assertEquals(403,request(coach,"POST","/api/billing/"+action,"{}",csrf(coach),false).statusCode());
        assertEquals(200,request(coach,"GET","/api/workouts",null,null,false).statusCode());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM billing_account",Integer.class));
        verifyNoInteractions(stripe);
        var owner=client();acceptTestAthlete(coach,owner,"coach-access-"+UUID.randomUUID()+"@example.test");
        assertFalse(status(owner).path("membershipAccess").asBoolean());
        assertTrue(status(owner).path("offers").size()>0);
        assertEquals(200,request(owner,"POST","/api/billing/checkout","{}",csrf(owner),false).statusCode());
    }
    @Test void checkoutWebhookRenewalCancellationAndOwnership() throws Exception {
        var coach=client();var owner=client();var other=client();
        login(coach,"coach@example.test","Coach-test-password-123");
        String ownerId=acceptTestAthlete(coach,owner,"billing-lifecycle@example.test");
        acceptTestAthlete(coach,other,"billing-other@example.test");
        assertEquals(200,request(owner,"POST","/api/billing/reconcile","{}",csrf(owner),false).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM billing_account WHERE user_id=?",Integer.class,UUID.fromString(ownerId)));
        var guest=client();
        assertEquals(401,request(guest,"POST","/api/billing/checkout","{}",csrf(guest),false).statusCode());
        assertEquals(401,request(client(),"GET","/api/billing",null,null,false).statusCode());
        assertEquals(403,request(owner,"POST","/api/billing/checkout","{}",null,false).statusCode());
        assertEquals(400,request(owner,"POST","/api/billing/checkout","{\"priceId\":\"price_EVIL\"}",csrf(owner),false).statusCode());
        assertEquals(200,request(owner,"POST","/api/billing/checkout","{}",csrf(owner),false).statusCode());
        assertFalse(status(owner).path("membershipAccess").asBoolean());
        assertEquals(200,request(owner,"GET","/app/index.html?billing=success",null,null,false).statusCode());
        assertFalse(status(owner).path("membershipAccess").asBoolean());
        String customer=customer(ownerId);
        fake.state(customer,"active",false,"paid",Instant.now().plus(Duration.ofDays(30)));
        assertFalse(status(owner).path("membershipAccess").asBoolean()); // No webhook yet.
        deliver("checkout.session.completed",customer);
        assertTrue(status(owner).path("membershipAccess").asBoolean());
        assertFalse(status(other).path("membershipAccess").asBoolean());
        assertTrue(status(coach).path("membershipAccess").asBoolean()); // Coaching is free and independent of the athlete's payment.
        assertEquals("coach",status(coach).path("status").asText());
        assertEquals(400,request(other,"POST","/api/billing/portal","{\"userId\":\""+ownerId+"\"}",csrf(other),false).statusCode());
        assertEquals(409,request(other,"POST","/api/billing/portal","{}",csrf(other),false).statusCode());
        assertEquals(200,request(owner,"POST","/api/billing/portal","{}",csrf(owner),false).statusCode());
        assertEquals(customer,fake.portalParameters.get("customer"));
        assertEquals(409,request(owner,"POST","/api/billing/checkout","{}",csrf(owner),false).statusCode());
        assertEquals(403,request(owner,"PUT","/api/billing","{\"status\":\"active\"}",csrf(owner),false).statusCode());
        fake.state(customer,"past_due",false,"open",Instant.now().plus(Duration.ofDays(30)));
        deliver("invoice.payment_failed",customer);assertFalse(status(owner).path("membershipAccess").asBoolean());
        fake.state(customer,"active",false,"paid",Instant.now().plus(Duration.ofDays(60)));
        deliver("invoice.paid",customer);assertTrue(status(owner).path("membershipAccess").asBoolean());
        fake.state(customer,"active",true,"paid",Instant.now().plus(Duration.ofDays(60)));
        deliver("customer.subscription.updated",customer);assertTrue(status(owner).path("membershipAccess").asBoolean());
        assertTrue(status(owner).path("cancelAtPeriodEnd").asBoolean());
        fake.state(customer,"canceled",false,"paid",Instant.now().plus(Duration.ofDays(60)));
        deliver("customer.subscription.deleted",customer);assertFalse(status(owner).path("membershipAccess").asBoolean());
        assertEquals(204,request(owner,"POST","/api/accounts/logout","",csrf(owner),false).statusCode());
        assertEquals(401,request(owner,"GET","/api/billing",null,null,false).statusCode());
    }
    @Test void repeatedConcurrentCheckoutAndLostResponseReuseDurableAttempt() throws Exception {
        var coach=client();var owner=client();login(coach,"coach@example.test","Coach-test-password-123");
        acceptTestAthlete(coach,owner,"billing-retry@example.test");
        fake.loseResponse=true;
        assertEquals(502,request(owner,"POST","/api/billing/checkout","{}",csrf(owner),false).statusCode());
        String token=csrf(owner);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=pool.submit(()->request(owner,"POST","/api/billing/checkout","{}",token,false));
            var second=pool.submit(()->request(owner,"POST","/api/billing/checkout","{}",token,false));
            assertEquals(200,first.get().statusCode());assertEquals(200,second.get().statusCode());
            assertEquals(first.get().body(),second.get().body());
        }
        assertEquals(1,fake.customers.get());assertEquals(1,fake.sessions.get());
        assertFalse(status(owner).path("membershipAccess").asBoolean());
    }
    @Test void declinedAuthenticationPendingExpiredAndReconciliation() throws Exception {
        var coach=client();var owner=client();login(coach,"coach@example.test","Coach-test-password-123");
        String ownerId=acceptTestAthlete(coach,owner,"billing-incomplete@example.test");
        request(owner,"POST","/api/billing/checkout","{}",csrf(owner),false);
        String customer=customer(ownerId);
        for(String state:List.of("incomplete","incomplete_expired","unpaid","past_due","trialing","paused")) {
            fake.state(customer,state,false,"open",Instant.now().plus(Duration.ofDays(30)));
            deliver("invoice.payment_action_required",customer); assertFalse(status(owner).path("membershipAccess").asBoolean(),state);
        }
        fake.state(customer,"active",false,"paid",Instant.now().minusSeconds(1));
        deliver("customer.subscription.updated",customer);assertFalse(status(owner).path("membershipAccess").asBoolean());
        fake.state(customer,"active",false,"paid",Instant.now().plus(Duration.ofDays(30)));
        assertEquals(200,request(owner,"POST","/api/billing/reconcile","{}",csrf(owner),false).statusCode());
        assertTrue(status(owner).path("membershipAccess").asBoolean());
    }
    @Test void signedDuplicatesOutOfOrderAndRetryAreAtomic() throws Exception {
        var coach=client();var owner=client();login(coach,"coach@example.test","Coach-test-password-123");
        String ownerId=acceptTestAthlete(coach,owner,"billing-event@example.test");
        request(owner,"POST","/api/billing/checkout","{}",csrf(owner),false);
        String customer=customer(ownerId);
        fake.state(customer,"active",false,"paid",Instant.now().plus(Duration.ofDays(30)));
        String body=event("invoice.paid",customer),header=signature(body,Instant.now().getEpochSecond());
        assertEquals(400,request(client(),"POST","/api/billing/webhook",body,null,false).statusCode());
        String formatted="\n "+body+" \n";
        assertEquals(204,webhook(formatted,signature(formatted,Instant.now().getEpochSecond())).statusCode());
        // A different event is used below so the failed-delivery check is not already deduplicated.
        body=event("invoice.paid",customer);header=signature(body,Instant.now().getEpochSecond());
        assertEquals(400,webhook(body,signature(body,Instant.now().minusSeconds(600).getEpochSecond())).statusCode());
        assertEquals(400,webhook(body+" ",header).statusCode());
        String live=body.replace("\"livemode\":false","\"livemode\":true");
        assertEquals(400,webhook(live,signature(live,Instant.now().getEpochSecond())).statusCode());
        fake.failSync=true;assertEquals(502,webhook(body,header).statusCode());
        String eventId=json.readTree(body).path("id").asText();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM billing_event WHERE event_id=?",Integer.class,eventId));
        fake.failSync=false;assertEquals(204,webhook(body,header).statusCode());
        int syncs=fake.syncs.get();assertEquals(204,webhook(body,header).statusCode());assertEquals(syncs,fake.syncs.get());
        // Old failure payload arrives after success; canonical state is still active.
        deliver("invoice.payment_failed",customer);assertTrue(status(owner).path("membershipAccess").asBoolean());
        fake.state(customer,"canceled",false,"paid",Instant.now().plus(Duration.ofDays(30)));
        deliver("checkout.session.completed",customer);assertFalse(status(owner).path("membershipAccess").asBoolean());
    }
    @Test void refusesLiveCredentialsAndNonLocalReturnUrl() {
        assertThrows(IllegalStateException.class,()->new BillingSettings(true,"sk_live_REJECT","whsec_FAKE","price_CAPtest","bpc_CAPtest","http://127.0.0.1:8081"));
        assertThrows(IllegalStateException.class,()->new BillingSettings(true,"sk_test_FAKE","whsec_FAKE","price_CAPtest","bpc_CAPtest","https://example.test"));
    }
}
