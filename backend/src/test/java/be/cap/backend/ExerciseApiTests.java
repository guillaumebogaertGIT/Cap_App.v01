package be.cap.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExerciseApiTests {
    @TempDir static Path directory;
    @DynamicPropertySource static void paths(DynamicPropertyRegistry registry) {
        registry.add("cap.exercises.file", () -> directory.resolve("exercises.json").toString());
        registry.add("cap.workouts.file", () -> directory.resolve("workouts.json").toString());
        registry.add("cap.training.file", () -> directory.resolve("training.json").toString());
    }
    @Value("${local.server.port}") int port;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json").header("Origin", "http://127.0.0.1:5500")
            .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void manageCatalogThroughHttpWithoutChangingSavedWorkoutNames() throws Exception {
        var listed = request("GET", "/api/exercises", null);
        assertEquals(200, listed.statusCode());
        assertEquals(1078, mapper.readTree(listed.body()).size());
        String body = """
            {"name":"CAP API Squat","region":"Lower Body","category":"Squat","instructions":"Brace"}
            """;
        var created = request("POST", "/api/exercises", body);
        assertEquals(201, created.statusCode());
        assertEquals("http://127.0.0.1:5500", created.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        String id = mapper.readTree(created.body()).get("id").asText();
        var workout = request("POST", "/api/workouts", """
            {"name":"Existing workout","exercises":[{"name":"CAP API Squat","sets":3,"reps":5}]}
            """);
        assertEquals(201, workout.statusCode());
        assertEquals(200, request("PUT", "/api/exercises/" + id, body.replace("CAP API Squat", "CAP API Renamed")).statusCode());
        var archived = request("PATCH", "/api/exercises/" + id + "/archive", "{\"archived\":true}");
        assertTrue(mapper.readTree(archived.body()).get("archived").asBoolean());
        var workouts = mapper.readTree(request("GET", "/api/workouts", null).body());
        assertEquals("CAP API Squat", workouts.get(0).get("exercises").get(0).get("name").asText());
        var restored = request("PATCH", "/api/exercises/" + id + "/archive", "{\"archived\":false}");
        assertFalse(mapper.readTree(restored.body()).get("archived").asBoolean());
        assertEquals(400, request("PATCH", "/api/exercises/" + id + "/archive", "{}").statusCode());
        assertEquals(404, request("PUT", "/api/exercises/missing", body).statusCode());
        assertEquals(400, request("POST", "/api/exercises", body.replace("Lower Body", "invalid")).statusCode());
    }
}
