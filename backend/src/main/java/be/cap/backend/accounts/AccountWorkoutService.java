package be.cap.backend.accounts;

import be.cap.backend.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Reuse the tested workout/training rules, with SQL transactions and real ownership. */
@Service
@Profile("accounts")
@Transactional(rollbackFor = Exception.class)
public class AccountWorkoutService {
    private final JdbcTemplate jdbc;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public AccountWorkoutService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private void coach(AccountService.Account user) { if (!user.role().equals("COACH")) throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout niet gevonden."); }
    private boolean athlete(String id) {
        try { return jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE'", Long.class, UUID.fromString(id)) == 1; }
        catch (IllegalArgumentException | NullPointerException invalid) { return false; }
    }
    private WorkoutStore workouts(boolean lock) throws IOException {
        String json = jdbc.queryForObject("SELECT payload FROM workout_state WHERE id=1" + (lock ? " FOR UPDATE" : ""), String.class);
        return new WorkoutStore(mapper.readValue(json, WorkoutStore.State.class), state ->
            jdbc.update("UPDATE workout_state SET payload=? WHERE id=1", mapper.writeValueAsString(state)), this::athlete);
    }
    private TrainingStore training(String athleteId, boolean lock) throws IOException {
        if (athleteId == null || athleteId.isEmpty()) return new TrainingStore(List.of(), "", state -> {});
        if (!athlete(athleteId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kies een sporter uit de club.");
        UUID id=UUID.fromString(athleteId);
        String json=jdbc.queryForObject("SELECT payload FROM athlete_training WHERE athlete_id=?"+(lock?" FOR UPDATE":""),String.class,id);
        return new TrainingStore(Arrays.asList(mapper.readValue(json,TrainingStore.Session[].class)),athleteId,
            state->jdbc.update("UPDATE athlete_training SET payload=? WHERE athlete_id=?",mapper.writeValueAsString(state),id));
    }
    private WorkoutStore.DatedWorkout assignment(WorkoutStore store,String id,AccountService.Account user) {
        var assignment=store.assignment(id);
        if (!user.role().equals("COACH") && !user.id().toString().equals(assignment.planned().athleteId())) throw missing();
        return assignment;
    }
    public List<Workout> list(AccountService.Account user) throws IOException {
        var store=workouts(false);
        return user.role().equals("COACH")?store.list():store.assignments().stream().map(WorkoutStore.DatedWorkout::planned).filter(w->user.id().toString().equals(w.athleteId())).toList();
    }
    public List<WorkoutStore.DatedWorkout> assignments(AccountService.Account user) throws IOException {
        return workouts(false).assignments().stream().filter(a->user.role().equals("COACH")||user.id().toString().equals(a.planned().athleteId())).toList();
    }
    public Workout save(String id,Workout input,String week,AccountService.Account user) throws IOException {
        coach(user); var store=workouts(true);
        if (input==null) throw missing();
        if (id!=null) {
            Workout old=store.list().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow(this::missing);
            input=new Workout(id,input.name(),input.description(),input.exercises(),old.athleteId(),old.assignedDate(),input.category(),input.workoutType());
        }
        Workout resolved=training(input.athleteId(),false).resolveTargets(input);
        if(id!=null) return store.update(id,resolved);
        return week==null||week.isBlank()?store.create(resolved):store.createWeekly(resolved,week);
    }
    public void delete(String id,AccountService.Account user) throws IOException { coach(user); workouts(true).delete(id); }
    public Workout assign(String id,String athleteId,String date,String week,AccountService.Account user) throws IOException {
        coach(user); var store=workouts(true);
        Workout old=store.list().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow(this::missing);
        Workout input=new Workout(id,old.name(),old.description(),old.exercises(),athleteId,date,old.category(),old.workoutType());
        Workout resolved=training(athleteId,false).resolveTargets(input);
        return week==null||week.isBlank()?store.assign(id,resolved):store.assignWeekly(id,resolved,week);
    }
    private void unfinished(WorkoutStore.DatedWorkout assignment) throws IOException {
        if(training(assignment.planned().athleteId(),false).history().stream().anyMatch(s->assignment.assignmentId().equals(s.assignmentId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze workout is al afgerond.");
    }
    public WorkoutStore.DatedWorkout schedule(String id,String date,AccountService.Account user) throws IOException {
        var store=workouts(true); var selected=assignment(store,id,user); unfinished(selected); return store.schedule(id,date);
    }
    public WorkoutStore.DatedWorkout editAssignment(String id,Workout input,AccountService.Account user) throws IOException {
        coach(user); var store=workouts(true); var selected=assignment(store,id,user); unfinished(selected);
        Workout old=selected.planned();
        return store.updateAssignment(id,training(old.athleteId(),false).resolveTargets(new Workout(old.id(),input.name(),input.description(),input.exercises(),old.athleteId(),old.assignedDate(),input.category(),input.workoutType())));
    }
    public WorkoutStore.DatedWorkout note(String id,String note,AccountService.Account user) throws IOException {
        coach(user);var store=workouts(true);assignment(store,id,user);return store.updateNote(id,note);
    }
    public void removeAssignment(String id,AccountService.Account user) throws IOException {
        coach(user);var store=workouts(true);unfinished(assignment(store,id,user));store.deleteAssignment(id);
    }
    private String historyOwner(AccountService.Account user,String athleteId) {
        if(user.role().equals("ATHLETE")) {
            if(athleteId!=null&&!athleteId.equals(user.id().toString())) throw missing();
            return user.id().toString();
        }
        return athleteId==null?"":athleteId;
    }
    public List<TrainingStore.Session> history(AccountService.Account user,String athleteId) throws IOException { return training(historyOwner(user,athleteId),false).history(); }
    public List<TrainingStore.Record> records(AccountService.Account user,String athleteId) throws IOException { return training(historyOwner(user,athleteId),false).records(); }
    public record SessionWorkout(UUID sessionId,String title,String date,String start,Workout workout) {}
    public List<SessionWorkout> sessionWorkouts(AccountService.Account user) {
        if(!user.role().equals("ATHLETE")) return List.of();
        return jdbc.query("""
            SELECT s.* FROM club_session s JOIN session_booking b ON b.session_id=s.id
            WHERE b.athlete_id=? AND b.cancelled=FALSE AND s.workout_snapshot IS NOT NULL
            AND (s.session_type<>'Privé' OR EXISTS(SELECT 1 FROM session_recipient r WHERE r.session_id=s.id AND r.athlete_id=?))
            ORDER BY s.session_date,s.start_time
            """,(row,index)->new SessionWorkout(row.getObject("id",UUID.class),row.getString("title"),row.getDate("session_date").toString(),row.getTime("start_time").toString(),mapper.readValue(row.getString("workout_snapshot"),Workout.class)),user.id(),user.id());
    }
    public void attach(UUID sessionId,String workoutId,AccountService.Account user) throws IOException {
        coach(user);
        // Always lock shared workout state before a session, matching completion's lock order.
        var store=workouts(true);
        Workout saved=store.list().stream().filter(w->w.id().equals(workoutId)).findFirst().orElseThrow(this::missing);
        var exists=jdbc.query("SELECT session_type,session_date,start_time FROM club_session WHERE id=? FOR UPDATE",(row,index)->Map.of("type",row.getString(1),"date",row.getDate(2).toString(),"start",row.getTime(3).toString()),sessionId);
        if(exists.isEmpty()) throw missing();
        var session=exists.getFirst();
        if(session.get("type").equals("Open gym")||!java.time.LocalDateTime.parse(session.get("date")+"T"+session.get("start")).isAfter(java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Brussels"))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een toekomstige groepsles of privésessie.");
        if(saved.exercises().stream().anyMatch(e->"percent".equals(e.loadMode())||e.setPlan()!=null&&e.setPlan().stream().anyMatch(s->"percent".equals(s.loadMode()))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Een gedeelde sessie vereist vaste gewichten; percentages horen bij een persoonlijke workout.");
        Workout snapshot=new Workout(saved.id(),saved.name(),saved.description(),saved.exercises(),"","",saved.category(),saved.workoutType());
        jdbc.update("UPDATE club_session SET workout_snapshot=? WHERE id=?",mapper.writeValueAsString(snapshot),sessionId);
    }
    public TrainingStore.Session finish(TrainingStore.Completion input,int version,AccountService.Account user) throws IOException {
        if(!user.role().equals("ATHLETE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var store=workouts(true);var training=training(user.id().toString(),true);
        if(input==null) throw missing();
        var existing=training.history().stream().filter(s->s.id().equals(input.id())).findFirst();
        if(existing.isPresent()) return existing.get();
        var draft=active(user,true);
        if(draft==null||!draft.id().equals(input.id())) throw missing();
        if(version!=draft.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze training is op een ander scherm gewijzigd. Vernieuw de pagina.");
        var trusted=new TrainingStore.Completion(draft.id(),draft.planned().id(),draft.startedAt(),java.time.Instant.now().toString(),input.exercises(),draft.assignmentId());
        var finished=training.finish(trusted,draft.planned());
        jdbc.update("DELETE FROM training_draft WHERE athlete_id=?",user.id());
        return finished;
    }
    public record Draft(String id,Workout planned,String startedAt,String assignmentId,List<TrainingStore.ExerciseResult> exercises,int version) {}
    private Draft active(AccountService.Account user,boolean lock) throws IOException {
        if(!user.role().equals("ATHLETE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var rows=jdbc.query("SELECT payload FROM training_draft WHERE athlete_id=?"+(lock?" FOR UPDATE":""),(row,index)->row.getString(1),user.id());
        return rows.isEmpty()?null:mapper.readValue(rows.getFirst(),Draft.class);
    }
    public Draft active(AccountService.Account user) throws IOException { return active(user,false); }
    public Draft start(String assignmentId,AccountService.Account user) throws IOException {
        if(!user.role().equals("ATHLETE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var store=workouts(true);training(user.id().toString(),true);
        var old=active(user,true);if(old!=null) return old;
        Workout planned;
        if(assignmentId!=null&&assignmentId.startsWith("session:")) {
            String sessionId=assignmentId.substring(8);
            planned=sessionWorkouts(user).stream().filter(s->s.sessionId().toString().equals(sessionId)).map(SessionWorkout::workout).findFirst().orElseThrow(this::missing);
        } else {
            if(assignmentId==null) throw missing();
            planned=assignment(store,assignmentId,user).planned();
        }
        var exercises=new ArrayList<TrainingStore.ExerciseResult>();
        for(int index=0;index<planned.exercises().size();index++) {
            var exercise=planned.exercises().get(index);var sets=new ArrayList<TrainingStore.SetResult>();
            for(int set=0;set<exercise.sets();set++) sets.add(new TrainingStore.SetResult(exercise.setPlan()==null?exercise.reps():exercise.setPlan().get(set).reps(),null,null));
            exercises.add(new TrainingStore.ExerciseResult(index,sets));
        }
        var draft=new Draft(UUID.randomUUID().toString(),planned,java.time.Instant.now().toString(),assignmentId,exercises,0);
        jdbc.update("INSERT INTO training_draft(id,athlete_id,payload) VALUES(?,?,?)",UUID.fromString(draft.id()),user.id(),mapper.writeValueAsString(draft));
        return draft;
    }
    public Draft progress(String id,int version,List<TrainingStore.ExerciseResult> exercises,AccountService.Account user) throws IOException {
        var draft=active(user,true);
        if(draft==null||!draft.id().equals(id)) throw missing();
        if(version!=draft.version()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze training is op een ander scherm gewijzigd. Vernieuw de pagina.");
        if(exercises==null||exercises.size()!=draft.planned().exercises().size()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Controleer de oefeningen.");
        Set<Integer> indexes=new HashSet<>();
        for(var exercise:exercises) {
            if(exercise==null||exercise.exerciseIndex()<0||exercise.exerciseIndex()>=exercises.size()||!indexes.add(exercise.exerciseIndex())||exercise.sets()==null||exercise.sets().size()>100)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Controleer de sets.");
            for(var set:exercise.sets()) if(set==null||set.reps()<0||set.reps()>1000||set.kg()!=null&&(!Double.isFinite(set.kg())||set.kg()<0||set.kg()>2000)||set.rir()!=null&&(set.rir()<0||set.rir()>10))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Controleer gewicht, herhalingen en RIR.");
        }
        var updated=new Draft(draft.id(),draft.planned(),draft.startedAt(),draft.assignmentId(),exercises,version+1);
        jdbc.update("UPDATE training_draft SET payload=? WHERE athlete_id=?",mapper.writeValueAsString(updated),user.id());return updated;
    }
    public void discard(AccountService.Account user) throws IOException { active(user,true);jdbc.update("DELETE FROM training_draft WHERE athlete_id=?",user.id()); }
}
