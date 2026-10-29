package be.cap.backend;

import be.cap.backend.accounts.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real context shutdown/restart, same disk database/schema, no data deletion. */
@EnabledIfEnvironmentVariable(named="CAP_TEST_POSTGRES_URL", matches="jdbc:postgresql:.*")
class PostgresRestartTests {
    final JsonMapper json = JsonMapper.builder().build();
    ConfigurableApplicationContext start(Map<String,Object> settings) {
        return new SpringApplicationBuilder(BackendApplication.class).profiles("accounts")
            .initializers(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("restart-test-settings", settings)))
            .run();
    }
    HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    void login(HttpClient client, int port) throws Exception {
        String csrf=json.readTree(get(client,port,"/api/accounts/csrf").body()).get("token").asText();
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/accounts/login"))
            .header("X-CSRF-TOKEN",csrf).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("email=restart-coach%40example.test&password=Restart-test-password-25"))
            .build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(204,response.statusCode());
    }
    @Test void accountsAssignmentsAndWorkoutsSurviveBackendRestartButSessionsDoNot() throws Exception {
        String url=System.getenv("CAP_TEST_POSTGRES_URL");
        String schema="cap_test_restart_"+UUID.randomUUID().toString().replace("-", "");
        Map<String,Object> settings=new HashMap<>();
        settings.put("spring.datasource.url",url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        settings.put("spring.datasource.username",System.getenv().getOrDefault("CAP_TEST_POSTGRES_USER","cap_test"));
        settings.put("spring.datasource.password",System.getenv().getOrDefault("CAP_TEST_POSTGRES_PASSWORD",""));
        settings.put("spring.flyway.schemas",schema); settings.put("spring.flyway.default-schema",schema);
        settings.put("cap.accounts.bootstrap-email","restart-coach@example.test");
        settings.put("cap.accounts.bootstrap-password","Restart-test-password-25");
        settings.put("server.port",0);
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        UUID athleteId; String workoutId;
        UUID foodId=UUID.randomUUID();
        var foodDay=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels"));
        try(var first=start(settings)) {
            var accounts=first.getBean(AccountService.class); var workouts=first.getBean(AccountWorkoutService.class);
            var coach=accounts.account("restart-coach@example.test");
            var athlete=accounts.accept(accounts.invite("restart-athlete@example.test",coach.email()).token(),"Restart athlete","Athlete-test-password-25");
            athleteId=athlete.id();
            var billingJdbc=first.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            billingJdbc.update("INSERT INTO billing_account(user_id,customer_id,customer_key,customer_started_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",athleteId,"cus_RestartFixture",UUID.randomUUID());
            UUID creditOrder=UUID.randomUUID();
            billingJdbc.update("INSERT INTO billing_order(id,user_id,offer_code,price_id,status,created_at,paid_at) VALUES(?,?,'credits_1','price_RestartCredit','paid',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",creditOrder,athleteId);
            billingJdbc.update("INSERT INTO lesson_credit(id,user_id,order_id,credit_number) VALUES(?,?,?,1)",UUID.randomUUID(),athleteId,creditOrder);
            billingJdbc.update("""
                INSERT INTO billing_subscription(subscription_id,user_id,customer_id,price_id,status,payment_status,
                period_start,period_end,cancel_at_period_end,synced_at) VALUES(?,?,?,?,'active','paid',CURRENT_TIMESTAMP,?,true,CURRENT_TIMESTAMP)
                ""","sub_RestartFixture",athleteId,"cus_RestartFixture","price_CAPtest",java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(86400)));
            first.getBean(FoodDiaryController.class).add(()->athlete.email(),
                new FoodDiaryController.Entry(foodId,foodDay,"breakfast","Restart oats",null,80.0,"g",300.0,12.0,45.0,7.0,null));
            var workout=workouts.save(null,new Workout(null,"Persistent restart workout","",List.of(new Workout.Exercise("Squat",1,5)),"",""),null,coach);
            workoutId=workout.id();
            first.getBean(NutritionController.class).save(()->coach.email(),athleteId,
                new NutritionController.Input(new NutritionController.Targets(2300.0,150.0,250.0,70.0,2500.0),"handmatig"));
            workouts.assign(workoutId,athleteId.toString(),java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).plusDays(1).toString(),null,coach);
            int port=first.getEnvironment().getProperty("local.server.port",Integer.class);
            login(client,port);
            assertEquals(200,get(client,port,"/api/accounts/me").statusCode());
        }
        try(var second=start(settings)) {
            var accounts=second.getBean(AccountService.class); var workouts=second.getBean(AccountWorkoutService.class);
            var coach=accounts.account("restart-coach@example.test");
            var athlete=accounts.account("restart-athlete@example.test");
            assertEquals(athleteId,athlete.id());
            var billing=second.getBean(be.cap.backend.accounts.billing.BillingService.class).summary(athlete);
            assertEquals("active",billing.status());assertTrue(billing.cancelAtPeriodEnd());assertTrue(billing.hasCustomer());
            assertNotNull(billing.periodEnd());
            assertEquals(1,billing.credits());
            var diary=second.getBean(FoodDiaryController.class).get(()->coach.email(),foodDay,athleteId).getBody();
            assertNotNull(diary);assertEquals(foodId,diary.getFirst().id());
            assertEquals("Restart oats",diary.getFirst().name());
            var nutrition=second.getBean(NutritionController.class).get(()->athlete.email(),null).getBody();
            assertNotNull(nutrition);assertEquals(2300.0,nutrition.targets().calories());
            assertTrue(workouts.list(coach).stream().anyMatch(workout->workout.id().equals(workoutId)));
            assertEquals(workoutId,workouts.assignments(athlete).getFirst().planned().id());
            int port=second.getEnvironment().getProperty("local.server.port",Integer.class);
            assertEquals(401,get(client,port,"/api/accounts/me").statusCode());
            login(client,port);
            assertEquals(200,get(client,port,"/api/accounts/me").statusCode());
            assertTrue(get(client,port,"/api/workouts").body().contains("Persistent restart workout"));
        }
    }
}
