package be.cap.backend.accounts;

import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Shared owner agenda; display names and titles never grant access. */
@RestController
@Profile("accounts")
@RequestMapping("/api/owner-agenda")
public class OwnerAgendaController {
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final StaffPolicy policy;
    public OwnerAgendaController(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;}
    private AccountService.Account owner(Principal p){
        var actor=accounts.account(p.getName());
        if(!policy.role(actor).equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return actor;
    }
    public record Task(UUID id,String title,String notes,LocalDate date,boolean done,int version,List<UUID> assignees){}
    private Task task(java.sql.ResultSet r,int index) throws java.sql.SQLException {
        return new Task(r.getObject("id",UUID.class),r.getString("title"),r.getString("notes"),r.getObject("due_date",LocalDate.class),r.getBoolean("done"),r.getInt("version"),
            jdbc.query("SELECT user_id FROM owner_task_assignee WHERE task_id=? ORDER BY user_id",(a,j)->a.getObject(1,UUID.class),r.getObject("id",UUID.class)));
    }
    private List<Task> tasks(LocalDate start,LocalDate end){
        return jdbc.query("SELECT * FROM owner_task WHERE due_date>=? AND due_date<? ORDER BY due_date,updated_at,id",this::task,start,end);
    }
    @GetMapping("/overview")
    @Transactional(readOnly=true)
    public ResponseEntity<Map<String,Object>> overview(Principal p){
        owner(p);
        var today=LocalDate.now(ZoneId.of("Europe/Brussels"));
        var dayStart=java.sql.Timestamp.from(today.atStartOfDay(ZoneId.of("Europe/Brussels")).toInstant());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
            "today",today,
            "tasks",jdbc.query("SELECT * FROM owner_task WHERE due_date=? OR (due_date<? AND (done=FALSE OR updated_at>=?)) ORDER BY CASE WHEN due_date=? THEN 0 ELSE 1 END,due_date,id LIMIT 8",this::task,today,today,dayStart,today),
            "totalCount",jdbc.queryForObject("SELECT COUNT(*) FROM owner_task WHERE due_date=? OR (due_date<? AND (done=FALSE OR updated_at>=?))",Long.class,today,today,dayStart),
            "dueCount",jdbc.queryForObject("SELECT COUNT(*) FROM owner_task WHERE done=FALSE AND due_date<=?",Long.class,today),
            "overdueCount",jdbc.queryForObject("SELECT COUNT(*) FROM owner_task WHERE done=FALSE AND due_date<?",Long.class,today),
            "owners",jdbc.queryForList("SELECT id,display_name AS name FROM cap_user WHERE active=TRUE AND staff_role='OWNER' AND role='COACH' ORDER BY display_name")));
    }
    @GetMapping
    @Transactional(readOnly=true)
    public ResponseEntity<Map<String,Object>> list(Principal p,@RequestParam String month){
        owner(p);YearMonth selected;
        try{selected=YearMonth.parse(month);if(selected.getYear()<1900||selected.getYear()>2200)throw new IllegalArgumentException();}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een geldige maand.");}
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("tasks",tasks(selected.atDay(1),selected.plusMonths(1).atDay(1)),
            "owners",jdbc.queryForList("SELECT id,display_name AS name FROM cap_user WHERE active=TRUE AND staff_role='OWNER' AND role='COACH' ORDER BY display_name")));
    }
    @PutMapping("/tasks/{id}")
    @Transactional
    public Task save(Principal p,@PathVariable UUID id,@RequestBody Task input){
        var actor=owner(p);
        // Serialize owner task changes with staff changes and validate current assignee roles.
        jdbc.query("SELECT id FROM cap_user WHERE role='COACH' ORDER BY id FOR UPDATE",(r,i)->r.getObject(1));
        owner(p);
        if(input.title()==null||input.title().isBlank()||input.title().trim().length()>160||input.notes()==null||input.notes().length()>2000
            ||input.date()==null||input.date().getYear()<1900||input.date().getYear()>2200||input.assignees()==null||input.assignees().isEmpty()
            ||input.assignees().size()>10||input.assignees().contains(null)||new HashSet<>(input.assignees()).size()!=input.assignees().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vul een titel, datum en verantwoordelijke in.");
        for(var assignee:input.assignees())if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='COACH' AND staff_role='OWNER' AND active=TRUE",Integer.class,assignee)!=1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een actieve admin.");
        var versions=jdbc.query("SELECT version FROM owner_task WHERE id=? FOR UPDATE",(r,i)->r.getInt(1),id);
        int version=versions.isEmpty()?0:versions.getFirst()+1;
        if(versions.isEmpty()){
            if(input.version()!=-1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze taak bestaat niet meer.");
            jdbc.update("INSERT INTO owner_task(id,title,notes,due_date,done,created_by) VALUES(?,?,?,?,?,?)",id,input.title().trim(),input.notes().trim(),input.date(),input.done(),actor.id());
        }else{
            if(input.version()!=versions.getFirst())throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze taak is ondertussen gewijzigd. Vernieuw de agenda en probeer opnieuw.");
            jdbc.update("UPDATE owner_task SET title=?,notes=?,due_date=?,done=?,version=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.title().trim(),input.notes().trim(),input.date(),input.done(),version,id);
            jdbc.update("DELETE FROM owner_task_assignee WHERE task_id=?",id);
        }
        for(var assignee:input.assignees())jdbc.update("INSERT INTO owner_task_assignee(task_id,user_id) VALUES(?,?)",id,assignee);
        policy.audit(actor,"OWNER_TASK_SAVE",id.toString(),input.done()?"DONE":"OPEN");
        return new Task(id,input.title().trim(),input.notes().trim(),input.date(),input.done(),version,input.assignees());
    }
    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(Principal p,@PathVariable UUID id,@RequestParam int version){
        var actor=owner(p);
        if(version<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ongeldige taakversie.");
        jdbc.query("SELECT id FROM cap_user WHERE role='COACH' ORDER BY id FOR UPDATE",(r,i)->r.getObject(1));
        owner(p);
        var versions=jdbc.query("SELECT version FROM owner_task WHERE id=? FOR UPDATE",(r,i)->r.getInt(1),id);
        if(versions.isEmpty())return;
        if(versions.getFirst()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze taak is ondertussen gewijzigd. Vernieuw de agenda voordat je ze verwijdert.");
        jdbc.update("DELETE FROM owner_task WHERE id=?",id);
        policy.audit(actor,"OWNER_TASK_DELETE",id.toString(),"DELETED");
    }
    @GetMapping("/notifications")
    public ResponseEntity<Map<String,Object>> notifications(Principal p){
        var actor=owner(p);
        var items=jdbc.queryForList("""
            SELECT e.id,u.display_name AS athlete,e.workout_name AS workout,e.completed_at AS completed,
                CASE WHEN r.event_id IS NULL THEN FALSE ELSE TRUE END AS read
            FROM owner_workout_event e JOIN cap_user u ON u.id=e.athlete_id
            LEFT JOIN owner_event_read r ON r.event_id=e.id AND r.user_id=?
            ORDER BY e.completed_at DESC,e.id LIMIT 50
            """,actor.id());
        var unread=jdbc.queryForObject("SELECT COUNT(*) FROM owner_workout_event e WHERE NOT EXISTS (SELECT 1 FROM owner_event_read r WHERE r.event_id=e.id AND r.user_id=?)",Long.class,actor.id());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("items",items,"unread",unread));
    }
    @PutMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void read(Principal p,@PathVariable UUID id){
        var actor=owner(p);
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),actor.id());
        owner(p);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM owner_workout_event WHERE id=?",Integer.class,id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM owner_event_read WHERE event_id=? AND user_id=?",Integer.class,id,actor.id())==0)
            jdbc.update("INSERT INTO owner_event_read(event_id,user_id) VALUES(?,?)",id,actor.id());
    }
}
