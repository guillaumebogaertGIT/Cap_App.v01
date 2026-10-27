package be.cap.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Shared local catalog. Only coach additions/overrides are written to disk. */
@Service
@org.springframework.context.annotation.Profile("!accounts")
public class ExerciseStore {
    public record Exercise(String id, String name, String region, String category, String subcategory,
                           String instructions, String notes, boolean archived) {}

    private final Path file;
    private final StateWriter<List<Exercise>> writer;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Map<String, Exercise> imported = new LinkedHashMap<>();
    private Map<String, Exercise> overrides = new LinkedHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public ExerciseStore(@Value("${cap.exercises.file:data/exercises.json}") String filename) throws IOException {
        writer = null;
        file = Path.of(filename).toAbsolutePath();
        try (var source = ExerciseStore.class.getResourceAsStream("/exercise-catalog.json")) {
            if (source == null) throw new IOException("Exercise catalog resource missing.");
            for (Exercise exercise : mapper.readValue(source, Exercise[].class)) imported.put(exercise.id(), exercise);
        }
        if (Files.exists(file)) {
            for (Exercise exercise : mapper.readValue(Files.readString(file), Exercise[].class)) {
                overrides.put(exercise.id(), exercise);
            }
        }
    }
    public ExerciseStore(List<Exercise> seed, List<Exercise> overrides, StateWriter<List<Exercise>> writer) {
        file = null;
        this.writer = writer;
        seed.forEach(exercise -> imported.put(exercise.id(),exercise));
        overrides.forEach(exercise -> this.overrides.put(exercise.id(),exercise));
    }

    public synchronized List<Exercise> list() {
        Map<String, Exercise> combined = new LinkedHashMap<>(imported);
        combined.putAll(overrides);
        return List.copyOf(combined.values());
    }

    public synchronized Exercise create(Exercise input) throws IOException {
        Exercise exercise = validated(UUID.randomUUID().toString(), input, false);
        rejectDuplicate(exercise, null);
        save(exercise);
        return exercise;
    }

    public synchronized Exercise update(String id, Exercise input) throws IOException {
        Exercise existing = find(id);
        Exercise exercise = validated(id, input, existing.archived());
        // Existing imported name collisions remain editable; new collisions are rejected.
        if (!nameKey(existing).equals(nameKey(exercise))) rejectDuplicate(exercise, id);
        save(exercise);
        return exercise;
    }

    public synchronized Exercise archive(String id, boolean archived) throws IOException {
        Exercise existing = find(id);
        if (existing.archived() == archived) return existing;
        Exercise exercise = new Exercise(id, existing.name(), existing.region(), existing.category(),
            existing.subcategory(), existing.instructions(), existing.notes(), archived);
        save(exercise);
        return exercise;
    }

    private Exercise find(String id) {
        Exercise result = overrides.containsKey(id) ? overrides.get(id) : imported.get(id);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Oefening niet gevonden.");
        return result;
    }

    private Exercise validated(String id, Exercise input, boolean archived) {
        if (input == null) throw invalid("Oefening ontbreekt.");
        String name = text(input.name(), 100, "Naam");
        String category = text(input.category(), 100, "Categorie");
        if (name.isEmpty() || category.isEmpty()) throw invalid("Vul een naam en categorie in.");
        if (input.region() == null || !List.of("Upper Body", "Lower Body", "Full Body").contains(input.region())) {
            throw invalid("Kies Upper body, Lower body of Full body.");
        }
        return new Exercise(id, name, input.region(), category, text(input.subcategory(), 100, "Beweging"),
            text(input.instructions(), 4000, "Instructies"), text(input.notes(), 2000, "Notities"), archived);
    }

    private String text(String value, int maximum, String label) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() > maximum) throw invalid(label + " mag maximaal " + maximum + " tekens bevatten.");
        return trimmed;
    }

    private String nameKey(Exercise exercise) {
        return exercise.name().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + ":" + exercise.region();
    }

    private void rejectDuplicate(Exercise exercise, String exceptId) {
        if (list().stream().anyMatch(item -> !item.id().equals(exceptId) && nameKey(item).equals(nameKey(exercise)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Deze oefening bestaat al voor deze lichaamsregio. Bewerk of herstel de bestaande oefening.");
        }
    }

    private void save(Exercise exercise) throws IOException {
        Map<String, Exercise> updated = new LinkedHashMap<>(overrides);
        updated.put(exercise.id(), exercise);
        if (writer != null) { writer.write(List.copyOf(updated.values())); overrides=updated; return; }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "exercises-", ".tmp");
        try {
            Files.writeString(temporary, mapper.writeValueAsString(updated.values()));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        overrides = updated;
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
