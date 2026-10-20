package be.cap.backend;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class WorkoutStoreTests {
    @TempDir Path directory;

    private Workout workout(String name, String athlete, int sets) {
        return new Workout(null, name, "Strength", List.of(new Workout.Exercise("Squat", sets, 10)), athlete, null);
    }

    @Test void savesReloadsAndReplacesTodaysAssignmentWithoutDeletingWorkouts() throws Exception {
        String file = directory.resolve("workouts.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        Workout first = store.create(workout("First", "preview-guillaume", 3));
        assertNotNull(first.id());
        assertFalse(first.assignedDate().isBlank());
        store.create(workout("Second", "preview-guillaume", 4));
        List<Workout> reloaded = new WorkoutStore(file).list();
        assertEquals(2, reloaded.size());
        assertEquals("", reloaded.get(0).athleteId());
        assertEquals("preview-guillaume", reloaded.get(1).athleteId());
        assertEquals(4, reloaded.get(1).exercises().get(0).sets());
    }

    @Test void invalidRequestsDoNotChangeSavedWorkouts() throws Exception {
        String file = directory.resolve("workouts.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        store.create(workout("Valid", "", 3));
        assertThrows(ResponseStatusException.class, () -> store.create(workout(" ", "", 3)));
        assertThrows(ResponseStatusException.class, () -> store.create(workout("Invalid", "", 0)));
        assertThrows(ResponseStatusException.class, () -> store.create(workout("Invalid", "unknown", 3)));
        assertEquals(1, new WorkoutStore(file).list().size());
    }

    @Test void updateKeepsIdentityAndAssignmentAndDoesNotDuplicate() throws Exception {
        String file = directory.resolve("editing.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        Workout original = store.create(workout("Original", "preview-guillaume", 3));
        Workout updated = store.update(original.id(), workout("Corrected", "", 4));
        assertEquals(original.id(), updated.id());
        assertEquals(original.athleteId(), updated.athleteId());
        assertEquals(original.assignedDate(), updated.assignedDate());
        assertEquals(List.of(updated), new WorkoutStore(file).list());
        assertEquals("Corrected", updated.name());
        assertEquals(4, updated.exercises().get(0).sets());
        assertThrows(ResponseStatusException.class, () -> store.update(original.id(), workout(" ", "", 3)));
        assertEquals(404, assertThrows(ResponseStatusException.class,
            () -> store.update("missing", workout("Valid", "", 3))).getStatusCode().value());
        assertEquals(List.of(updated), new WorkoutStore(file).list());
    }

    @Test void deletionPersistsAndLeavesOtherWorkoutsUntouched() throws Exception {
        String file = directory.resolve("workouts.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        Workout keep = store.create(workout("Keep", "", 3));
        Workout remove = store.create(workout("Remove", "preview-guillaume", 4));
        store.delete(remove.id());
        assertEquals(List.of(keep), new WorkoutStore(file).list());
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> store.delete(remove.id()));
        assertEquals(404, error.getStatusCode().value());
        assertEquals(List.of(keep), store.list());
    }

    @Test void restAndTempoSurviveResolutionAndReloadForEveryLoadMode() throws Exception {
        String file = directory.resolve("details.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        TrainingStore training = new TrainingStore(directory.resolve("training.json").toString());
        for (String mode : List.of("none", "fixed")) {
            Workout input = new Workout(null, "Details", "", List.of(
                new Workout.Exercise("Squat", 3, 5, mode, 60.0, null, null, 90, "3-0-1-0")), "", null);
            store.create(training.resolveTargets(input));
        }
        for (Workout saved : new WorkoutStore(file).list()) {
            assertEquals(90, saved.exercises().get(0).restSeconds());
            assertEquals("3-0-1-0", saved.exercises().get(0).tempo());
        }
        Workout invalidRest = new Workout(null, "Invalid", "", List.of(
            new Workout.Exercise("Squat", 3, 5, "none", null, null, null, -1, "")), "", null);
        assertThrows(ResponseStatusException.class, () -> store.create(invalidRest));
    }
}
