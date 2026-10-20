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
             "exercises":[{"name":"Squat","sets":3,"reps":5,"loadMode":"fixed","loadValue":60,"restSeconds":90,"tempo":"3-0-1-0"}]}
            """);
        assertEquals(201, created.statusCode());
        assertEquals("http://127.0.0.1:5500", created.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        String id = mapper.readTree(created.body()).get("id").asText();
        assertEquals(90, mapper.readTree(created.body()).get("exercises").get(0).get("restSeconds").asInt());
        assertEquals("3-0-1-0", mapper.readTree(created.body()).get("exercises").get(0).get("tempo").asText());
        String body = """
            {"id":"%s","workoutId":"%s","startedAt":"%s","finishedAt":"%s",
             "exercises":[{"exerciseIndex":0,"sets":[{"reps":5,"kg":60}]}]}
            """.formatted(UUID.randomUUID(), id, Instant.now().minusSeconds(60), Instant.now());
        var finished = post("/api/training", body);
        assertEquals(200, finished.statusCode());
        assertEquals(90, mapper.readTree(finished.body()).get("planned").get("exercises").get(0).get("restSeconds").asInt());
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
        String scheduledDate = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).plusDays(1).toString();
        assertEquals(200, post("/api/workouts/" + id + "/assignment", """
            {"athleteId":"preview-guillaume","date":"%s"}
            """.formatted(scheduledDate)).statusCode());
        var edited = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/workouts/" + id))
            .header("Content-Type", "application/json").header("Origin", "http://127.0.0.1:5500")
            .PUT(HttpRequest.BodyPublishers.ofString("""
                {"name":"Edited API workout","exercises":[{"name":"Row","sets":4,"reps":8,"restSeconds":120,"tempo":"2-0-1-0"}]}
                """)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, edited.statusCode());
        assertEquals(id, mapper.readTree(edited.body()).get("id").asText());
        assertEquals("Edited API workout", mapper.readTree(edited.body()).get("name").asText());
        assertEquals(120, mapper.readTree(edited.body()).get("exercises").get(0).get("restSeconds").asInt());
        var assignments = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/workout-assignments")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, assignments.statusCode());
        assertTrue(mapper.readTree(assignments.body()).valueStream().anyMatch(a ->
            scheduledDate.equals(a.get("planned").get("assignedDate").asText())
                && "Squat".equals(a.get("planned").get("exercises").get(0).get("name").asText())));
        String assignmentId = mapper.readTree(assignments.body()).valueStream()
            .filter(a -> scheduledDate.equals(a.get("planned").get("assignedDate").asText()))
            .findFirst().orElseThrow().get("assignmentId").asText();
        var scheduledFinish = post("/api/training", """
            {"id":"%s","workoutId":"%s","assignmentId":"%s","startedAt":"%s","finishedAt":"%s",
             "exercises":[{"exerciseIndex":0,"sets":[{"reps":5,"kg":60,"rir":2}]}]}
            """.formatted(UUID.randomUUID(), id, assignmentId, Instant.now().minusSeconds(60), Instant.now()));
        assertEquals(200, scheduledFinish.statusCode());
        assertEquals("Squat", mapper.readTree(scheduledFinish.body()).get("planned").get("exercises").get(0).get("name").asText());
        history = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/training")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var reassigned = post("/api/workouts/" + id + "/assignment", """
            {"athleteId":"preview-guillaume"}
            """);
        assertEquals(200, reassigned.statusCode());
        assertEquals(id, mapper.readTree(reassigned.body()).get("id").asText());
        assertEquals("preview-guillaume", mapper.readTree(reassigned.body()).get("athleteId").asText());
        assertEquals(400, post("/api/workouts/" + id + "/assignment", """
            {"athleteId":"unknown"}
            """).statusCode());
        var deleted = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/workouts/" + id))
            .header("Origin", "http://127.0.0.1:5500").DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleted.statusCode());
        assertEquals("http://127.0.0.1:5500", deleted.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        var remaining = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/workouts")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertFalse(mapper.readTree(remaining.body()).valueStream().anyMatch(w -> id.equals(w.get("id").asText())));
        var preserved = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/training")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(history.body(), preserved.body());
        assertEquals(finished.body(), post("/api/training", body).body());
        var records = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/records")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(60.0, mapper.readTree(records.body()).get(0).get("kg").asDouble());
    }
}
