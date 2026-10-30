package be.cap.backend;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("accounts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:accounts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "cap.accounts.bootstrap-owner=true", "cap.accounts.bootstrap-email=coach@example.test", "cap.accounts.bootstrap-password=Coach-test-password-123"
})
class AccountApiTests {
    @TempDir static Path directory;
    @DynamicPropertySource static void paths(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry,"accounts");
        registry.add("cap.exercises.file", () -> directory.resolve("exercises.json").toString());
        registry.add("cap.workouts.file", () -> directory.resolve("workouts.json").toString());
        registry.add("cap.training.file", () -> directory.resolve("training.json").toString());
    }
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    final JsonMapper json = JsonMapper.builder().build();

    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    HttpResponse<String> request(HttpClient client, String method, String path, String body, String csrf, boolean form) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        builder.header("Content-Type", form ? "application/x-www-form-urlencoded" : "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String csrf(HttpClient client) throws Exception {
        var response = request(client, "GET", "/api/accounts/csrf", null, null, false);
        assertEquals(200, response.statusCode());
        return json.readTree(response.body()).get("token").asText();
    }
    void login(HttpClient client, String email, String password) throws Exception {
        String body = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        assertEquals(204, request(client, "POST", "/api/accounts/login", body, csrf(client), true).statusCode());
    }

    @Test void inviteAcceptLoginAndEnforceOwnershipBoundary() throws Exception {
        var coach = client(); var athlete = client();
        var guestApp = request(coach, "GET", "/app/index.html", null, null, false);
        assertEquals(302, guestApp.statusCode());
        assertEquals("/accounts/index.html", guestApp.headers().firstValue("location").orElseThrow());
        var page = request(coach, "GET", "/accounts/index.html", null, null, false);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Mijn account aanmaken"));
        assertEquals(200, request(coach, "GET", "/accounts/accounts.js", null, null, false).statusCode());
        assertEquals(200, request(coach, "GET", "/accounts/shared/style.css", null, null, false).statusCode());
        assertEquals(401, request(coach, "GET", "/api/accounts/me", null, null, false).statusCode());
        assertEquals(401, request(coach, "GET", "/api/club/sessions", null, null, false).statusCode());
        assertEquals(403, request(coach, "POST", "/api/accounts/login", "email=coach@example.test&password=wrong", null, true).statusCode());
        assertEquals(401, request(coach, "POST", "/api/accounts/login", "email=coach@example.test&password=wrong", csrf(coach), true).statusCode());
        login(coach, "COACH@example.test", "Coach-test-password-123");
        var originalApp = request(coach, "GET", "/app/index.html", null, null, false);
        assertEquals(200, originalApp.statusCode());
        assertTrue(originalApp.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertTrue(originalApp.body().contains("id=\"sidebar-navigation\""));
        assertTrue(originalApp.body().contains("js/real-auth.js"));
        assertFalse(originalApp.body().contains("js/auth-preview.js"));
        assertTrue(originalApp.body().contains("id=\"cap-account-data\""));
        assertFalse(originalApp.body().contains("Coach-test-password-123"));
        assertEquals(200, request(coach, "GET", "/app/css/style.css", null, null, false).statusCode());
        var me = request(coach, "GET", "/api/accounts/me", null, null, false);
        assertEquals("COACH", json.readTree(me.body()).get("role").asText());
        assertFalse(me.body().contains("password"));
        assertTrue(json.readTree(request(coach, "GET", "/api/workouts", null, null, false).body()).isArray());
        var invitation = request(coach, "POST", "/api/accounts/invitations", "{\"email\":\" Athlete@example.test \"}", csrf(coach), false);
        assertEquals(201, invitation.statusCode());
        String token = json.readTree(invitation.body()).get("token").asText();
        assertNotEquals(token, jdbc.queryForObject("SELECT token_hash FROM athlete_invitation WHERE email = 'athlete@example.test'", String.class));
        String acceptance = json.writeValueAsString(java.util.Map.of("token", token, "name", "Test Athlete", "password", "Athlete-test-password-123", "role", "COACH"));
        var accepted = request(athlete, "POST", "/api/accounts/accept", acceptance, csrf(athlete), false);
        assertEquals(201, accepted.statusCode());
        assertEquals("ATHLETE", json.readTree(accepted.body()).get("role").asText());
        assertEquals(400, request(athlete, "POST", "/api/accounts/accept", acceptance, csrf(athlete), false).statusCode());
        String stored = jdbc.queryForObject("SELECT password_hash FROM cap_user WHERE email = 'athlete@example.test'", String.class);
        assertTrue(stored.startsWith("$2"));
        assertNotEquals("Athlete-test-password-123", stored);
        login(athlete, "athlete@example.test", "Athlete-test-password-123");
        assertEquals("ATHLETE", json.readTree(request(athlete, "GET", "/api/accounts/me", null, null, false).body()).get("role").asText());
        jdbc.update("UPDATE cap_user SET display_name=? WHERE email=?", "</script><script>alert(1)</script>", "athlete@example.test");
        try {
            var athleteApp = request(athlete, "GET", "/app/index.html", null, null, false);
            assertEquals(200, athleteApp.statusCode());
            assertFalse(athleteApp.body().contains("</script><script>alert(1)"));
            assertTrue(athleteApp.body().contains("\\u003c/script>"));
        } finally { jdbc.update("UPDATE cap_user SET display_name=? WHERE email=?", "Test Athlete", "athlete@example.test"); }
        assertEquals(403, request(athlete, "POST", "/api/workouts", "{}", csrf(athlete), false).statusCode());
        assertEquals(403, request(athlete, "POST", "/api/exercises", "{}", csrf(athlete), false).statusCode());
        assertEquals(403, request(athlete, "GET", "/api/club/people", null, null, false).statusCode());
        assertEquals(200, request(athlete, "GET", "/api/club/sessions", null, null, false).statusCode());
        assertEquals(403, request(athlete, "POST", "/api/accounts/invitations", "{\"email\":\"other@example.test\"}", csrf(athlete), false).statusCode());
        assertEquals("[]", request(athlete, "GET", "/api/training", null, null, false).body());
        assertEquals(204, request(athlete, "POST", "/api/accounts/logout", "", csrf(athlete), false).statusCode());
        assertEquals(401, request(athlete, "GET", "/api/accounts/me", null, null, false).statusCode());
    }

    @Test void expiredAndReplacedInvitationsCannotCreateAccounts() throws Exception {
        var coach = client(); var guest = client();
        login(coach, "coach@example.test", "Coach-test-password-123");
        String body = "{\"email\":\"expired@example.test\"}";
        var first = request(coach, "POST", "/api/accounts/invitations", body, csrf(coach), false);
        String old = json.readTree(first.body()).get("token").asText();
        var second = request(coach, "POST", "/api/accounts/invitations", body, csrf(coach), false);
        String current = json.readTree(second.body()).get("token").asText();
        String acceptance = "{\"token\":\"%s\",\"name\":\"Test\",\"password\":\"Athlete-test-password-123\"}";
        assertEquals(400, request(guest, "POST", "/api/accounts/accept", acceptance.formatted(old), csrf(guest), false).statusCode());
        jdbc.update("UPDATE athlete_invitation SET expires_at = ? WHERE email = ?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), "expired@example.test");
        assertEquals(400, request(guest, "POST", "/api/accounts/accept", acceptance.formatted(current), csrf(guest), false).statusCode());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE email = 'expired@example.test'", Long.class));
    }

    @Test void ownProfileAndPhotoPersistAndValidate() throws Exception {
        var coach = client(); var guest = client();
        assertEquals(401, request(guest, "GET", "/api/accounts/profile", null, null, false).statusCode());
        login(coach, "coach@example.test", "Coach-test-password-123");
        var saved = request(coach, "PUT", "/api/accounts/profile", "{\"name\":\"Updated coach\",\"heightCm\":180,\"weightKg\":75,\"sex\":\"male\"}", csrf(coach), false);
        assertEquals(200, saved.statusCode());
        assertEquals(180, json.readTree(request(coach, "GET", "/api/accounts/profile", null, null, false).body()).get("values").get("heightCm").asInt());
        assertEquals(400, request(coach, "PUT", "/api/accounts/profile", "{\"name\":\"Coach\",\"heightCm\":999}", csrf(coach), false).statusCode());
        assertEquals(400, request(coach, "PUT", "/api/accounts/profile/photo", "{\"image\":\"not an image\"}", csrf(coach), false).statusCode());
        var image = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "jpg", bytes);
        String payload = json.writeValueAsString(java.util.Map.of("image", "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())));
        assertEquals(204, request(coach, "PUT", "/api/accounts/profile/photo", payload, csrf(coach), false).statusCode());
        assertEquals(200, request(coach, "GET", "/api/accounts/profile/photo", null, null, false).statusCode());
        var invite = request(coach, "POST", "/api/accounts/invitations", "{\"email\":\"profile-owner@example.test\"}", csrf(coach), false);
        String token = json.readTree(invite.body()).get("token").asText();
        String acceptance = json.writeValueAsString(java.util.Map.of("token", token, "name", "Other athlete", "password", "Profile-athlete-password-25"));
        assertEquals(201, request(guest, "POST", "/api/accounts/accept", acceptance, csrf(guest), false).statusCode());
        login(guest, "profile-owner@example.test", "Profile-athlete-password-25");
        assertFalse(json.readTree(request(guest, "GET", "/api/accounts/profile", null, null, false).body()).get("hasPhoto").asBoolean());
        assertEquals(404, request(guest, "GET", "/api/accounts/profile/photo", null, null, false).statusCode());
        assertEquals(204, request(coach, "DELETE", "/api/accounts/profile/photo", null, csrf(coach), false).statusCode());
        assertEquals(404, request(coach, "GET", "/api/accounts/profile/photo", null, null, false).statusCode());
    }

    @Autowired org.springframework.context.ApplicationContext context;
    String acceptTestAthlete(HttpClient coach, HttpClient athlete, String email) throws Exception {
        var invite = request(coach,"POST","/api/accounts/invitations",json.writeValueAsString(java.util.Map.of("email",email)),csrf(coach),false);
        assertEquals(201,invite.statusCode());
        var token=json.readTree(invite.body()).get("token").asText();
        var accepted=request(athlete,"POST","/api/accounts/accept",json.writeValueAsString(java.util.Map.of("token",token,"name","API test athlete","password","Boundary-test-password-25")),csrf(athlete),false);
        assertEquals(201,accepted.statusCode());
        login(athlete,email,"Boundary-test-password-25");
        return json.readTree(accepted.body()).get("id").asText();
    }
    @Test void directApiOwnershipGuestLogoutAndDemoBoundaries() throws Exception {
        var coach=client();var first=client();var other=client();var guest=client();
        for(String path:java.util.List.of("/api/accounts/me","/api/accounts/profile","/api/workouts","/api/workout-assignments","/api/training","/api/records","/api/club/bookings","/api/exercises")) {
            var denied=request(guest,"GET",path,null,null,false);
            assertEquals(401,denied.statusCode(),path);
            assertTrue(denied.body().contains("message"));
        }
        login(coach,"coach@example.test","Coach-test-password-123");
        String owner=acceptTestAthlete(coach,first,"boundary-first@example.test");
        String peer=acceptTestAthlete(coach,other,"boundary-other@example.test");
        var workout=request(coach,"POST","/api/workouts","{\"name\":\"API ownership workout\",\"description\":\"\",\"athleteId\":\"\",\"assignedDate\":\"\",\"exercises\":[{\"name\":\"Squat\",\"sets\":1,\"reps\":5,\"loadMode\":\"none\"}]}",csrf(coach),false);
        assertEquals(201,workout.statusCode());
        String id=json.readTree(workout.body()).get("id").asText();
        String day=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).plusDays(1).toString();
        var assigned=request(coach,"POST","/api/workouts/"+id+"/assignment",json.writeValueAsString(java.util.Map.of("athleteId",owner,"date",day)),csrf(coach),false);
        assertEquals(200,assigned.statusCode());
        var assignments=json.readTree(request(first,"GET","/api/workout-assignments",null,null,false).body());
        String assignment=assignments.get(0).get("assignmentId").asText();
        assertEquals("[]",request(other,"GET","/api/workout-assignments",null,null,false).body());
        assertEquals("[]",request(other,"GET","/api/workouts",null,null,false).body());
        assertEquals(404,request(other,"GET","/api/training?athleteId="+owner,null,null,false).statusCode());
        assertEquals(404,request(first,"GET","/api/records?athleteId="+peer,null,null,false).statusCode());
        assertEquals(404,request(other,"PUT","/api/workout-assignments/"+assignment+"/schedule",json.writeValueAsString(java.util.Map.of("date",day)),csrf(other),false).statusCode());
        assertEquals(404,request(other,"POST","/api/training/active",json.writeValueAsString(java.util.Map.of("assignmentId",assignment)),csrf(other),false).statusCode());
        assertEquals(403,request(other,"POST","/api/accounts/invitations","{\"email\":\"forbidden@example.test\"}",csrf(other),false).statusCode());
        assertEquals(403,request(other,"PUT","/api/workout-assignments/"+assignment+"/note","{\"note\":\"forbidden\"}",csrf(other),false).statusCode());
        // Identical URL names are backed by account controllers, never the old demo stores.
        assertTrue(context.getBeansOfType(WorkoutController.class).isEmpty());
        assertTrue(context.getBeansOfType(TrainingController.class).isEmpty());
        assertEquals(404,request(first,"GET","/api/training?athleteId=preview-guillaume",null,null,false).statusCode());
        assertEquals(404,request(coach,"POST","/api/workouts/"+id+"/assignment",json.writeValueAsString(java.util.Map.of("athleteId","preview-guillaume","date",day)),csrf(coach),false).statusCode());
        var malformed=request(coach,"PUT","/api/accounts/profile","{broken-json-password-example",csrf(coach),false);
        assertEquals(400,malformed.statusCode());assertFalse(malformed.body().contains("password-example"));
        assertEquals(204,request(first,"POST","/api/accounts/logout","",csrf(first),false).statusCode());
        for(String path:java.util.List.of("/api/accounts/me","/api/training","/api/workouts","/api/accounts/profile"))
            assertEquals(401,request(first,"GET",path,null,null,false).statusCode(),path);
    }

    @Test void foodDiaryIsSharedReadOnlyWithCoachAndOwnedByAthlete() throws Exception {
        var coach=client(); var owner=client(); var other=client(); var guest=client();
        String day=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).toString();
        assertEquals(401,request(guest,"GET","/api/food-diary?date="+day,null,null,false).statusCode());
        login(coach,"coach@example.test","Coach-test-password-123");
        String ownerId=acceptTestAthlete(coach,owner,"diary-owner@example.test");
        acceptTestAthlete(coach,other,"diary-other@example.test");
        String id=java.util.UUID.randomUUID().toString();
        var input=new java.util.HashMap<String,Object>(java.util.Map.of("id",id,"date",day,"meal","breakfast","name","Test oats","amount",80,"unit","g","calories",300,"proteinG",12,"carbsG",45,"fatG",7));
        String body=json.writeValueAsString(input);
        assertEquals(403,request(coach,"POST","/api/food-diary",body,csrf(coach),false).statusCode());
        assertEquals(403,request(owner,"POST","/api/food-diary",body,null,false).statusCode());
        assertEquals(201,request(owner,"POST","/api/food-diary",body,csrf(owner),false).statusCode());
        assertEquals(200,request(owner,"POST","/api/food-diary",body,csrf(owner),false).statusCode());
        assertEquals(409,request(other,"POST","/api/food-diary",body,csrf(other),false).statusCode());
        var own=request(owner,"GET","/api/food-diary?date="+day,null,null,false);
        assertEquals(200,own.statusCode()); assertTrue(own.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertEquals(1,json.readTree(own.body()).size());
        assertEquals(own.body(),request(coach,"GET","/api/food-diary?date="+day+"&athleteId="+ownerId,null,null,false).body());
        assertEquals("[]",request(other,"GET","/api/food-diary?date="+day,null,null,false).body());
        assertEquals(404,request(other,"GET","/api/food-diary?date="+day+"&athleteId="+ownerId,null,null,false).statusCode());
        assertEquals(400,request(owner,"GET","/api/food-diary?date=invalid",null,null,false).statusCode());
        assertEquals(400,request(coach,"GET","/api/food-diary?date="+day,null,null,false).statusCode());
        assertEquals(403,request(coach,"DELETE","/api/food-diary/"+id,null,csrf(coach),false).statusCode());
        assertEquals(404,request(other,"DELETE","/api/food-diary/"+id,null,csrf(other),false).statusCode());
        input.put("id",java.util.UUID.randomUUID()); input.put("calories",-1);
        assertEquals(400,request(owner,"POST","/api/food-diary",json.writeValueAsString(input),csrf(owner),false).statusCode());
        input.put("calories",300); input.put("date",java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).plusDays(1).toString());
        assertEquals(400,request(owner,"POST","/api/food-diary",json.writeValueAsString(input),csrf(owner),false).statusCode());
        assertEquals(204,request(owner,"DELETE","/api/food-diary/"+id,null,csrf(owner),false).statusCode());
        assertEquals("[]",request(coach,"GET","/api/food-diary?date="+day+"&athleteId="+ownerId,null,null,false).body());
        // Undo is an explicit new save of the same immutable snapshot.
        assertEquals(201,request(owner,"POST","/api/food-diary",body,csrf(owner),false).statusCode());
        assertEquals(204,request(owner,"POST","/api/accounts/logout","",csrf(owner),false).statusCode());
        assertEquals(401,request(owner,"GET","/api/food-diary?date="+day,null,null,false).statusCode());
    }

    @Test void nutritionTargetsAreSharedWithOnlyTheAssignedAthlete() throws Exception {
        var coach=client();var owner=client();var other=client();var guest=client();
        assertEquals(401,request(guest,"GET","/api/nutrition-targets",null,null,false).statusCode());
        login(coach,"coach@example.test","Coach-test-password-123");
        String ownerId=acceptTestAthlete(coach,owner,"nutrition-owner@example.test");
        String otherId=acceptTestAthlete(coach,other,"nutrition-other@example.test");
        assertEquals(204,request(owner,"GET","/api/nutrition-targets",null,null,false).statusCode());
        String body="{\"targets\":{\"calories\":2300,\"proteinG\":150,\"carbsG\":250,\"fatG\":70,\"waterMl\":2500},\"origin\":\"handmatig\"}";
        assertEquals(403,request(coach,"PUT","/api/nutrition-targets/"+ownerId,body,null,false).statusCode());
        assertEquals(403,request(owner,"PUT","/api/nutrition-targets/"+ownerId,body,csrf(owner),false).statusCode());
        var saved=request(coach,"PUT","/api/nutrition-targets/"+ownerId,body,csrf(coach),false);
        assertEquals(200,saved.statusCode());
        assertEquals(ownerId,json.readTree(saved.body()).get("athleteId").asText());
        assertEquals(2300,json.readTree(request(owner,"GET","/api/nutrition-targets",null,null,false).body()).get("targets").get("calories").asInt());
        assertEquals(2300,json.readTree(request(coach,"GET","/api/nutrition-targets?athleteId="+ownerId,null,null,false).body()).get("targets").get("calories").asInt());
        assertEquals(404,request(other,"GET","/api/nutrition-targets?athleteId="+ownerId,null,null,false).statusCode());
        assertEquals(204,request(other,"GET","/api/nutrition-targets",null,null,false).statusCode());
        assertEquals(404,request(owner,"GET","/api/nutrition-targets?athleteId="+otherId,null,null,false).statusCode());
        assertEquals(400,request(coach,"PUT","/api/nutrition-targets/"+ownerId,body.replace("2300","-1"),csrf(coach),false).statusCode());
        assertEquals(400,request(coach,"PUT","/api/nutrition-targets/"+ownerId,"{\"targets\":{\"calories\":2300},\"origin\":\"handmatig\"}",csrf(coach),false).statusCode());
        assertEquals(200,request(coach,"PUT","/api/nutrition-targets/"+ownerId,body.replace("2300","2400"),csrf(coach),false).statusCode());
        assertEquals(2400,json.readTree(request(owner,"GET","/api/nutrition-targets",null,null,false).body()).get("targets").get("calories").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM nutrition_target WHERE athlete_id=?",Integer.class,java.util.UUID.fromString(ownerId)));
    }
}
