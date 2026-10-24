package be.cap.backend;

import java.util.List;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@CrossOrigin(origins = "http://127.0.0.1:5500")
@RestController
public class WorkoutController {
    public record Assignment(String athleteId, String date, String weekStart) {}
    private final WorkoutStore store;
    private final TrainingStore training;

    public WorkoutController(WorkoutStore store, TrainingStore training) { this.store = store; this.training = training; }

    @GetMapping("/api/workouts")
    public List<Workout> getWorkouts() {
        return store.list();
    }
    @GetMapping("/api/workout-assignments")
    public List<WorkoutStore.DatedWorkout> getAssignments() { return store.assignments(); }

    public record Note(String note) {}
    public record Schedule(String date) {}

    @PutMapping("/api/workout-assignments/{id}/schedule")
    public WorkoutStore.DatedWorkout schedule(@PathVariable("id") String id, @RequestBody Schedule schedule) throws IOException {
        if (training.history().stream().anyMatch(session -> id.equals(session.assignmentId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deze workout is al afgerond.");
        }
        return store.schedule(id, schedule.date());
    }

    @PutMapping("/api/workout-assignments/{id}")
    public WorkoutStore.DatedWorkout updateAssignment(@PathVariable("id") String id, @RequestBody Workout workout) throws IOException {
        Workout old = store.assignment(id).planned();
        Workout input = new Workout(old.id(), workout.name(), workout.description(), workout.exercises(), old.athleteId(), old.assignedDate(), workout.category(), workout.workoutType());
        return store.updateAssignment(id, training.resolveTargets(input));
    }

    @PutMapping("/api/workout-assignments/{id}/note")
    public WorkoutStore.DatedWorkout updateNote(@PathVariable("id") String id, @RequestBody Note note) throws IOException {
        return store.updateNote(id, note.note());
    }

    @DeleteMapping("/api/workout-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable("id") String id) throws IOException { store.deleteAssignment(id); }

    @PostMapping("/api/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    public Workout createWorkout(@RequestBody Workout workout, @RequestParam(value = "weekStart", required = false) String weekStart) throws IOException {
        Workout resolved = training.resolveTargets(workout);
        return weekStart == null ? store.create(resolved) : store.createWeekly(resolved, weekStart);
    }

    @DeleteMapping("/api/workouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkout(@PathVariable("id") String id) throws IOException {
        store.delete(id);
    }

    @PutMapping("/api/workouts/{id}")
    public Workout updateWorkout(@PathVariable("id") String id, @RequestBody Workout workout) throws IOException {
        Workout existing = store.list().stream().filter(w -> w.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout niet gevonden."));
        Workout input = new Workout(id, workout.name(), workout.description(), workout.exercises(), existing.athleteId(), existing.assignedDate(), workout.category(), workout.workoutType());
        return store.update(id, training.resolveTargets(input));
    }

    @PostMapping("/api/workouts/{id}/assignment")
    public Workout assignWorkout(@PathVariable("id") String id, @RequestBody Assignment assignment) throws IOException {
        Workout existing = store.list().stream().filter(w -> w.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout niet gevonden."));
        Workout input = new Workout(id, existing.name(), existing.description(), existing.exercises(), assignment.athleteId(), assignment.date(), existing.category(), existing.workoutType());
        Workout resolved = training.resolveTargets(input);
        return assignment.weekStart() == null || assignment.weekStart().isBlank() ? store.assign(id, resolved) : store.assignWeekly(id, resolved, assignment.weekStart());
    }
}
