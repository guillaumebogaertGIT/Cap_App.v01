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
    "cap.accounts.bootstrap-email=coach@example.test", "cap.accounts.bootstrap-password=Coach-test-password-123"
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
        assertEquals("[]", request(coach, "GET", "/api/workouts", null, null, false).body());
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
}
