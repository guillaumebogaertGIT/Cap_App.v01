package be.cap.backend;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrainingApiTests {
    @TempDir static Path directory;
    @DynamicPropertySource static void paths(DynamicPropertyRegistry registry) {
        registry.add("cap.workouts.file", () -> directory.resolve("workouts.json").toString());
        registry.add("cap.training.file", () -> directory.resolve("training.json").toString());
    }
    @Value("${local.server.port}") int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json").header("Origin", "http://127.0.0.1:5500")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void completeWorkoutThroughHttpThenUseItsRepRecord() throws Exception {
        var created = post("/api/workouts", """
            {"name":"API test", "description":"", "athleteId":"preview-guillaume",
             "exercises":[{"name":"Squat","sets":3,"reps":5,"loadMode":"fixed","loadValue":60}]}
            """);
        assertEquals(201, created.statusCode());
        assertEquals("http://127.0.0.1:5500", created.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        String id = mapper.readTree(created.body()).get("id").asText();
        String body = """
            {"id":"%s","workoutId":"%s","startedAt":"%s","finishedAt":"%s",
             "exercises":[{"exerciseIndex":0,"sets":[{"reps":5,"kg":60}]}]}
            """.formatted(UUID.randomUUID(), id, Instant.now().minusSeconds(60), Instant.now());
        var finished = post("/api/training", body);
        assertEquals(200, finished.statusCode());
        assertEquals(1, mapper.readTree(finished.body()).get("newRecords").size());
        assertEquals(finished.body(), post("/api/training", body).body());
        var target = post("/api/workouts", """
            {"name":"Next", "athleteId":"preview-guillaume",
             "exercises":[{"name":"Squat","sets":3,"reps":5,"loadMode":"percent","loadValue":90}]}
            """);
        assertEquals(201, target.statusCode());
        assertEquals(54.0, mapper.readTree(target.body()).get("exercises").get(0).get("targetKg").asDouble());
        assertEquals(400, post("/api/workouts", """
            {"name":"No matching PR", "athleteId":"preview-guillaume",
             "exercises":[{"name":"Squat","sets":3,"reps":8,"loadMode":"percent","loadValue":90}]}
            """).statusCode());
        var history = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/training")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(1, mapper.readTree(history.body()).size());
    }
}
