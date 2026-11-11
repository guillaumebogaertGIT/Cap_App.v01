package be.cap.backend;

import be.cap.backend.accounts.*;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("accounts")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"cap.billing.enabled=true","cap.billing.secret=sk_test_FAKE","cap.billing.webhook-secret=whsec_FAKE","cap.billing.portal=bpc_FAKE","cap.billing.price=price_Test","cap.accounts.enforce-membership=true","cap.accounts.bootstrap-owner=true","cap.accounts.bootstrap-email=coach@example.test","cap.accounts.bootstrap-password=Coach-test-password-123"})
class AthleteMembershipAccessTests {
    @TempDir static Path directory;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){PostgresTestDatabase.configure(r,"membership_gate");r.add("cap.exercises.file",()->directory.resolve("exercises.json").toString());r.add("cap.workouts.file",()->directory.resolve("workouts.json").toString());r.add("cap.training.file",()->directory.resolve("training.json").toString());}
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;@Autowired AccountService accounts;@Autowired AthleteFeatureAccess access;@Autowired PlanningService planning;
    @org.springframework.test.context.bean.override.mockito.MockitoBean be.cap.backend.accounts.billing.StripeGateway stripe;
    @AfterEach void noProviderRequests(){org.mockito.Mockito.verifyNoInteractions(stripe);}
    AccountApiTests web;AccountService.Account athlete,coach;HttpClient client;
    LocalDate today(){return LocalDate.now(ZoneId.of("Europe/Brussels"));}
    @BeforeEach void setup() throws Exception {web=new AccountApiTests();web.port=port;web.jdbc=jdbc;coach=accounts.account("coach@example.test");athlete=accounts.accept(accounts.invite(UUID.randomUUID()+"@example.test",coach.email()).token(),"Limited test client","Athlete-test-password-123");client=web.client();web.login(client,athlete.email(),"Athlete-test-password-123");}
    int get(String path) throws Exception{return web.request(client,"GET",path,null,null,false).statusCode();}
    void gift(LocalDate start,LocalDate end){jdbc.update("INSERT INTO complimentary_membership(id,athlete_id,offer_code,starts_on,ends_on,reason,granted_by) VALUES(?,?,'full_1m',?,?,'Test',?)",UUID.randomUUID(),athlete.id(),start,end,coach.id());}
    @Test void noMembershipCanOnlyReadPlanningAndPurchase() throws Exception {
        assertFalse(access.fullAccess(athlete));assertTrue(access.fullAccess(coach));
        for(String p:List.of("/api/club/sessions","/api/club/bookings","/api/billing","/api/accounts/me","/api/accounts/access"))assertEquals(200,get(p),p);
        for(String p:List.of("/api/records","/api/training","/api/workouts","/api/exercises","/api/nutrition-targets/self","/api/food-diary","/api/meal-plans/self","/api/ai-coach","/api/ai-coach/settings","/api/social/notifications","/api/measurements","/api/food-products/personal"))assertEquals(403,get(p),p);
        var token=web.csrf(client);assertEquals(403,web.request(client,"POST","/api/club/sessions/"+UUID.randomUUID()+"/booking","{}",token,false).statusCode());
        assertEquals(200,get("/api/nutrition-preferences/self"));assertEquals(200,web.request(client,"PUT","/api/nutrition-preferences/self","{\"allergies\":\"\",\"avoidedFoods\":\"\",\"aiShare\":false,\"version\":0}",token,false).statusCode());
        assertEquals(403,web.request(client,"PUT","/api/accounts/profile","{}",token,false).statusCode());
        assertTrue(access.allowed(athlete,"POST","/api/billing/checkout"));
        var html=web.request(client,"GET","/app/index.html",null,null,false);assertEquals(200,html.statusCode());assertTrue(html.body().contains("\"fullAccess\":false"));assertTrue(html.body().contains("\"draft\":null"));
        assertEquals(204,web.request(client,"POST","/api/accounts/logout",null,token,false).statusCode());
    }
    @Test void currentGiftUnlocksAndRevocationRelocksWithoutNewLogin() throws Exception {
        gift(today(),today().plusDays(5));assertTrue(access.fullAccess(athlete));assertEquals(200,get("/api/records"));
        jdbc.update("UPDATE complimentary_membership SET revoked=TRUE WHERE athlete_id=?",athlete.id());assertFalse(access.fullAccess(athlete));assertEquals(403,get("/api/records"));
    }
    @Test void paidMembershipUnlocksOnlyWhilePaidAndCurrent() throws Exception {
        jdbc.update("INSERT INTO billing_account(user_id,customer_key,customer_started_at) VALUES(?,?,CURRENT_TIMESTAMP)",athlete.id(),UUID.randomUUID());
        jdbc.update("INSERT INTO billing_subscription(subscription_id,user_id,customer_id,price_id,status,payment_status,period_start,period_end,cancel_at_period_end,synced_at) VALUES(?,?,?,?,'active','paid',CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '5 days',FALSE,CURRENT_TIMESTAMP)","sub_"+UUID.randomUUID(),athlete.id(),"cus_"+UUID.randomUUID(),"price_Test");
        assertTrue(access.fullAccess(athlete));assertEquals(200,get("/api/records"));
        jdbc.update("UPDATE billing_subscription SET period_start=CURRENT_TIMESTAMP+INTERVAL '1 day' WHERE user_id=?",athlete.id());assertFalse(access.fullAccess(athlete));
        jdbc.update("UPDATE billing_subscription SET period_start=CURRENT_TIMESTAMP-INTERVAL '1 day',payment_status='unpaid' WHERE user_id=?",athlete.id());assertFalse(access.fullAccess(athlete));assertEquals(403,get("/api/records"));
    }
    @Test void futureAndExpiredMembershipsDoNotUnlock() {gift(today().plusDays(1),today().plusDays(5));gift(today().minusDays(5),today().minusDays(1));assertFalse(access.fullAccess(athlete));}
    @Test void creditsUnlockAndSpentCreditOnlyCoversUpcomingActiveBooking() throws Exception {
        UUID credit=UUID.randomUUID(),order=UUID.randomUUID();jdbc.update("INSERT INTO billing_account(user_id,customer_key,customer_started_at) VALUES(?,?,CURRENT_TIMESTAMP)",athlete.id(),UUID.randomUUID());jdbc.update("INSERT INTO billing_order(id,user_id,offer_code,price_id,status,created_at,paid_at) VALUES(?,?,'credits_1','price_Test','paid',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",order,athlete.id());jdbc.update("INSERT INTO lesson_credit(id,user_id,order_id,credit_number) VALUES(?,?,?,1)",credit,athlete.id(),order);assertTrue(access.fullAccess(athlete));
        var s=planning.save(null,new PlanningService.Input("Credit session",today().plusDays(1).toString(),"10:00","11:00",coach.id(),"CAP",10,"Groep","","",List.of(),1),coach).getFirst();
        jdbc.update("INSERT INTO session_booking(session_id,athlete_id) VALUES(?,?)",s.id(),athlete.id());jdbc.update("UPDATE lesson_credit SET session_id=? WHERE id=?",s.id(),credit);assertTrue(access.fullAccess(athlete));
        jdbc.update("UPDATE session_booking SET cancelled=TRUE WHERE session_id=?",s.id());assertFalse(access.fullAccess(athlete));
    }
    @Test void planningReadDoesNotLeakWorkoutSnapshot() throws Exception {
        var s=planning.save(null,new PlanningService.Input("Read only session",today().plusDays(2).toString(),"10:00","11:00",coach.id(),"CAP",10,"Groep","","",List.of(),1),coach).getFirst();
        jdbc.update("UPDATE club_session SET workout_snapshot=? WHERE id=?","{\"name\":\"SECRET_WORKOUT_CONTENT\",\"exercises\":[]}",s.id());
        var response=web.request(client,"GET","/api/club/sessions",null,null,false);assertEquals(200,response.statusCode());assertFalse(response.body().contains("SECRET_WORKOUT_CONTENT"));
        var html=web.request(client,"GET","/app/index.html",null,null,false);assertFalse(html.body().contains("SECRET_WORKOUT_CONTENT"));
    }
}
