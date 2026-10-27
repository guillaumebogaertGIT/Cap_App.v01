package be.cap.backend;

import be.cap.backend.accounts.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("accounts")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:real-workouts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=", "cap.accounts.bootstrap-email=workout-coach@example.test","cap.accounts.bootstrap-password=Coach-test-password-123"})
class AccountWorkoutServiceTests {
    @org.springframework.test.context.DynamicPropertySource
    static void postgres(org.springframework.test.context.DynamicPropertyRegistry properties) { PostgresTestDatabase.configure(properties,"workouts"); }
    @Autowired AccountService accounts;
    @Autowired AccountWorkoutService workouts;
    @Autowired PlanningService planning;
    @Autowired AccountExerciseService exercises;
    AccountService.Account coach(){return accounts.account("workout-coach@example.test");}
    AccountService.Account athlete(){String email=UUID.randomUUID()+"@example.test";return accounts.accept(accounts.invite(email,coach().email()).token(),"Test sporter","Athlete-test-password-123");}
    String date(){return LocalDate.now(ZoneId.of("Europe/Brussels")).plusDays(2).toString();}
    Workout template(String name){return new Workout(null,name,"",List.of(new Workout.Exercise("Squat",1,5,"fixed",40.0,null,null)),"","","","lower-body");}
    TrainingStore.Completion completion(AccountWorkoutService.Draft draft,double kg){return new TrainingStore.Completion(draft.id(),"forged-client-id",null,null,List.of(new TrainingStore.ExerciseResult(0,List.of(new TrainingStore.SetResult(5,kg)))));}
    @Test void assignmentsDraftsHistoryAndRecordsBelongToRealAthlete() throws Exception {
        var first=athlete();var other=athlete();
        var saved=workouts.save(null,template("Own workout"),null,coach());
        workouts.assign(saved.id(),first.id().toString(),date(),null,coach());
        var assignment=workouts.assignments(first).getFirst();
        assertTrue(workouts.assignments(other).isEmpty());
        assertThrows(ResponseStatusException.class,()->workouts.start(assignment.assignmentId(),other));
        assertThrows(ResponseStatusException.class,()->workouts.save(null,template("Forbidden"),null,first));
        var draft=workouts.start(assignment.assignmentId(),first);
        assertEquals(draft.id(),workouts.start(assignment.assignmentId(),first).id());
        var progress=workouts.progress(draft.id(),draft.version(),List.of(new TrainingStore.ExerciseResult(0,List.of(new TrainingStore.SetResult(5,50.0,2)))),first);
        assertEquals(50.0,workouts.active(first).exercises().getFirst().sets().getFirst().kg());
        assertThrows(ResponseStatusException.class,()->workouts.progress(draft.id(),draft.version(),progress.exercises(),first));
        // Changing the prescription after start cannot rewrite the performed plan.
        Workout changed=new Workout(saved.id(),"Edited", "",List.of(new Workout.Exercise("Hinge",1,8)),"","");
        workouts.editAssignment(assignment.assignmentId(),changed,coach());
        var finished=workouts.finish(completion(progress,50),progress.version(),first);
        assertEquals("Squat",finished.planned().exercises().getFirst().name());
        assertEquals(first.id().toString(),finished.athleteId());
        assertEquals(finished.id(),workouts.finish(completion(progress,50),progress.version(),first).id());
        assertNull(workouts.active(first));assertEquals(1,workouts.history(first,null).size());
        assertEquals(50.0,workouts.records(first,null).getFirst().kg());assertTrue(workouts.records(other,null).isEmpty());
        assertThrows(ResponseStatusException.class,()->workouts.history(other,first.id().toString()));
        assertThrows(ResponseStatusException.class,()->workouts.finish(completion(progress,50),progress.version(),other));
    }
    @Test void bookedSessionSnapshotAndPercentageTargetsUseCorrectOwners() throws Exception {
        var first=athlete();var other=athlete();
        var saved=workouts.save(null,template("Shared workout"),null,coach());
        var session=planning.save(null,new PlanningService.Input("Private",date(),"18:00","19:00",coach().id(),"CAP",1,"Privé","","",List.of(first.id()),1),coach()).getFirst();
        workouts.attach(session.id(),saved.id(),coach());
        assertEquals(1,workouts.sessionWorkouts(first).size());assertTrue(workouts.sessionWorkouts(other).isEmpty());
        assertThrows(ResponseStatusException.class,()->workouts.start("session:"+session.id(),other));
        var draft=workouts.start("session:"+session.id(),first);
        workouts.finish(completion(draft,100),draft.version(),first);
        var percent=new Workout(null,"80 percent","",List.of(new Workout.Exercise("Squat",1,5,"percent",80.0,null,null)),first.id().toString(),date());
        var resolved=workouts.save(null,percent,null,coach());assertEquals(80.0,resolved.exercises().getFirst().targetKg());
        assertThrows(ResponseStatusException.class,()->workouts.assign(resolved.id(),other.id().toString(),date(),null,coach()));
        planning.book(session.id(),first,true);assertTrue(workouts.sessionWorkouts(first).isEmpty());
    }
    @Test void exerciseChangesAreSharedWithoutRewritingWorkoutSnapshots() throws Exception {
        var input=new ExerciseStore.Exercise(null,"CAP DB "+UUID.randomUUID(),"Lower Body","Squat","","", "",false);
        var created=exercises.save(null,input);assertTrue(exercises.list().stream().anyMatch(e->e.id().equals(created.id())));
        exercises.archive(created.id(),true);assertTrue(exercises.list().stream().filter(e->e.id().equals(created.id())).findFirst().orElseThrow().archived());
        exercises.archive(created.id(),false);assertFalse(exercises.list().stream().filter(e->e.id().equals(created.id())).findFirst().orElseThrow().archived());
    }
}
