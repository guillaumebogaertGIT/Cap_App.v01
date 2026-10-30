package be.cap.backend.accounts;

import be.cap.backend.*;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("accounts")
class AccountWorkoutController {
    private final AccountService accounts;
    private final AccountWorkoutService service;
    AccountWorkoutController(AccountService accounts,AccountWorkoutService service) { this.accounts=accounts;this.service=service; }
    private AccountService.Account user(Principal principal) { return accounts.account(principal.getName()); }
    @GetMapping("/api/workouts") List<Workout> list(Principal p) throws IOException { return service.list(user(p)); }
    @GetMapping("/api/workout-assignments") List<AccountWorkoutService.AssignmentView> assignments(Principal p) throws IOException { return service.assignmentViews(user(p)); }
    @PostMapping("/api/workouts") @ResponseStatus(HttpStatus.CREATED)
    Workout create(Principal p,@RequestBody Workout input,@RequestParam(required=false) String weekStart) throws IOException { return service.save(null,input,weekStart,user(p)); }
    @PutMapping("/api/workouts/{id}") Workout update(Principal p,@PathVariable String id,@RequestBody Workout input) throws IOException { return service.save(id,input,null,user(p)); }
    @DeleteMapping("/api/workouts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Principal p,@PathVariable String id) throws IOException { service.delete(id,user(p)); }
    @PostMapping("/api/workouts/{id}/assignment") Workout assign(Principal p,@PathVariable String id,@RequestBody WorkoutController.Assignment input) throws IOException {
        return service.assign(id,input.athleteId(),input.date(),input.weekStart(),user(p));
    }
    @PutMapping("/api/workout-assignments/{id}/schedule") WorkoutStore.DatedWorkout schedule(Principal p,@PathVariable String id,@RequestBody WorkoutController.Schedule input) throws IOException { return service.schedule(id,input.date(),user(p)); }
    @PutMapping("/api/workout-assignments/{id}/note") WorkoutStore.DatedWorkout note(Principal p,@PathVariable String id,@RequestBody WorkoutController.Note input) throws IOException { return service.note(id,input.note(),user(p)); }
    @PutMapping("/api/workout-assignments/{id}") WorkoutStore.DatedWorkout editAssignment(Principal p,@PathVariable String id,@RequestBody Workout input) throws IOException { return service.editAssignment(id,input,user(p)); }
    @DeleteMapping("/api/workout-assignments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(Principal p,@PathVariable String id) throws IOException { service.removeAssignment(id,user(p)); }
    @GetMapping("/api/training") List<TrainingStore.Session> history(Principal p,@RequestParam(required=false) String athleteId) throws IOException { return service.history(user(p),athleteId); }
    @GetMapping("/api/records") List<TrainingStore.Record> records(Principal p,@RequestParam(required=false) String athleteId) throws IOException { return service.records(user(p),athleteId); }
    record Finish(String id,int version,List<TrainingStore.ExerciseResult> exercises) {}
    @PostMapping("/api/training") TrainingStore.Session finish(Principal p,@RequestBody Finish input) throws IOException {
        return service.finish(new TrainingStore.Completion(input.id(),null,null,null,input.exercises()),input.version(),user(p));
    }
    record Start(String assignmentId) {}
    record Progress(String id,int version,List<TrainingStore.ExerciseResult> exercises) {}
    @GetMapping("/api/training/active") org.springframework.http.ResponseEntity<AccountWorkoutService.Draft> active(Principal p) throws IOException {
        var draft=service.active(user(p));return draft==null?org.springframework.http.ResponseEntity.noContent().build():org.springframework.http.ResponseEntity.ok(draft);
    }
    @PostMapping("/api/training/active") AccountWorkoutService.Draft start(Principal p,@RequestBody Start input) throws IOException { return service.start(input.assignmentId(),user(p)); }
    @PutMapping("/api/training/active") AccountWorkoutService.Draft progress(Principal p,@RequestBody Progress input) throws IOException { return service.progress(input.id(),input.version(),input.exercises(),user(p)); }
    @DeleteMapping("/api/training/active") @ResponseStatus(HttpStatus.NO_CONTENT) void discard(Principal p) throws IOException { service.discard(user(p)); }
    @GetMapping("/api/session-workouts") List<AccountWorkoutService.SessionWorkout> sessions(Principal p) { return service.sessionWorkouts(user(p)); }
    record Attachment(String workoutId) {}
    @PutMapping("/api/club/sessions/{id}/workout") @ResponseStatus(HttpStatus.NO_CONTENT)
    void attach(Principal p,@PathVariable UUID id,@RequestBody Attachment input) throws IOException { service.attach(id,input.workoutId(),user(p)); }
}
