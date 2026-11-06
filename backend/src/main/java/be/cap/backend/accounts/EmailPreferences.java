package be.cap.backend.accounts;

import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("accounts") @RequestMapping("/api/email-preferences")
public class EmailPreferences {
    public record Preferences(boolean assignments,boolean reminders,boolean deliveryEnabled){}
    public record Input(Boolean assignments,Boolean reminders){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final CapEmail mail;
    public EmailPreferences(JdbcTemplate jdbc,AccountService accounts,CapEmail mail){this.jdbc=jdbc;this.accounts=accounts;this.mail=mail;}
    public static boolean wants(JdbcTemplate jdbc,UUID id,boolean assignment){var values=jdbc.query("SELECT "+(assignment?"assignments":"reminders")+" FROM email_preferences WHERE user_id=?",(r,i)->r.getBoolean(1),id);return values.isEmpty()||values.getFirst();}
    @GetMapping public ResponseEntity<Preferences> get(Principal p){var id=accounts.account(p.getName()).id();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Preferences(wants(jdbc,id,true),wants(jdbc,id,false),mail.enabled));}
    @PutMapping @Transactional public Preferences save(Principal p,@RequestBody Input input){if(input.assignments()==null||input.reminders()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);var id=accounts.account(p.getName()).id();jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),id);if(jdbc.update("UPDATE email_preferences SET assignments=?,reminders=? WHERE user_id=?",input.assignments(),input.reminders(),id)==0)jdbc.update("INSERT INTO email_preferences(user_id,assignments,reminders) VALUES(?,?,?)",id,input.assignments(),input.reminders());return new Preferences(input.assignments(),input.reminders(),mail.enabled);}
    @Scheduled(fixedDelay=60000,initialDelay=60000) @Transactional
    public void reminders(){
        if(!mail.enabled)return;
        jdbc.query("SELECT id FROM workout_state WHERE id=1 FOR UPDATE",(r,i)->r.getInt(1));
        var now=Instant.now();var zone=ZoneId.of("Europe/Brussels");var day=now.atZone(zone).toLocalDate();
        var rows=jdbc.queryForList("SELECT s.id,s.session_date,s.start_time,b.athlete_id,u.email FROM club_session s JOIN session_booking b ON b.session_id=s.id JOIN cap_user u ON u.id=b.athlete_id WHERE b.cancelled=FALSE AND u.active=TRUE AND s.session_date>=? AND s.session_date<=?",day,day.plusDays(1));
        for(var row:rows){var starts=LocalDateTime.of(((java.sql.Date)row.get("session_date")).toLocalDate(),((java.sql.Time)row.get("start_time")).toLocalTime()).atZone(zone).toInstant();UUID user=(UUID)row.get("athlete_id");
            if(starts.isAfter(now)&&!starts.isAfter(now.plusSeconds(3600))&&wants(jdbc,user,false))mail.queue("reminder:"+row.get("id")+":"+user+":"+starts.getEpochSecond(),(String)row.get("email"),"CAP · Je training begint binnenkort","Je hebt een geboekte training op "+starts.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))+". Bekijk de actuele planning: "+mail.publicUrl+"/app/index.html#planning",starts);
        }
    }
}
