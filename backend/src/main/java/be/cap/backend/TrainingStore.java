package be.cap.backend;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Completed sessions are immutable snapshots. This prototype has one example athlete. */
@Service
public class TrainingStore {
    public record SetResult(int reps, Double kg) {}
    public record ExerciseResult(int exerciseIndex, List<SetResult> sets) {}
    public record Completion(String id, String workoutId, String startedAt, String finishedAt,
                             List<ExerciseResult> exercises) {}
    public record Record(String exercise, int reps, double kg) {}
    public record Session(String id, String athleteId, Workout planned, Instant startedAt, Instant finishedAt,
                          long durationSeconds, List<ExerciseResult> exercises, List<Record> newRecords) {}
    private final Path file;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private List<Session> sessions;

    public TrainingStore(@Value("${cap.training.file:data/training.json}") String filename) throws IOException {
        file = Path.of(filename).toAbsolutePath();
        sessions = Files.exists(file) ? new ArrayList<>(Arrays.asList(mapper.readValue(Files.readString(file), Session[].class))) : new ArrayList<>();
    }
    public synchronized List<Session> history() { return List.copyOf(sessions); }
    private static String key(String name, int reps) { return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + ":" + reps; }
    public synchronized List<Record> records() {
        Map<String, Record> best = new LinkedHashMap<>();
        for (Session session : sessions) for (ExerciseResult exercise : session.exercises()) {
            String name = session.planned().exercises().get(exercise.exerciseIndex()).name();
            for (SetResult set : exercise.sets()) {
                String key = key(name, set.reps());
                if (!best.containsKey(key) || set.kg() > best.get(key).kg()) best.put(key, new Record(name, set.reps(), set.kg()));
            }
        }
        return List.copyOf(best.values());
    }

    /** Resolve percentage targets once so future PRs do not rewrite an existing plan. */
    public synchronized Workout resolveTargets(Workout input) {
        if (input == null || input.exercises() == null || input.exercises().stream().anyMatch(Objects::isNull)) throw invalid("Oefeningen ontbreken.");
        List<Workout.Exercise> exercises = input.exercises().stream().map(e -> {
            String mode = e.loadMode() == null ? "none" : e.loadMode();
            if (mode.equals("none")) return new Workout.Exercise(e.name(), e.sets(), e.reps());
            Double value = e.loadValue();
            if (value == null || !Double.isFinite(value) || value < 0 || value > 2000) throw invalid("Ongeldig gewicht of percentage.");
            if (mode.equals("fixed")) return new Workout.Exercise(e.name(), e.sets(), e.reps(), mode, value, value, null);
            if (!mode.equals("percent") || value <= 0 || value > 200 || e.name() == null
                || !"preview-guillaume".equals(input.athleteId())) throw invalid("Een percentage vereist een toegewezen sporter en een waarde van 1 tot 200.");
            Record reference = records().stream().filter(r -> key(r.exercise(), r.reps()).equals(key(e.name(), e.reps()))).findFirst()
                .orElseThrow(() -> invalid("Geen PR voor " + e.name() + " met " + e.reps() + " herhalingen. Kies een vast gewicht."));
            double target = Math.round(reference.kg() * value) / 100.0;
            return new Workout.Exercise(e.name(), e.sets(), e.reps(), mode, value, target, reference.kg());
        }).toList();
        return new Workout(input.id(), input.name(), input.description(), exercises, input.athleteId(), input.assignedDate());
    }

    public synchronized Session finish(Completion input, Workout planned) throws IOException {
        if (input == null || input.id() == null) throw invalid("Sessie ontbreekt.");
        try { UUID.fromString(input.id()); } catch (IllegalArgumentException e) { throw invalid("Ongeldige sessiecode."); }
        // Retrying after a lost response must never duplicate a completed session.
        for (Session existing : sessions) if (existing.id().equals(input.id())) return existing;
        if (planned == null || !planned.id().equals(input.workoutId())) throw invalid("Workout niet gevonden.");
        Instant start, end;
        try { start = Instant.parse(input.startedAt()); end = Instant.parse(input.finishedAt()); }
        catch (Exception e) { throw invalid("Ongeldige trainingstijden."); }
        long duration = Duration.between(start, end).getSeconds();
        if (duration < 0 || duration > 604800 || end.isAfter(Instant.now().plusSeconds(300))) throw invalid("Controleer de trainingstijden.");
        if (input.exercises() == null || input.exercises().size() != planned.exercises().size()) throw invalid("Controleer de oefeningen.");
        Set<Integer> indexes = new HashSet<>();
        int totalSets = 0;
        Map<String, Record> previous = new HashMap<>();
        records().forEach(r -> previous.put(key(r.exercise(), r.reps()), r));
        Map<String, Record> improvements = new LinkedHashMap<>();
        for (ExerciseResult exercise : input.exercises()) {
            if (exercise == null || exercise.exerciseIndex() < 0 || exercise.exerciseIndex() >= planned.exercises().size()
                || !indexes.add(exercise.exerciseIndex()) || exercise.sets() == null || exercise.sets().size() > 100) throw invalid("Ongeldige oefening of sets.");
            String name = planned.exercises().get(exercise.exerciseIndex()).name();
            totalSets += exercise.sets().size();
            for (SetResult set : exercise.sets()) {
                if (set == null || set.reps() < 1 || set.reps() > 1000 || set.kg() == null || !Double.isFinite(set.kg()) || set.kg() < 0 || set.kg() > 2000) throw invalid("Controleer gewicht en herhalingen.");
                String key = key(name, set.reps());
                double best = previous.containsKey(key) ? previous.get(key).kg() : -1;
                if (set.kg() > best && (!improvements.containsKey(key) || set.kg() > improvements.get(key).kg())) improvements.put(key, new Record(name, set.reps(), set.kg()));
            }
        }
        if (totalSets == 0) throw invalid("Vul minstens één uitgevoerde set in.");
        List<ExerciseResult> results = input.exercises().stream().map(e -> new ExerciseResult(e.exerciseIndex(), List.copyOf(e.sets()))).toList();
        Session session = new Session(input.id(), "preview-guillaume", planned, start, end, duration, results, List.copyOf(improvements.values()));
        List<Session> updated = new ArrayList<>(sessions); updated.add(session);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "training-", ".tmp");
        try {
            Files.writeString(temporary, mapper.writeValueAsString(updated));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        sessions = updated;
        return session;
    }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
