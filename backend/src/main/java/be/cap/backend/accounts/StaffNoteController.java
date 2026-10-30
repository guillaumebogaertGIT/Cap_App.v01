package be.cap.backend.accounts;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Append-only private staff notes. No update/delete route and no athlete serialization. */
@RestController
@Profile("accounts")
@RequestMapping("/api/staff-notes")
class StaffNoteController {
    record Note(UUID id,UUID clientId,String assignmentId,UUID authorId,String author,Instant createdAt,String content){}
    record Input(UUID clientId,String assignmentId,String content,UUID requestId){}
    private final AccountService accounts;private final StaffPolicy policy;private final JdbcTemplate jdbc;private final AccountWorkoutService workouts;
    StaffNoteController(AccountService accounts,StaffPolicy policy,JdbcTemplate jdbc,AccountWorkoutService workouts){this.accounts=accounts;this.policy=policy;this.jdbc=jdbc;this.workouts=workouts;}
    private List<Note> read(UUID client){return jdbc.query("SELECT n.*,u.display_name AS author FROM staff_note n JOIN cap_user u ON u.id=n.author_id WHERE client_id=? ORDER BY created_at DESC,id DESC",
        (r,i)->new Note(r.getObject("id",UUID.class),client,r.getString("assignment_id"),r.getObject("author_id",UUID.class),r.getString("author"),r.getTimestamp("created_at").toInstant(),r.getString("content")),client);}
    @GetMapping
    ResponseEntity<List<Note>> list(Principal p,@RequestParam UUID clientId){var user=accounts.account(p.getName());policy.require(user,StaffPolicy.Permission.NOTE_VIEW);policy.requireClient(user,clientId.toString());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(read(clientId));}
    @PostMapping
    @Transactional(rollbackFor=Exception.class)
    ResponseEntity<Note> create(Principal p,@RequestBody Input input) throws java.io.IOException {
        var user=accounts.account(p.getName());policy.require(user,StaffPolicy.Permission.NOTE_CREATE);
        if(input.clientId()==null || input.requestId()==null || input.content()==null || input.content().isBlank() || input.content().length()>4000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een sporter en vul een notitie van maximaal 4000 tekens in.");
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,user.id());
        policy.require(user,StaffPolicy.Permission.NOTE_CREATE);
        policy.requireClient(user,input.clientId().toString());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",Integer.class,input.clientId())!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(input.assignmentId()!=null && workouts.assignments(user).stream().noneMatch(a->a.assignmentId().equals(input.assignmentId()) && a.planned().athleteId().equals(input.clientId().toString())))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Toewijzing niet gevonden.");
        var previous=jdbc.queryForList("SELECT id,client_id,assignment_id,content FROM staff_note WHERE author_id=? AND request_id=?",user.id(),input.requestId());
        UUID id;
        if(!previous.isEmpty()) {
            var old=previous.getFirst();if(!input.clientId().equals(old.get("client_id")) || !Objects.equals(input.assignmentId(),old.get("assignment_id")) || !input.content().trim().equals(old.get("content")))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze aanvraag is al voor een andere notitie gebruikt.");
            id=(UUID)old.get("id");
        } else {id=UUID.randomUUID();jdbc.update("INSERT INTO staff_note(id,client_id,assignment_id,author_id,created_at,content,request_id) VALUES(?,?,?,?,CURRENT_TIMESTAMP,?,?)",id,input.clientId(),input.assignmentId(),user.id(),input.content().trim(),input.requestId());policy.audit(user,"NOTE_CREATE",id.toString(),input.clientId().toString());}
        final UUID savedId=id;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(read(input.clientId()).stream().filter(n->n.id().equals(savedId)).findFirst().orElseThrow());
    }
}
