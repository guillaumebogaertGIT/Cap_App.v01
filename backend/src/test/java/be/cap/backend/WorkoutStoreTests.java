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
}
