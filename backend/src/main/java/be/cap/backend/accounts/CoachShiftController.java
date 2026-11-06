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

@RestController
@Profile("accounts")
@RequestMapping("/api/coach-shifts")
public class CoachShiftController {
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;
    public CoachShiftController(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;}
    private AccountService.Account actor(Principal p,boolean write){
        var user=accounts.account(p.getName());var role=policy.role(user);
        if(write?!role.equals("OWNER"):!Set.of("OWNER","MANAGER","INSTRUCTOR").contains(role))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }
    public record Shift(UUID id,UUID coachId,String coach,LocalDate date,LocalTime start,LocalTime end,String note,int version){}
    public record Input(UUID coachId,LocalDate date,LocalTime start,LocalTime end,String note,int version){}
    private List<Shift> query(String where,Object... args){return jdbc.query("SELECT s.*,u.display_name FROM coach_shift s JOIN cap_user u ON u.id=s.coach_id "+where,
        (r,i)->new Shift(r.getObject("id",UUID.class),r.getObject("coach_id",UUID.class),r.getString("display_name"),r.getObject("shift_date",LocalDate.class),r.getObject("start_time",LocalTime.class),r.getObject("end_time",LocalTime.class),r.getString("note"),r.getInt("version")),args);}
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private void upcoming(LocalDate day,LocalTime time){if(!LocalDateTime.of(day,time).isAfter(LocalDateTime.now(ZoneId.of("Europe/Brussels"))))throw invalid("Deze werkuren zijn al gestart. Kies een toekomstig tijdstip.");}
    @GetMapping
    @Transactional(readOnly=true)
    public ResponseEntity<Map<String,Object>> list(Principal p,@RequestParam LocalDate from,@RequestParam LocalDate through){
        var user=actor(p,false);boolean owner=policy.role(user).equals("OWNER");
        if(through.isBefore(from)||through.isAfter(from.plusDays(370)))throw invalid("Kies een periode van maximaal een jaar.");
        var shifts=owner?query("WHERE shift_date>=? AND shift_date<=? ORDER BY shift_date,start_time,s.id",from,through)
            :query("WHERE shift_date>=? AND shift_date<=? AND coach_id=? ORDER BY shift_date,start_time,s.id",from,through,user.id());
        var coaches=owner?jdbc.queryForList("SELECT id,display_name AS name FROM cap_user WHERE active=TRUE AND role='COACH' AND staff_role IN ('OWNER','MANAGER','INSTRUCTOR') ORDER BY display_name")
            :List.of(Map.<String,Object>of("id",user.id(),"name",user.name()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("shifts",shifts,"coaches",coaches));
    }
    private void lock(){jdbc.query("SELECT id FROM cap_user WHERE role='COACH' ORDER BY id FOR UPDATE",(r,i)->r.getObject(1));}
    @PutMapping("/{id}")
    @Transactional
    public Shift save(Principal p,@PathVariable UUID id,@RequestBody Input input){
        var user=actor(p,true);lock();actor(p,true);
        if(input.coachId()==null||input.date()==null||input.start()==null||input.end()==null||!input.end().isAfter(input.start())||input.note()==null||input.note().length()>500)throw invalid("Controleer coach, datum, tijden en notitie.");
        upcoming(input.date(),input.start());
        if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND active=TRUE AND role='COACH' AND staff_role IN ('OWNER','MANAGER','INSTRUCTOR')",Integer.class,input.coachId())!=1)throw invalid("Kies een actieve coach.");
        var existing=query("WHERE s.id=? FOR UPDATE",id);int version=0;
        if(existing.isEmpty()){if(input.version()!=-1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze werkuren bestaan niet meer.");}
        else{var old=existing.getFirst();upcoming(old.date(),old.start());if(old.version()!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"De planning is ondertussen gewijzigd. Vernieuw en probeer opnieuw.");version=old.version()+1;}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM coach_shift WHERE coach_id=? AND shift_date=? AND start_time<? AND end_time>? AND id<>?",Integer.class,input.coachId(),input.date(),input.end(),input.start(),id)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Deze coach heeft al overlappende werkuren. Pas de tijden aan.");
        if(existing.isEmpty())jdbc.update("INSERT INTO coach_shift(id,coach_id,shift_date,start_time,end_time,note) VALUES(?,?,?,?,?,?)",id,input.coachId(),input.date(),input.start(),input.end(),input.note().trim());
        else jdbc.update("UPDATE coach_shift SET coach_id=?,shift_date=?,start_time=?,end_time=?,note=?,version=? WHERE id=?",input.coachId(),input.date(),input.start(),input.end(),input.note().trim(),version,id);
        policy.audit(user,"COACH_SHIFT_SAVE",id.toString(),input.coachId().toString());return query("WHERE s.id=?",id).getFirst();
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(Principal p,@PathVariable UUID id,@RequestParam int version){
        var user=actor(p,true);lock();actor(p,true);var existing=query("WHERE s.id=? FOR UPDATE",id);if(existing.isEmpty())return;
        var old=existing.getFirst();upcoming(old.date(),old.start());
        if(old.version()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"De planning is ondertussen gewijzigd. Vernieuw voordat je verwijdert.");
        jdbc.update("DELETE FROM coach_shift WHERE id=?",id);policy.audit(user,"COACH_SHIFT_DELETE",id.toString(),old.coachId().toString());
    }
}
