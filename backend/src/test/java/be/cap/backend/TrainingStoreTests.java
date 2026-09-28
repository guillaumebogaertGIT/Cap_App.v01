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
}
