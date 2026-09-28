package be.cap.backend;

import java.util.List;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://127.0.0.1:5500")
@RestController
public class WorkoutController {
    private final WorkoutStore store;
    private final TrainingStore training;

    public WorkoutController(WorkoutStore store, TrainingStore training) { this.store = store; this.training = training; }

    @GetMapping("/api/workouts")
    public List<Workout> getWorkouts() {
        return store.list();
    }

    @PostMapping("/api/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    public Workout createWorkout(@RequestBody Workout workout) throws IOException {
        return store.create(training.resolveTargets(workout));
    }
}
