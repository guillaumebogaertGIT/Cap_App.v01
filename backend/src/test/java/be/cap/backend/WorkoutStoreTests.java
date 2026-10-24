package be.cap.backend;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class WorkoutStoreTests {
    @TempDir Path directory;

    @Test void flexibleWeekCanBePlannedWithoutDuplicatingOrLosingEditsAndNotes() throws Exception {
        String file = directory.resolve("weekly.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels"));
        String monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
        Workout template = store.create(workout("Library", "", 3));
        Workout assigned = new Workout(template.id(), template.name(), "", template.exercises(), "preview-guillaume", "");
        store.assignWeekly(template.id(), assigned, monday);
        String id = store.assignments().get(0).assignmentId();
        assertEquals("", store.assignment(id).planned().assignedDate());
        store.updateNote(id, "Weekly note");
        store.updateAssignment(id, workout("Personal", "", 4));
        store.schedule(id, today.toString());
        store.assignWeekly(template.id(), assigned, monday);
        WorkoutStore reloaded = new WorkoutStore(file);
        assertEquals(1, reloaded.assignments().size());
        assertEquals(monday, reloaded.assignment(id).weekStart());
        assertEquals(today.toString(), reloaded.assignment(id).planned().assignedDate());
        assertEquals("Weekly note", reloaded.assignment(id).note());
        assertEquals("Personal", reloaded.assignment(id).planned().name());
        assertEquals("Library", reloaded.list().get(0).name());
        assertThrows(ResponseStatusException.class, () -> reloaded.schedule(id, java.time.LocalDate.parse(monday).plusDays(7).toString()));
        reloaded.schedule(id, "");
        assertEquals("", new WorkoutStore(file).assignment(id).planned().assignedDate());
        int before = reloaded.list().size();
        assertThrows(ResponseStatusException.class, () -> reloaded.createWeekly(workout("Invalid week", "preview-guillaume", 3), java.time.LocalDate.parse(monday).plusDays(1).toString()));
        assertEquals(before, new WorkoutStore(file).list().size());
        reloaded.createWeekly(workout("New weekly", "preview-guillaume", 3), monday);
        WorkoutStore created = new WorkoutStore(file);
        assertEquals(2, created.assignments().size());
        assertEquals(monday, created.assignments().get(1).weekStart());
    }

    @Test void assignmentEditsNotesAndDeletionPersistWithoutChangingTemplateOrOtherDates() throws Exception {
        String file = directory.resolve("assignment-editing.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        Workout template = store.create(workout("Template", "", 3));
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).toString();
        String tomorrow = java.time.LocalDate.parse(today).plusDays(1).toString();
        for (String date : List.of(today, tomorrow)) {
            store.assign(template.id(), new Workout(template.id(), template.name(), template.description(), template.exercises(), "preview-guillaume", date));
        }
        String id = store.assignments().get(0).assignmentId();
        store.updateNote(id, "  Rustig opbouwen  ");
        store.updateAssignment(id, workout("Personal version", "", 5));
        WorkoutStore reloaded = new WorkoutStore(file);
        assertEquals("Rustig opbouwen", reloaded.assignment(id).note());
        assertEquals("Personal version", reloaded.assignment(id).planned().name());
        assertEquals(5, reloaded.assignment(id).planned().exercises().get(0).sets());
        assertEquals(today, reloaded.assignment(id).planned().assignedDate());
        assertEquals("preview-guillaume", reloaded.assignment(id).planned().athleteId());
        assertEquals("Template", reloaded.list().get(0).name());
        assertEquals(3, reloaded.assignments().get(1).planned().exercises().get(0).sets());
        assertThrows(ResponseStatusException.class, () -> reloaded.updateNote(id, "x".repeat(2001)));
        assertThrows(ResponseStatusException.class, () -> reloaded.updateAssignment(id, workout("", "", 3)));
        assertEquals("Rustig opbouwen", new WorkoutStore(file).assignment(id).note());
        reloaded.deleteAssignment(id);
        WorkoutStore deleted = new WorkoutStore(file);
        assertEquals(1, deleted.list().size());
        assertEquals(1, deleted.assignments().size());
        assertEquals(tomorrow, deleted.assignments().get(0).planned().assignedDate());
        assertThrows(ResponseStatusException.class, () -> deleted.assignment(id));
    }

    @Test void workoutTypeAndAudiencePersistThroughCreateResolveAssignAndReload() throws Exception {
        String file = directory.resolve("workout-types.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        TrainingStore training = new TrainingStore(directory.resolve("types-training.json").toString());
        for (String category : List.of("CAP HYROX", "Private", "Open gym", "Athlete")) {
            Workout input = new Workout(null, "Upper session", "", List.of(new Workout.Exercise("Row", 3, 8)), "", "", category, "upper-body");
            Workout saved = store.create(training.resolveTargets(input));
            store.assign(saved.id(), training.resolveTargets(new Workout(saved.id(), saved.name(), "", saved.exercises(), "preview-guillaume", "", category, saved.workoutType())));
        }
        WorkoutStore reloaded = new WorkoutStore(file);
        assertEquals(4, reloaded.list().size());
        assertTrue(reloaded.list().stream().allMatch(w -> "upper-body".equals(w.workoutType())));
        assertTrue(reloaded.assignments().stream().allMatch(a -> "upper-body".equals(a.planned().workoutType())));
        assertEquals(List.of("CAP HYROX", "Private", "Open gym", "Athlete"), reloaded.list().stream().map(Workout::category).toList());
        assertThrows(ResponseStatusException.class, () -> store.create(new Workout(null, "Invalid", "", List.of(new Workout.Exercise("Row", 3, 8)), "", "", "", "unknown")));
    }

    @Test void datedAssignmentsKeepSeparateSnapshotsAndCategoryAfterTemplateEdits() throws Exception {
        String file = directory.resolve("dated.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        Workout template = store.create(new Workout(null, "HYROX A", "", List.of(new Workout.Exercise("Squat", 3, 10)), "", "", "CAP HYROX"));
        String tomorrow = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).plusDays(1).toString();
        String nextDay = java.time.LocalDate.parse(tomorrow).plusDays(1).toString();
        for (String date : List.of(tomorrow, nextDay)) {
            store.assign(template.id(), new Workout(template.id(), template.name(), "", template.exercises(), "preview-guillaume", date, template.category()));
        }
        store.update(template.id(), new Workout(null, "Edited", "", List.of(new Workout.Exercise("Row", 4, 8)), "", "", "CAP HYROX"));
        WorkoutStore reloaded = new WorkoutStore(file);
        assertEquals(1, reloaded.list().size());
        assertEquals(2, reloaded.assignments().size());
        assertEquals("CAP HYROX", reloaded.list().get(0).category());
        assertEquals(List.of(tomorrow, nextDay), reloaded.assignments().stream().map(a -> a.planned().assignedDate()).toList());
        assertTrue(reloaded.assignments().stream().allMatch(a -> a.planned().name().equals("HYROX A") && a.planned().exercises().get(0).name().equals("Squat")));
        assertNotEquals(reloaded.assignments().get(0).assignmentId(), reloaded.assignments().get(1).assignmentId());
        assertThrows(ResponseStatusException.class, () -> reloaded.assign(template.id(), new Workout(template.id(), "Invalid", "", template.exercises(), "preview-guillaume", "invalid", "CAP HYROX")));
    }

    @Test void oldArrayStorageMigratesWithoutLosingWorkoutOrAssignment() throws Exception {
        Path file = directory.resolve("legacy.json");
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Brussels")).toString();
        String original = """
            [{"id":"legacy","name":"Old","description":"","exercises":[{"name":"Squat","sets":3,"reps":5}],"athleteId":"preview-guillaume","assignedDate":"%s"}]
            """.formatted(today);
        java.nio.file.Files.writeString(file, original);
        WorkoutStore store = new WorkoutStore(file.toString());
        assertEquals(1, store.assignments().size());
        store.create(workout("New", "", 3));
        assertEquals(original, java.nio.file.Files.readString(file.resolveSibling("legacy.json.legacy-backup")));
        WorkoutStore reloaded = new WorkoutStore(file.toString());
        assertEquals(2, reloaded.list().size());
        assertEquals(1, reloaded.assignments().size());
    }

    @Test void assignsSavedWorkoutWithoutCopyAndReplacesTodaysAssignment() throws Exception {
        String file = directory.resolve("assignments.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        Workout previous = store.create(workout("Previous", "preview-guillaume", 3));
        Workout template = store.create(workout("Saved", "", 4));
        Workout request = new Workout(template.id(), template.name(), template.description(), template.exercises(), "preview-guillaume", "");
        Workout assigned = store.assign(template.id(), request);
        assertEquals(template.id(), assigned.id());
        assertEquals(previous.assignedDate(), assigned.assignedDate());
        var saved = new WorkoutStore(file).list();
        assertEquals(2, saved.size());
        assertEquals("", saved.get(0).athleteId());
        assertEquals("preview-guillaume", saved.get(1).athleteId());
        store.assign(template.id(), request);
        assertEquals(2, store.list().size());
        assertThrows(ResponseStatusException.class, () -> store.assign("missing", request));
        assertThrows(ResponseStatusException.class, () -> store.assign(template.id(), template));
    }

    @Test void supersetsSurviveResolutionAndReloadAndRequireTwoExercises() throws Exception {
        String file = directory.resolve("supersets.json").toString();
        WorkoutStore store = new WorkoutStore(file);
        TrainingStore training = new TrainingStore(directory.resolve("training.json").toString());
        var squat = new Workout.Exercise("Squat", 3, 8, "none", null, null, null, 60, "3-0-1-0", "A");
        var row = new Workout.Exercise("Row", 3, 8, "fixed", 30.0, null, null, 60, "2-0-1-0", "A");
        store.create(training.resolveTargets(new Workout(null, "Superset", "", List.of(squat, row), "", "")));
        assertTrue(new WorkoutStore(file).list().get(0).exercises().stream().allMatch(e -> "A".equals(e.supersetGroup())));
        assertThrows(ResponseStatusException.class, () -> store.create(new Workout(null, "Incomplete", "", List.of(squat), "", "")));
        assertEquals(1, store.list().size());
    }

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
