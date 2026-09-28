package be.cap.backend;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

/** Local prototype storage. Real account authorization must be added before deployment. */
@Service
public class WorkoutStore {
    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private List<Workout> workouts;

    public WorkoutStore(@Value("${cap.workouts.file:data/workouts.json}") String filename) throws IOException {
        file = Path.of(filename).toAbsolutePath();
        workouts = Files.exists(file)
            ? new ArrayList<>(Arrays.asList(mapper.readValue(Files.readString(file), Workout[].class)))
            : new ArrayList<>();
    }

    public synchronized List<Workout> list() { return List.copyOf(workouts); }

    public synchronized Workout create(Workout input) throws IOException {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 100
            || input.description() != null && input.description().length() > 1000
            || input.exercises() == null || input.exercises().isEmpty() || input.exercises().size() > 100) {
            throw invalid();
        }
        for (Workout.Exercise exercise : input.exercises()) {
            if (exercise == null || exercise.name() == null || exercise.name().isBlank()
                || exercise.name().length() > 100 || exercise.sets() < 1 || exercise.sets() > 100
                || exercise.reps() < 1 || exercise.reps() > 1000) throw invalid();
        }
        String athlete = input.athleteId() == null ? "" : input.athleteId();
        if (!athlete.isEmpty() && !athlete.equals("preview-guillaume")) throw invalid();
        String date = athlete.isEmpty() ? "" : LocalDate.now(ZoneId.of("Europe/Brussels")).toString();
        Workout saved = new Workout(UUID.randomUUID().toString(), input.name().trim(),
            input.description() == null ? "" : input.description().trim(),
            input.exercises().stream().map(e -> new Workout.Exercise(e.name().trim(), e.sets(), e.reps(),
                e.loadMode(), e.loadValue(), e.targetKg(), e.referenceKg())).toList(), athlete, date);
        List<Workout> updated = new ArrayList<>(workouts);
        // One assigned workout per example athlete per day; older workouts stay in the library.
        if (!athlete.isEmpty()) updated.replaceAll(w -> athlete.equals(w.athleteId()) && date.equals(w.assignedDate())
            ? new Workout(w.id(), w.name(), w.description(), w.exercises(), "", "") : w);
        updated.add(saved);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "workouts-", ".tmp");
        try {
            Files.writeString(temporary, mapper.writeValueAsString(updated));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        workouts = updated;
        return saved;
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Controleer de workoutnaam, oefeningen en sporter.");
    }
}
