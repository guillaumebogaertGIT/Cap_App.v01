package be.cap.backend;

import be.cap.backend.accounts.FinanceAccess;
import be.cap.backend.accounts.AccountService.Account;
import be.cap.backend.accounts.billing.StripeGateway;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties={"cap.accounts.finance-reviewer=coach@example.test",
    "cap.billing.enabled=true","cap.billing.secret=sk_test_FAKE","cap.billing.webhook-secret=whsec_FAKE",
    "cap.billing.portal=bpc_FAKE"})
class FinanceApiTests extends AccountApiTests {
    @MockitoBean StripeGateway stripe;
    @Test void managerAndFinanceCanReadReportsButCannotExecuteStaffPaymentWrites() throws Exception {
        when(stripe.get(anyString())).thenReturn(json.readTree("{\"data\":[],\"has_more\":false}"));
        for(String role:List.of("MANAGER","FINANCE")) {
            String email=UUID.randomUUID()+"@example.test";
            jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,staff_role,created_at) SELECT ?,?,'Financial reader',password_hash,'COACH',?,CURRENT_TIMESTAMP FROM cap_user WHERE email='coach@example.test'",UUID.randomUUID(),email,role);
            var user=client();login(user,email,"Coach-test-password-123");
            assertEquals(200,request(user,"GET","/api/finance?month=2026-10",null,null,false).statusCode());
            var people=request(user,"GET","/api/billing/athletes",null,null,false);assertEquals(200,people.statusCode());
            for(var person:json.readTree(people.body())) {assertEquals(2,person.size());assertTrue(person.has("id"));assertTrue(person.has("name"));}
            for(String action:List.of("checkout","portal","cancel","reconcile"))assertEquals(403,request(user,"POST","/api/billing/"+action,"{}",csrf(user),false).statusCode());
        }
        verify(stripe,never()).post(anyString(),anyMap(),any());
    }
    private Map<String,Object> charge(String id,String customer,long amount,long refund) {
        var value=new HashMap<String,Object>();
        value.put("id",id); value.put("customer",customer); value.put("amount_captured",amount);
        value.put("amount_refunded",refund); value.put("livemode",false); value.put("paid",true);
        value.put("captured",true); value.put("status","succeeded"); value.put("currency","eur");
        value.put("created",Instant.parse("2026-10-01T00:00:00Z").getEpochSecond());
        return value;
    }
    @Test void accessIsSeparateAndNeverTrustsDisplayNames() throws Exception {
        String path="/api/finance?month=2026-10";
        assertEquals(401,request(client(),"GET",path,null,null,false).statusCode());
        var coach=client(); login(coach,"coach@example.test","Coach-test-password-123");
        var athlete=client(); acceptTestAthlete(coach,athlete,"finance-"+UUID.randomUUID()+"@example.test");
        assertEquals(403,request(athlete,"GET",path,null,null,false).statusCode());
        String email="ordinary-"+UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,staff_role) SELECT ?,?,'Glenn Vlamynck',password_hash,'COACH',?,'INSTRUCTOR' FROM cap_user WHERE email='coach@example.test'",
            UUID.randomUUID(),email,Timestamp.from(Instant.now()));
        var ordinary=client(); login(ordinary,email,"Coach-test-password-123");
        assertEquals(403,request(ordinary,"GET",path,null,null,false).statusCode());
        assertTrue(request(ordinary,"GET","/app/index.html",null,null,false).body().contains("\"reviewFinance\":false"));
        assertTrue(request(coach,"GET","/app/index.html",null,null,false).body().contains("\"reviewFinance\":true"));
        assertEquals(204,request(coach,"POST","/api/accounts/logout","",csrf(coach),false).statusCode());
        assertEquals(401,request(coach,"GET",path,null,null,false).statusCode());
        verifyNoInteractions(stripe);
    }
    @Test void paginatedTotalsUseCapturedMoneyAndExcludeFailedUnrelatedAndUncaptured() throws Exception {
        var coach=client(); login(coach,"coach@example.test","Coach-test-password-123");
        UUID owner=jdbc.queryForObject("SELECT id FROM cap_user WHERE email='coach@example.test'",UUID.class);
        String customer="cus_Finance"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO billing_account(user_id,customer_id,customer_key,customer_started_at) VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET customer_id=excluded.customer_id",
            owner,customer,UUID.randomUUID(),Timestamp.from(Instant.now()));
        var one=charge("ch_First",customer,8000,1000);
        var failed=charge("ch_Failed",customer,9999,0); failed.put("status","failed");
        var uncaptured=charge("ch_Hold",customer,9999,0); uncaptured.put("captured",false);
        var usd=charge("ch_Usd",customer,500,0); usd.put("currency","usd");
        when(stripe.get(anyString())).thenAnswer(call -> {
            String path=call.getArgument(0);
            assertTrue(path.contains("created%5Bgte%5D=1790805600")); // October starts at Brussels UTC+2.
            assertTrue(path.contains("created%5Blt%5D=1793487600")); // November starts at UTC+1.
            return json.valueToTree(path.contains("starting_after=")
                ? Map.of("data",List.of(charge("ch_Renewal",customer,7500,0)),"has_more",false)
                : Map.of("data",List.of(one,failed,uncaptured,usd,charge("ch_External","cus_Other",99999,0)),"has_more",true));
        });
        var response=request(coach,"GET","/api/finance?month=2026-10",null,null,false);
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var report=json.readTree(response.body());
        assertEquals(15500,report.path("receivedCents").asLong()); assertEquals(1000,report.path("refundedCents").asLong());
        assertEquals(14500,report.path("remainingCents").asLong()); assertEquals(2,report.path("payments").size());
        assertEquals(1,report.path("excludedCurrencyCount").asInt());
        assertFalse(response.body().contains(customer)); assertFalse(response.body().contains("ch_First"));
        verify(stripe,times(2)).get(anyString()); verify(stripe,never()).post(anyString(),anyMap(),nullable(String.class));
    }
    @Test void invalidMonthLiveDataAndPartialPagesFailWithoutInventingTotals() throws Exception {
        var coach=client(); login(coach,"coach@example.test","Coach-test-password-123");
        assertEquals(400,request(coach,"GET","/api/finance?month=2026-13",null,null,false).statusCode());
        assertEquals(400,request(coach,"GET","/api/finance?month=bad",null,null,false).statusCode());
        verifyNoInteractions(stripe);
        var live=charge("ch_Live","cus_Unknown",9000,0); live.put("livemode",true);
        when(stripe.get(anyString())).thenReturn(json.valueToTree(Map.of("data",List.of(live),"has_more",false)));
        assertEquals(502,request(coach,"GET","/api/finance?month=2026-10",null,null,false).statusCode());
        when(stripe.get(anyString())).thenReturn(json.valueToTree(Map.of("data",List.of(),"has_more",true)));
        var response=request(coach,"GET","/api/finance?month=2026-10",null,null,false);
        assertEquals(502,response.statusCode()); assertFalse(response.body().contains("remainingCents"));
    }
}
