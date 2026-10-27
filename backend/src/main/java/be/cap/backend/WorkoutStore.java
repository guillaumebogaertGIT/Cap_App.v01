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
@org.springframework.context.annotation.Profile("!accounts")
public class WorkoutStore {
    private final Path file;
    private final StateWriter<State> writer;
    private final java.util.function.Predicate<String> athleteExists;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private List<Workout> workouts;
    public record DatedWorkout(String assignmentId, Workout planned, String note, String weekStart) {
        public DatedWorkout(String assignmentId, Workout planned) { this(assignmentId, planned, "", null); }
    }
    public record State(List<Workout> workouts, List<DatedWorkout> assignments) {}
    private List<DatedWorkout> assignments;
    private boolean legacyFormat;

    @org.springframework.beans.factory.annotation.Autowired
    public WorkoutStore(@Value("${cap.workouts.file:data/workouts.json}") String filename) throws IOException {
        writer = null;
        athleteExists = "preview-guillaume"::equals;
        file = Path.of(filename).toAbsolutePath();
        workouts = new ArrayList<>();
        assignments = new ArrayList<>();
        if (Files.exists(file)) {
            String json = Files.readString(file);
            if (mapper.readTree(json).isArray()) {
                legacyFormat = true;
                workouts.addAll(Arrays.asList(mapper.readValue(json, Workout[].class)));
                for (Workout workout : workouts) if ("preview-guillaume".equals(workout.athleteId()) && workout.assignedDate() != null && !workout.assignedDate().isBlank()) assignments.add(dated(workout));
            } else {
                State state = mapper.readValue(json, State.class);
                workouts.addAll(state.workouts()); assignments.addAll(state.assignments());
            }
        }
    }

    public WorkoutStore(State state, StateWriter<State> writer, java.util.function.Predicate<String> athleteExists) {
        file = null;
        this.writer = writer;
        this.athleteExists = athleteExists;
        workouts = new ArrayList<>(state.workouts());
        assignments = new ArrayList<>(state.assignments());
    }

    public synchronized List<Workout> list() { return List.copyOf(workouts); }
    public synchronized List<DatedWorkout> assignments() { return List.copyOf(assignments); }
    public synchronized DatedWorkout assignment(String id) {
        return assignments.stream().filter(a -> a.assignmentId().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Toewijzing niet gevonden."));
    }

    public synchronized DatedWorkout updateAssignment(String id, Workout input) throws IOException {
        DatedWorkout existing = assignment(id);
        validate(input);
        Workout old = existing.planned();
        Workout planned = new Workout(old.id(), input.name().trim(), input.description() == null ? "" : input.description().trim(),
            input.exercises(), old.athleteId(), old.assignedDate(), input.category(), input.workoutType());
        DatedWorkout updated = new DatedWorkout(id, planned, existing.note(), existing.weekStart());
        save(workouts, assignments.stream().map(a -> a.assignmentId().equals(id) ? updated : a).toList());
        return updated;
    }

    public synchronized DatedWorkout updateNote(String id, String note) throws IOException {
        DatedWorkout existing = assignment(id);
        if (note == null || note.length() > 2000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Een notitie mag maximaal 2000 tekens bevatten.");
        DatedWorkout updated = new DatedWorkout(id, existing.planned(), note.trim(), existing.weekStart());
        save(workouts, assignments.stream().map(a -> a.assignmentId().equals(id) ? updated : a).toList());
        return updated;
    }

    public synchronized void deleteAssignment(String id) throws IOException {
        Workout planned = assignment(id).planned();
        List<Workout> updated = workouts.stream().map(w -> w.id().equals(planned.id())
            && Objects.equals(w.athleteId(), planned.athleteId()) && Objects.equals(w.assignedDate(), planned.assignedDate())
            ? new Workout(w.id(), w.name(), w.description(), w.exercises(), "", "", w.category(), w.workoutType()) : w).toList();
        save(updated, assignments.stream().filter(a -> !a.assignmentId().equals(id)).toList());
    }
    private DatedWorkout dated(Workout workout) {
        String key = workout.id() + ":" + workout.athleteId() + ":" + workout.assignedDate();
        return new DatedWorkout(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), workout);
    }
    private String assignmentDate(String value) {
        if (value == null || value.isBlank()) return LocalDate.now(ZoneId.of("Europe/Brussels")).toString();
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.isBefore(LocalDate.now(ZoneId.of("Europe/Brussels")))) throw invalid();
            return date.toString();
        } catch (java.time.format.DateTimeParseException e) { throw invalid(); }
    }

    public synchronized Workout create(Workout input) throws IOException {
        return write(input, null);
    }
    public synchronized Workout createWeekly(Workout input, String weekStart) throws IOException {
        if (!athleteExists.test(input.athleteId())) throw invalid();
        return write(input, null, validWeek(weekStart));
    }

    private String validWeek(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getDayOfWeek() != java.time.DayOfWeek.MONDAY || date.plusDays(6).isBefore(LocalDate.now(ZoneId.of("Europe/Brussels")))) throw invalid();
            return date.toString();
        } catch (java.time.format.DateTimeParseException | NullPointerException e) { throw invalid(); }
    }

    private DatedWorkout weekly(Workout workout, String weekStart) {
        String key = workout.id() + ":" + workout.athleteId() + ":week:" + weekStart;
        return new DatedWorkout(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), workout, "", weekStart);
    }

    public synchronized Workout assignWeekly(String id, Workout resolved, String weekStart) throws IOException {
        String week = validWeek(weekStart);
        if (!athleteExists.test(resolved.athleteId()) || workouts.stream().noneMatch(w -> w.id().equals(id))) throw invalid();
        Workout planned = new Workout(id, resolved.name(), resolved.description(), resolved.exercises(), resolved.athleteId(), "", resolved.category(), resolved.workoutType());
        DatedWorkout next = weekly(planned, week);
        // Repeating the same weekly assignment preserves its chosen day, edits and notes.
        if (assignments.stream().anyMatch(a -> a.assignmentId().equals(next.assignmentId()))) return assignment(next.assignmentId()).planned();
        List<DatedWorkout> all = new ArrayList<>(assignments); all.add(next);
        save(workouts, all);
        return planned;
    }

    public synchronized DatedWorkout schedule(String id, String date) throws IOException {
        DatedWorkout existing = assignment(id);
        if (existing.weekStart() == null || existing.weekStart().isBlank()) throw invalid();
        validWeek(existing.weekStart());
        String selected = date == null || date.isBlank() ? "" : assignmentDate(date);
        LocalDate first = LocalDate.parse(existing.weekStart());
        if (!selected.isEmpty() && (LocalDate.parse(selected).isBefore(first) || LocalDate.parse(selected).isAfter(first.plusDays(6)))) throw invalid();
        Workout old = existing.planned();
        Workout planned = new Workout(old.id(), old.name(), old.description(), old.exercises(), old.athleteId(), selected, old.category(), old.workoutType());
        DatedWorkout updated = new DatedWorkout(id, planned, existing.note(), existing.weekStart());
        save(workouts, assignments.stream().map(a -> a.assignmentId().equals(id) ? updated : a).toList());
        return updated;
    }

    public synchronized Workout update(String id, Workout input) throws IOException {
        return write(input, id);
    }

    private void validate(Workout input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 100
            || input.description() != null && input.description().length() > 1000
            || input.exercises() == null || input.exercises().isEmpty() || input.exercises().size() > 100) {
            throw invalid();
        }
        for (Workout.Exercise exercise : input.exercises()) {
            if (exercise == null || exercise.name() == null || exercise.name().isBlank()
                || exercise.name().length() > 100 || exercise.sets() < 1 || exercise.sets() > 100
                || exercise.reps() < 1 || exercise.reps() > 1000
                || exercise.restSeconds() != null && (exercise.restSeconds() < 0 || exercise.restSeconds() > 3600)
                || exercise.tempo() != null && exercise.tempo().length() > 40) throw invalid();
            if (exercise.setPlan() != null && (exercise.setPlan().size() != exercise.sets()
                || exercise.setPlan().stream().anyMatch(set -> set == null || set.reps() < 1 || set.reps() > 1000))) throw invalid();
            if (exercise.supersetGroup() != null && !exercise.supersetGroup().isEmpty() && !exercise.supersetGroup().matches("[A-F]")) throw invalid();
        }
        Map<String, Long> groups = input.exercises().stream().filter(e -> e.supersetGroup() != null && !e.supersetGroup().isEmpty())
            .collect(java.util.stream.Collectors.groupingBy(Workout.Exercise::supersetGroup, java.util.stream.Collectors.counting()));
        if (groups.values().stream().anyMatch(count -> count < 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kies minstens twee oefeningen per superset.");
        }
        String category = input.category() == null ? "" : input.category();
        if (!List.of("", "CAP HYROX", "CAP Hybrid", "CAP Move", "CAP Lift & HIT", "Private", "Open gym", "Athlete").contains(category)) throw invalid();
        String workoutType = input.workoutType() == null ? "other" : input.workoutType();
        if (!List.of("other", "full-body", "lower-body", "upper-body", "conditioning").contains(workoutType)) throw invalid();
    }

    private Workout write(Workout input, String id) throws IOException {
        return write(input, id, null);
    }
    private Workout write(Workout input, String id, String weekStart) throws IOException {
        Workout existing = id == null ? null : workouts.stream().filter(w -> w.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout niet gevonden."));
        validate(input);
        String category = input.category() == null ? "" : input.category();
        String workoutType = input.workoutType() == null ? "other" : input.workoutType();
        String athlete = existing != null ? existing.athleteId() : input.athleteId() == null ? "" : input.athleteId();
        if (!athlete.isEmpty() && !athleteExists.test(athlete)) throw invalid();
        String date = weekStart != null ? "" : existing != null ? existing.assignedDate() : athlete.isEmpty() ? "" : assignmentDate(input.assignedDate());
        Workout saved = new Workout(id == null ? UUID.randomUUID().toString() : id, input.name().trim(),
            input.description() == null ? "" : input.description().trim(),
            input.exercises().stream().map(e -> new Workout.Exercise(e.name().trim(), e.sets(), e.reps(),
                e.loadMode(), e.loadValue(), e.targetKg(), e.referenceKg(), e.restSeconds(),
                e.tempo() == null ? null : e.tempo().trim(), e.supersetGroup(), e.setPlan())).toList(), athlete, date, category, workoutType);
        List<Workout> updated = new ArrayList<>(workouts);
        // One assigned workout per example athlete per day; older workouts stay in the library.
        if (existing == null && weekStart == null && !athlete.isEmpty()) updated.replaceAll(w -> athlete.equals(w.athleteId()) && date.equals(w.assignedDate())
            ? new Workout(w.id(), w.name(), w.description(), w.exercises(), "", "", w.category(), w.workoutType()) : w);
        if (existing == null) updated.add(saved);
        else updated.replaceAll(w -> w.id().equals(id) ? saved : w);
        List<DatedWorkout> scheduled = new ArrayList<>(assignments);
        if (existing == null && !athlete.isEmpty()) scheduled.add(weekStart == null ? dated(saved) : weekly(saved, weekStart));
        save(updated, scheduled);
        return saved;
    }

    public synchronized void delete(String id) throws IOException {
        List<Workout> updated = new ArrayList<>(workouts);
        if (!updated.removeIf(workout -> workout.id().equals(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout niet gevonden.");
        }
        save(updated, assignments.stream().filter(a -> !a.planned().id().equals(id)).toList());
    }

    public synchronized Workout assign(String id, Workout resolved) throws IOException {
        if (!athleteExists.test(resolved.athleteId())) throw invalid();
        if (workouts.stream().noneMatch(w -> w.id().equals(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout niet gevonden.");
        }
        String date = assignmentDate(resolved.assignedDate());
        Workout assigned = new Workout(id, resolved.name(), resolved.description(), resolved.exercises(), resolved.athleteId(), date, resolved.category(), resolved.workoutType());
        List<Workout> updated = workouts.stream().map(w -> w.id().equals(id) ? assigned
            : resolved.athleteId().equals(w.athleteId()) && date.equals(w.assignedDate())
                ? new Workout(w.id(), w.name(), w.description(), w.exercises(), "", "", w.category(), w.workoutType()) : w).toList();
        DatedWorkout scheduled = dated(assigned);
        List<DatedWorkout> all = new ArrayList<>(assignments.stream().filter(a -> !a.assignmentId().equals(scheduled.assignmentId())).toList());
        all.add(scheduled);
        save(updated, all);
        return assigned;
    }

    private void save(List<Workout> updated, List<DatedWorkout> scheduled) throws IOException {
        if (writer != null) {
            writer.write(new State(updated, scheduled));
            workouts = updated; assignments = scheduled;
            return;
        }
        Files.createDirectories(file.getParent());
        if (legacyFormat && Files.exists(file)) {
            Path backup = file.resolveSibling(file.getFileName() + ".legacy-backup");
            if (!Files.exists(backup)) Files.copy(file, backup);
        }
        Path temporary = Files.createTempFile(file.getParent(), "workouts-", ".tmp");
        try {
            Files.writeString(temporary, mapper.writeValueAsString(new State(updated, scheduled)));
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        workouts = updated;
        assignments = scheduled;
        legacyFormat = false;
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Controleer de workoutnaam, oefeningen en sporter.");
    }
}
