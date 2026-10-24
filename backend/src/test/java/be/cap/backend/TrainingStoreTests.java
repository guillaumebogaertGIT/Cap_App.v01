package be.cap.backend;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class TrainingStoreTests {
    @TempDir Path directory;
    private Workout plan() { return new Workout("workout-1", "Strength", "", List.of(new Workout.Exercise("Squat", 3, 5)), "preview-guillaume", ""); }
    private TrainingStore.Completion completion(int reps, double kg) {
        return new TrainingStore.Completion(UUID.randomUUID().toString(), "workout-1", Instant.now().minusSeconds(120).toString(), Instant.now().toString(),
            List.of(new TrainingStore.ExerciseResult(0, List.of(new TrainingStore.SetResult(reps, kg)))));
    }
    @Test void repRecordsPersistenceAndIdempotentCompletion() throws Exception {
        String file = directory.resolve("training.json").toString();
        TrainingStore store = new TrainingStore(file);
        var first = completion(5, 60);
        var saved = store.finish(first, plan());
        assertEquals(120, saved.durationSeconds());
        assertEquals(1, saved.newRecords().size());
        assertEquals(saved.id(), store.finish(first, plan()).id());
        assertEquals(1, store.history().size());
        assertTrue(store.finish(completion(5, 60), plan()).newRecords().isEmpty());
        store.finish(completion(3, 70), plan());
        store.finish(completion(5, 62.5), plan());
        TrainingStore reloaded = new TrainingStore(file);
        assertEquals(4, reloaded.history().size());
        assertEquals(2, reloaded.records().size());
        assertEquals(62.5, reloaded.records().stream().filter(r -> r.reps() == 5).findFirst().orElseThrow().kg());
        assertEquals(3, reloaded.history().get(0).planned().exercises().get(0).sets());
    }
    @Test void percentagesUseMatchingRepRecordAndFreezeTarget() throws Exception {
        TrainingStore store = new TrainingStore(directory.resolve("training.json").toString());
        Workout request = new Workout(null, "Next", "", List.of(new Workout.Exercise("Squat", 3, 5, "percent", 90.0, 999.0, 999.0)), "preview-guillaume", "");
        assertThrows(ResponseStatusException.class, () -> store.resolveTargets(request));
        store.finish(completion(5, 60), plan());
        Workout resolved = store.resolveTargets(request);
        assertEquals(54.0, resolved.exercises().get(0).targetKg());
        assertEquals(60.0, resolved.exercises().get(0).referenceKg());
        store.finish(completion(5, 70), plan());
        assertEquals(54.0, resolved.exercises().get(0).targetKg());
        Workout fixed = new Workout(null, "Fixed", "", List.of(new Workout.Exercise("New exercise", 2, 8, "fixed", 25.0, null, null)), "", "");
        assertEquals(25.0, store.resolveTargets(fixed).exercises().get(0).targetKg());
    }
    @Test void invalidResultsNeverCreateHistoryOrRecords() throws Exception {
        TrainingStore store = new TrainingStore(directory.resolve("training.json").toString());
        assertThrows(ResponseStatusException.class, () -> store.finish(completion(0, 60), plan()));
        assertThrows(ResponseStatusException.class, () -> store.finish(completion(5, -1), plan()));
        assertThrows(ResponseStatusException.class, () -> store.finish(completion(5, Double.NaN), plan()));
        var missingWeight = new TrainingStore.Completion(UUID.randomUUID().toString(), "workout-1", Instant.now().minusSeconds(30).toString(), Instant.now().toString(),
            List.of(new TrainingStore.ExerciseResult(0, List.of(new TrainingStore.SetResult(5, null)))));
        assertThrows(ResponseStatusException.class, () -> store.finish(missingWeight, plan()));
        var invalid = new TrainingStore.Completion(UUID.randomUUID().toString(), "workout-1", Instant.now().toString(), Instant.now().minusSeconds(30).toString(), List.of());
        assertThrows(ResponseStatusException.class, () -> store.finish(invalid, plan()));
        assertTrue(store.history().isEmpty()); assertTrue(store.records().isEmpty());
    }

    @Test void optionalRirPersistsAndRejectsOutOfRangeValues() throws Exception {
        String file = directory.resolve("rir.json").toString();
        TrainingStore store = new TrainingStore(file);
        for (int rir : new int[]{0, 2, 10, -1, 11}) {
            var input = new TrainingStore.Completion(UUID.randomUUID().toString(), "workout-1",
                Instant.now().minusSeconds(60).toString(), Instant.now().toString(),
                List.of(new TrainingStore.ExerciseResult(0, List.of(new TrainingStore.SetResult(5, 60.0, rir)))));
            if (rir < 0 || rir > 10) assertThrows(ResponseStatusException.class, () -> store.finish(input, plan()));
            else store.finish(input, plan());
        }
        assertEquals(List.of(0, 2, 10), new TrainingStore(file).history().stream()
            .map(session -> session.exercises().get(0).sets().get(0).rir()).toList());
    }
    @Test void perSetTargetsUseExactRepRecordsAndPersistActualWeights() throws Exception {
        String filename = directory.resolve("set-training.json").toString();
        TrainingStore store = new TrainingStore(filename);
        store.finish(completion(5, 100), plan());
        store.finish(completion(8, 80), plan());
        var sets = List.of(new Workout.SetTarget(8, "percent", 60.0, 999.0, 999.0),
            new Workout.SetTarget(5, "percent", 80.0, null, null),
            new Workout.SetTarget(6, "fixed", 45.0, null, null),
            new Workout.SetTarget(10, "none", null, null, null));
        var exercise = new Workout.Exercise("Squat", 4, 8, "none", null, null, null, 60, "3-0-1-0", null, sets);
        Workout request = new Workout("workout-1", "Build up", "", List.of(exercise), "preview-guillaume", "");
        Workout resolved = store.resolveTargets(request);
        var targets = resolved.exercises().getFirst().setPlan();
        assertEquals(48.0, targets.get(0).targetKg());
        assertEquals(80.0, targets.get(0).referenceKg());
        assertEquals(80.0, targets.get(1).targetKg());
        assertEquals(45.0, targets.get(2).targetKg());
        assertNull(targets.get(3).targetKg());
        var saved = new WorkoutStore(directory.resolve("set-workouts.json").toString()).create(resolved);
        assertEquals(targets, new WorkoutStore(directory.resolve("set-workouts.json").toString()).list().getFirst().exercises().getFirst().setPlan());
        var actual = store.finish(completion(8, 52.5), resolved);
        assertEquals(52.5, actual.exercises().getFirst().sets().getFirst().kg());
        var reloaded = new TrainingStore(filename).history().getLast();
        assertEquals(targets, reloaded.planned().exercises().getFirst().setPlan());
        assertEquals(48.0, resolved.exercises().getFirst().setPlan().getFirst().targetKg());
        var missing = new Workout.Exercise("Squat", 1, 9, "none", null, null, null, null, null, null,
            List.of(new Workout.SetTarget(9, "percent", 60.0, null, null)));
        assertThrows(ResponseStatusException.class, () -> store.resolveTargets(new Workout(null, "Missing", "", List.of(missing), "preview-guillaume", "")));
        var invalid = new Workout.Exercise("Squat", 2, 8, "none", null, null, null, null, null, null, sets);
        assertThrows(ResponseStatusException.class, () -> store.resolveTargets(new Workout(null, "Invalid", "", List.of(invalid), "preview-guillaume", "")));
    }
}
