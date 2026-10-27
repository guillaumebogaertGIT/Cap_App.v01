package be.cap.backend;

import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.context.annotation.Profile("!accounts")
@CrossOrigin(origins = "http://127.0.0.1:5500")
public class TrainingController {
    private final TrainingStore training;
    private final WorkoutStore workouts;

    public TrainingController(TrainingStore training, WorkoutStore workouts) {
        this.training = training;
        this.workouts = workouts;
    }

    @GetMapping("/api/training")
    public List<TrainingStore.Session> history() {
        return training.history();
    }

    @GetMapping("/api/records")
    public List<TrainingStore.Record> records() {
        return training.records();
    }

    @PostMapping("/api/training")
    public TrainingStore.Session finish(@RequestBody TrainingStore.Completion completion) throws IOException {
        return training.finish(completion, findPlannedWorkout(completion));
    }

    private Workout findPlannedWorkout(TrainingStore.Completion completion) {
        return completion.assignmentId() == null
            ? workouts.list().stream().filter(w -> w.id().equals(completion.workoutId())).findFirst().orElse(null)
            : workouts.assignments().stream().filter(a -> a.assignmentId().equals(completion.assignmentId()))
                .map(WorkoutStore.DatedWorkout::planned).findFirst().orElse(null);
    }
}
