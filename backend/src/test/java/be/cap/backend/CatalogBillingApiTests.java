package be.cap.backend;

import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"cap.billing.enforce-bookings=true",
    "cap.accounts.membership-reviewers=coach@example.test,second-reviewer@example.test",
    "cap.billing.prices={\"full_1m\":\"price_One\",\"full_3m\":\"price_Three\",\"full_12m\":\"price_Year\",\"monthly_flexible\":\"price_Flex\",\"monthly_fixed\":\"price_Fixed\",\"credits_1\":\"price_C1\",\"credits_5\":\"price_C5\",\"credits_10\":\"price_C10\",\"credits_20\":\"price_C20\"}"})
class CatalogBillingApiTests extends BillingApiTests {
    CatalogFake catalogFake;
    @BeforeEach @Override void configureStripe() {
        fake=catalogFake=new CatalogFake();
        when(stripe.get(anyString())).thenAnswer(call->fake.get(call.getArgument(0)));
        when(stripe.post(anyString(),anyMap(),nullable(String.class))).thenAnswer(call->fake.post(call.getArgument(0),call.getArgument(1),call.getArgument(2)));
    }
    class CatalogFake extends FakeStripe {
        Map<String,Integer> amounts=Map.of("price_One",9000,"price_Three",22500,"price_Year",75000,"price_Flex",7500,"price_Fixed",6500,"price_C1",1800,"price_C5",8000,"price_C10",14000,"price_C20",24000);
        Map<String,ObjectNode> checkouts=new ConcurrentHashMap<>();
        Map<String,ObjectNode> attempts=new ConcurrentHashMap<>();
        boolean readOnlyPortal, wrongPrice;
        int termUpdates;
        @Override JsonNode get(String path) {
            if(path.startsWith("/v1/prices/") && !path.endsWith("price_CAPtest")) {
                String price=path.substring(path.lastIndexOf('/')+1);
                var value=new HashMap<String,Object>(); value.put("livemode",false);value.put("active",true);value.put("currency","eur");
                value.put("unit_amount",wrongPrice?1:amounts.get(price));value.put("billing_scheme","per_unit");
                if(price.equals("price_Flex")||price.equals("price_Fixed")) value.put("recurring",Map.of("interval","month","interval_count",1));
                return tree(value);
            }
            if(path.startsWith("/v1/checkout/sessions/")) {
                var session=checkouts.get(path.substring(path.lastIndexOf('/')+1).split("\\?")[0]);
                if(session!=null) return session.deepCopy();
            }
            if(path.startsWith("/v1/billing_portal/configurations/") && readOnlyPortal)
                return tree(Map.of("livemode",false,"active",true,"features",Map.of("subscription_cancel",Map.of("enabled",false),"subscription_update",Map.of("enabled",false))));
            return super.get(path);
        }
        @Override JsonNode post(String path,Map<String,String> parameters,String key) {
            if(path.startsWith("/v1/subscriptions/")) {
                termUpdates++;
                assertEquals("none",parameters.get("proration_behavior"));
                for(var list:subscriptions.values()) for(var value:list) if(value.path("id").asText().equals(path.substring(path.lastIndexOf('/')+1))) {
                    ((ObjectNode)value).put("cancel_at",Long.parseLong(parameters.get("cancel_at")));return value.deepCopy();
                }
                throw new AssertionError("Missing fake subscription");
            }
            if(path.equals("/v1/checkout/sessions") && !"legacy_test".equals(parameters.get("metadata[cap_offer]"))) {
                var value=attempts.computeIfAbsent(key,k->{
                    String session="cs_test_Catalog"+run+sessions.incrementAndGet(),price=parameters.get("line_items[0][price]");
                    var data=new HashMap<String,Object>();
                    data.put("id",session);data.put("livemode",false);data.put("status","open");data.put("payment_status","unpaid");
                    data.put("customer",parameters.get("customer"));data.put("client_reference_id",parameters.get("client_reference_id"));
                    data.put("mode",parameters.get("mode"));data.put("currency","eur");data.put("amount_total",amounts.get(price));
                    data.put("metadata",Map.of("cap_order_id",parameters.get("metadata[cap_order_id]"),"cap_offer",parameters.get("metadata[cap_offer]")));
                    data.put("line_items",Map.of("data",List.of(Map.of("price",Map.of("id",price),"quantity",1)),"has_more",false));
                    data.put("url","https://checkout.stripe.com/c/pay/FAKE");
                    var node=(ObjectNode)tree(data);checkouts.put(session,node);return node;
                });
                if(loseResponse) {loseResponse=false;throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,"Fake lost response");}
                return value.deepCopy();
            }
            return super.post(path,parameters,key);
        }
        ObjectNode finish(String owner,Instant paid) {
            var value=checkouts.values().stream().filter(v->v.path("client_reference_id").asText().equals(owner)).findFirst().orElseThrow();
            value.put("status","complete");value.put("payment_status","paid");
            if(value.path("mode").asText().equals("payment")) {
                value.set("payment_intent",tree(Map.of("livemode",false,"status","succeeded","latest_charge",Map.of("livemode",false,"paid",true,"refunded",false,"amount_refunded",0,"created",paid.getEpochSecond()))));
            } else {
                String customer=value.path("customer").asText(),subId="sub_Catalog"+customer.substring(4);
                value.put("subscription",subId);
                var sub=new HashMap<String,Object>();sub.put("livemode",false);sub.put("id",subId);sub.put("customer",customer);sub.put("status","active");sub.put("billing_cycle_anchor",paid.getEpochSecond());
                sub.put("latest_invoice",Map.of("status","paid"));sub.put("metadata",Map.of("cap_account_id",owner,"cap_order_id",value.path("metadata").path("cap_order_id").asText()));
                sub.put("items",Map.of("data",List.of(Map.of("quantity",1,"price",value.path("line_items").path("data").path(0).path("price"),"current_period_start",paid.getEpochSecond(),"current_period_end",paid.atZone(ZoneOffset.UTC).plusMonths(1).toEpochSecond()))));
                subscriptions.put(customer,new ArrayList<>(List.of(tree(sub))));
            }
            return value;
        }
    }
    HttpClient coach() throws Exception { var client=client();login(client,"coach@example.test","Coach-test-password-123");return client; }
    String athlete(HttpClient owner) throws Exception { return acceptTestAthlete(coach(),owner,"catalog-"+UUID.randomUUID()+"@example.test"); }
    int buy(HttpClient owner,String offer) throws Exception { return request(owner,"POST","/api/billing/checkout",json.writeValueAsString(Map.of("offer",offer)),csrf(owner),false).statusCode(); }
    String session(HttpClient coach) throws Exception {
        return session(coach,"Groep");
    }
    String sessionBody(HttpClient coach,String type) throws Exception {
        String coachId=json.readTree(request(coach,"GET","/api/accounts/me",null,null,false).body()).path("id").asText();
        return json.writeValueAsString(Map.of("title","Test credit lesson","date",LocalDate.now(ZoneId.of("Europe/Brussels")).plusDays(1).toString(),"start","12:00","end","13:00","coachId",coachId,"location","Test","capacity",5,"type",type,"category",type.equals("Groep")?"CAP HYROX":"","repeat",1));
    }
    String session(HttpClient coach,String type) throws Exception {
        var result=request(coach,"POST","/api/club/sessions",sessionBody(coach,type),csrf(coach),false);assertEquals(201,result.statusCode(),result.body());
        return json.readTree(result.body()).path(0).path("id").asText();
    }
    int book(HttpClient user,String lesson,String method) throws Exception { return request(user,method,"/api/club/sessions/"+lesson+"/booking",null,csrf(user),false).statusCode(); }
    @Test void catalogPricesAreServerControlledAndUnavailablePaymentsGrantNothing() throws Exception {
        var owner=client();athlete(owner);
        assertEquals(9,status(owner).path("offers").size());
        assertEquals(400,buy(owner,"untrusted"));
        assertEquals(400,request(owner,"POST","/api/billing/checkout","{\"offer\":\"credits_20\",\"price\":1}",csrf(owner),false).statusCode());
        catalogFake.wrongPrice=true;assertEquals(409,buy(owner,"credits_1"));catalogFake.wrongPrice=false;
        assertEquals(200,buy(owner,"credits_1"));assertEquals(0,status(owner).path("credits").asInt());
        assertEquals(409,buy(owner,"credits_5"));assertEquals(200,buy(owner,"credits_1"));assertEquals(1,fake.sessions.get());
        assertEquals(200,request(owner,"GET","/app/index.html?billing=success",null,null,false).statusCode());assertEquals(0,status(owner).path("credits").asInt());
    }
    @Test void creditPurchaseDuplicateEventsOwnershipAndBookingReturn() throws Exception {
        var owner=client();String ownerId=athlete(owner);var other=client();athlete(other);var coach=coach();
        assertEquals(200,buy(owner,"credits_5"));catalogFake.finish(ownerId,Instant.now().minusSeconds(60));
        String event=event("checkout.session.completed",customer(ownerId));String signature=signature(event,Instant.now().getEpochSecond());
        assertEquals(204,webhook(event,signature).statusCode());assertEquals(204,webhook(event,signature).statusCode());deliver("invoice.paid",customer(ownerId));
        assertEquals(5,status(owner).path("credits").asInt());assertEquals(0,status(other).path("credits").asInt());assertFalse(status(owner).path("membershipAccess").asBoolean());
        String lesson=session(coach);assertEquals(409,book(other,lesson,"POST"));assertEquals(204,book(owner,lesson,"POST"));assertEquals(204,book(owner,lesson,"POST"));assertEquals(4,status(owner).path("credits").asInt());
        assertEquals(204,book(owner,lesson,"DELETE"));assertEquals(204,book(owner,lesson,"DELETE"));assertEquals(5,status(owner).path("credits").asInt());
        assertEquals(204,book(owner,lesson,"POST"));assertEquals(204,request(coach,"DELETE","/api/club/sessions/"+lesson,null,csrf(coach),false).statusCode());assertEquals(5,status(owner).path("credits").asInt());
    }
    @Test void lastCreditCannotBeSpentTwiceAndPastLessonCannotBeRefunded() throws Exception {
        var owner=client();String ownerId=athlete(owner);var coach=coach();assertEquals(200,buy(owner,"credits_1"));catalogFake.finish(ownerId,Instant.now());deliver("checkout.session.completed",customer(ownerId));
        String one=session(coach),two=session(coach,"Open gym"),token=csrf(owner);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var a=pool.submit(()->request(owner,"POST","/api/club/sessions/"+one+"/booking",null,token,false).statusCode());
            var b=pool.submit(()->request(owner,"POST","/api/club/sessions/"+two+"/booking",null,token,false).statusCode());
            assertEquals(Set.of(204,409),new HashSet<>(List.of(a.get(),b.get())));
        }
        assertEquals(0,status(owner).path("credits").asInt());
        UUID booked=jdbc.queryForObject("SELECT session_id FROM lesson_credit WHERE user_id=?",UUID.class,UUID.fromString(ownerId));
        jdbc.update("UPDATE club_session SET session_date=? WHERE id=?",java.sql.Date.valueOf(LocalDate.now().minusDays(1)),booked);
        assertEquals(400,book(owner,booked.toString(),"DELETE"));assertEquals(0,status(owner).path("credits").asInt());
    }
    @Test void prepaidCalendarDurationExpiresAndCoversBookingWithoutCredit() throws Exception {
        for(var entry:Map.of("full_1m",1,"full_3m",3,"full_12m",12).entrySet()) {
            var owner=client();String ownerId=athlete(owner);assertEquals(200,buy(owner,entry.getKey()));
            Instant paid=Instant.now().minusSeconds(60);catalogFake.finish(ownerId,paid);deliver("checkout.session.completed",customer(ownerId));
            assertTrue(status(owner).path("membershipAccess").asBoolean());
            assertEquals(paid.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).atZone(ZoneId.of("Europe/Brussels")).plusMonths(entry.getValue()).toInstant(),Instant.parse(status(owner).path("periodEnd").asText()));
            assertEquals(204,book(owner,session(coach()),"POST"));assertEquals(0,status(owner).path("credits").asInt());
            assertEquals(204,book(owner,session(coach(),"Open gym"),"POST"));assertEquals(0,status(owner).path("credits").asInt());
            assertEquals(409,buy(owner,"full_1m"));
            jdbc.update("UPDATE billing_order SET period_end=? WHERE user_id=?",Timestamp.from(Instant.now().minusSeconds(1)),UUID.fromString(ownerId));
            assertFalse(status(owner).path("membershipAccess").asBoolean());
        }
    }
    @Test void monthlyPlansStopAfterTwelveMonthsAndOnlyFlexibleCanCancel() throws Exception {
        for(String code:List.of("monthly_flexible","monthly_fixed")) {
            var owner=client();String ownerId=athlete(owner);assertEquals(200,buy(owner,code));
            Instant paid=Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            catalogFake.finish(ownerId,paid);deliver("checkout.session.completed",customer(ownerId));
            var state=status(owner);assertTrue(state.path("membershipAccess").asBoolean());
            assertEquals(paid.atZone(ZoneOffset.UTC).plusMonths(12).toInstant(),Instant.parse(state.path("termEnd").asText()));
            assertEquals(409,request(owner,"POST","/api/billing/portal","{}",csrf(owner),false).statusCode());
            catalogFake.readOnlyPortal=true;assertEquals(200,request(owner,"POST","/api/billing/portal","{}",csrf(owner),false).statusCode());catalogFake.readOnlyPortal=false;
            var canceled=request(owner,"POST","/api/billing/cancel","{}",csrf(owner),false);
            assertEquals(code.equals("monthly_flexible")?200:409,canceled.statusCode(),canceled.body());
            if(code.equals("monthly_flexible")) {
                assertTrue(status(owner).path("membershipAccess").asBoolean());assertTrue(status(owner).path("cancelAtPeriodEnd").asBoolean());assertFalse(status(owner).path("canCancel").asBoolean());
                assertEquals(200,request(owner,"POST","/api/billing/cancel","{}",csrf(owner),false).statusCode());
            }
        }
    }
    @Test void lostCheckoutResponseRecoversOnceAndTamperedCanonicalAmountRollsBack() throws Exception {
        var owner=client();String ownerId=athlete(owner);catalogFake.loseResponse=true;
        assertEquals(502,buy(owner,"credits_20"));assertEquals(200,buy(owner,"credits_20"));assertEquals(1,fake.sessions.get());
        var session=catalogFake.finish(ownerId,Instant.now());session.put("amount_total",1);
        String body=event("checkout.session.completed",customer(ownerId)),signature=signature(body,Instant.now().getEpochSecond());
        assertEquals(409,webhook(body,signature).statusCode());assertEquals(0,status(owner).path("credits").asInt());
        session.put("amount_total",24000);assertEquals(204,webhook(body,signature).statusCode());assertEquals(20,status(owner).path("credits").asInt());
    }
    @Test void signedCheckoutRecoversLostResponseWithoutStartingAnotherPurchase() throws Exception {
        var owner=client();String ownerId=athlete(owner);catalogFake.loseResponse=true;
        assertEquals(502,buy(owner,"credits_10"));
        var session=catalogFake.finish(ownerId,Instant.now());
        String body=json.writeValueAsString(Map.of("id","evt_"+UUID.randomUUID().toString().replace("-",""),"livemode",false,"type","checkout.session.completed",
            "data",Map.of("object",Map.of("id",session.path("id").asText(),"customer",customer(ownerId)))));
        assertEquals(204,webhook(body,signature(body,Instant.now().getEpochSecond())).statusCode());
        assertEquals(10,status(owner).path("credits").asInt());assertEquals(1,fake.sessions.get());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM billing_order WHERE user_id=? AND status='paid'",Integer.class,UUID.fromString(ownerId)));
    }
    @Test void unpaidOrExpiredCheckoutAndCancelRequestsCannotGrantAccess() throws Exception {
        var owner=client();String ownerId=athlete(owner);assertEquals(200,buy(owner,"credits_1"));
        deliver("checkout.session.completed",customer(ownerId));assertEquals(0,status(owner).path("credits").asInt());
        var session=catalogFake.checkouts.values().iterator().next();session.put("status","expired");
        deliver("checkout.session.expired",customer(ownerId));assertEquals(0,status(owner).path("credits").asInt());
        assertEquals(403,request(owner,"POST","/api/billing/cancel","{}",null,false).statusCode());
        assertEquals(400,request(owner,"POST","/api/billing/cancel","{\"userId\":\"other\"}",csrf(owner),false).statusCode());
        var guest=client();assertEquals(401,request(guest,"POST","/api/billing/cancel","{}",csrf(guest),false).statusCode());
    }
    @Test void openGymSpendsOneCreditAndKeepsItAcrossGroupTypeChanges() throws Exception {
        var owner=client();String ownerId=athlete(owner);var other=client();athlete(other);var coach=coach();
        assertEquals(200,buy(owner,"credits_5"));catalogFake.finish(ownerId,Instant.now());deliver("checkout.session.completed",customer(ownerId));
        String gym=session(coach,"Open gym");
        assertEquals(409,book(other,gym,"POST"));
        assertEquals(204,book(owner,gym,"POST"));assertEquals(204,book(owner,gym,"POST"));assertEquals(4,status(owner).path("credits").asInt());
        assertEquals(204,book(owner,gym,"DELETE"));assertEquals(204,book(owner,gym,"DELETE"));assertEquals(5,status(owner).path("credits").asInt());
        assertEquals(204,book(owner,gym,"POST"));
        assertEquals(200,request(coach,"PUT","/api/club/sessions/"+gym,sessionBody(coach,"Open gym"),csrf(coach),false).statusCode());assertEquals(4,status(owner).path("credits").asInt());
        assertEquals(200,request(coach,"PUT","/api/club/sessions/"+gym,sessionBody(coach,"Groep"),csrf(coach),false).statusCode());assertEquals(4,status(owner).path("credits").asInt());
        assertEquals(200,request(coach,"PUT","/api/club/sessions/"+gym,sessionBody(coach,"Open gym"),csrf(coach),false).statusCode());assertEquals(4,status(owner).path("credits").asInt());
        assertEquals(204,request(coach,"DELETE","/api/club/sessions/"+gym+"/bookings/"+ownerId,null,csrf(coach),false).statusCode());assertEquals(5,status(owner).path("credits").asInt());
        assertEquals(204,book(owner,gym,"POST"));
        var privateInput=(ObjectNode)json.readTree(sessionBody(coach,"Privé"));privateInput.set("athleteIds",json.readTree("[\""+ownerId+"\"]"));
        assertEquals(200,request(coach,"PUT","/api/club/sessions/"+gym,json.writeValueAsString(privateInput),csrf(coach),false).statusCode());assertEquals(5,status(owner).path("credits").asInt());
    }
    @Test void coachCanReadSelectedAthleteBillingButCannotExposeItToAthletesOrGuests() throws Exception {
        var coach=coach();var owner=client();String ownerId=athlete(owner);var other=client();String otherId=athlete(other);
        assertEquals(200,buy(owner,"credits_5"));catalogFake.finish(ownerId,Instant.now());deliver("checkout.session.completed",customer(ownerId));
        fake.state(customer(ownerId),"active",true,"paid",Instant.now().plusSeconds(86400));deliver("invoice.paid",customer(ownerId));
        int rows=jdbc.queryForObject("SELECT COUNT(*) FROM billing_account",Integer.class);
        clearInvocations(stripe);
        String path="/api/billing/athletes/"+ownerId;
        var response=request(coach,"GET",path,null,null,false);
        assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var value=json.readTree(response.body());
        assertEquals(ownerId,value.path("athleteId").asText());assertEquals(5,value.path("credits").asInt());
        assertTrue(value.path("membershipAccess").asBoolean());assertTrue(value.path("cancellationPlanned").asBoolean());assertFalse(value.path("paidUntil").isNull());
        assertFalse(value.has("offers"));assertFalse(value.has("hasCustomer"));assertFalse(value.has("canCancel"));
        assertFalse(response.body().contains(customer(ownerId)));assertFalse(response.body().contains("price_CAPtest"));assertFalse(response.body().contains("stripe.com"));
        var empty=request(coach,"GET","/api/billing/athletes/"+otherId,null,null,false);assertEquals(200,empty.statusCode());
        assertEquals(0,json.readTree(empty.body()).path("credits").asInt());assertFalse(json.readTree(empty.body()).path("membershipAccess").asBoolean());
        verifyNoInteractions(stripe);assertEquals(rows,jdbc.queryForObject("SELECT COUNT(*) FROM billing_account",Integer.class));
        assertEquals(401,request(client(),"GET",path,null,null,false).statusCode());
        assertEquals(403,request(owner,"GET",path,null,null,false).statusCode());assertEquals(403,request(other,"GET",path,null,null,false).statusCode());
        assertEquals(403,request(other,"GET","/api/billing/athletes/"+otherId,null,null,false).statusCode());
        assertEquals(404,request(coach,"GET","/api/billing/athletes/"+UUID.randomUUID(),null,null,false).statusCode());
        String coachId=json.readTree(request(coach,"GET","/api/accounts/me",null,null,false).body()).path("id").asText();
        assertEquals(404,request(coach,"GET","/api/billing/athletes/"+coachId,null,null,false).statusCode());
        assertEquals(400,request(coach,"GET","/api/billing/athletes/not-a-uuid",null,null,false).statusCode());
        assertEquals(405,request(coach,"PUT",path,"{\"credits\":100}",csrf(coach),false).statusCode());
        assertEquals(405,request(coach,"POST",path,"{}",csrf(coach),false).statusCode());
        assertEquals(204,request(coach,"POST","/api/accounts/logout","",csrf(coach),false).statusCode());assertEquals(401,request(coach,"GET",path,null,null,false).statusCode());
    }
    @Test void membershipReviewIsLimitedToTheTwoDesignatedCoachLogins() throws Exception {
        var allowed=coach();var owner=client();String ownerId=athlete(owner);
        for (String email:List.of("second-reviewer@example.test","ordinary-coach@example.test")) {
            jdbc.update("""
                INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at)
                SELECT ?,?,'Glenn Vlamynck',password_hash,'COACH',CURRENT_TIMESTAMP FROM cap_user WHERE email='coach@example.test'
                """,UUID.randomUUID(),email);
        }
        var second=client();login(second,"second-reviewer@example.test","Coach-test-password-123");
        var ordinary=client();login(ordinary,"ordinary-coach@example.test","Coach-test-password-123");
        String path="/api/billing/athletes/"+ownerId;
        assertEquals(200,request(allowed,"GET",path,null,null,false).statusCode());
        assertEquals(200,request(second,"GET",path,null,null,false).statusCode());
        assertEquals(403,request(ordinary,"GET",path,null,null,false).statusCode());
        assertEquals(403,request(ordinary,"GET","/api/billing/athletes/"+UUID.randomUUID(),null,null,false).statusCode());
        assertEquals(200,request(ordinary,"GET","/api/club/people",null,null,false).statusCode());
        assertEquals(200,request(ordinary,"GET","/api/workouts",null,null,false).statusCode());
        var allowedPage=request(second,"GET","/app/index.html",null,null,false).body();
        var deniedPage=request(ordinary,"GET","/app/index.html",null,null,false).body();
        assertTrue(allowedPage.contains("\"reviewMemberships\":true"));
        assertTrue(deniedPage.contains("\"reviewMemberships\":false"));
    }
}
