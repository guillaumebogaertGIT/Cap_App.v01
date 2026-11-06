package be.cap.backend.accounts;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("accounts") @RequestMapping("/api/accounts/recovery")
public class RecoveryController {
    public record Request(String email){}
    public record Reset(String token,String password){@Override public String toString(){return "Reset[redacted]";}}
    private final JdbcTemplate jdbc;private final CapEmail mail;private final PasswordEncoder passwords;private final SessionRegistry sessions;
    public RecoveryController(JdbcTemplate jdbc,CapEmail mail,PasswordEncoder passwords,SessionRegistry sessions){this.jdbc=jdbc;this.mail=mail;this.passwords=passwords;this.sessions=sessions;}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException();}}
    @GetMapping public Map<String,Boolean> available(){return Map.of("enabled",mail.enabled);}
    @PostMapping @Transactional
    public ResponseEntity<Void> request(@RequestBody Request input,HttpServletRequest request){
        if(!mail.enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Wachtwoordherstel is nog niet ingesteld. Neem contact op met CAP.");
        String email=input.email()==null?"":input.email().trim().toLowerCase(Locale.ROOT);
        if(email.length()>254||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vul een geldig e-mailadres in.");
        // Serialize the small rate table without trusting forwarded client headers.
        jdbc.query("SELECT id FROM workout_state WHERE id=1 FOR UPDATE",(r,i)->r.getInt(1));
        String bucket=hash(request.getRemoteAddr());Instant now=Instant.now();
        var rate=jdbc.queryForList("SELECT * FROM recovery_rate WHERE bucket=?",bucket);
        if(rate.isEmpty())jdbc.update("INSERT INTO recovery_rate(bucket,window_start,hits) VALUES(?,?,1)",bucket,Timestamp.from(now));
        else{var row=rate.getFirst();Instant start=((Timestamp)row.get("window_start")).toInstant();if(start.isBefore(now.minusSeconds(900)))jdbc.update("UPDATE recovery_rate SET hits=1,window_start=? WHERE bucket=?",Timestamp.from(now),bucket);else{if(((Number)row.get("hits")).intValue()>=10)return ResponseEntity.accepted().build();jdbc.update("UPDATE recovery_rate SET hits=hits+1 WHERE bucket=?",bucket);}}
        var users=jdbc.queryForList("SELECT id,email FROM cap_user WHERE email=? AND active=TRUE AND (role='ATHLETE' OR staff_role IN ('OWNER','MANAGER','INSTRUCTOR','FINANCE'))",email);
        if(!users.isEmpty()){
            var user=users.getFirst();UUID id=(UUID)user.get("id");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM account_recovery WHERE user_id=? AND used=FALSE AND expires_at>?",Integer.class,id,Timestamp.from(now.plusSeconds(1740)))==0){
                byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);Instant expires=now.plusSeconds(1800);
                jdbc.update("UPDATE account_recovery SET used=TRUE WHERE user_id=?",id);
                jdbc.update("INSERT INTO account_recovery(token_hash,user_id,expires_at) VALUES(?,?,?)",hash(token),id,Timestamp.from(expires));
                mail.queue("reset:"+hash(token),email,"CAP · Wachtwoord herstellen","Kies een nieuw wachtwoord via deze link (30 minuten geldig):\n"+mail.publicUrl+"/accounts/index.html#reset="+token+"\nHeb je dit niet gevraagd? Negeer deze e-mail.",expires);
            }
        }
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/confirm") @Transactional
    public ResponseEntity<Void> reset(@RequestBody Reset input){
        if(!mail.enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        if(input.token()==null||input.token().length()>100||input.password()==null||input.password().length()<12||input.password().getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Gebruik minstens 12 tekens en maximaal 72 UTF-8 bytes.");
        // Same lock order as recovery requests to avoid reset/request deadlocks.
        jdbc.query("SELECT id FROM workout_state WHERE id=1 FOR UPDATE",(r,i)->r.getInt(1));
        var rows=jdbc.queryForList("SELECT r.user_id,u.email FROM account_recovery r JOIN cap_user u ON u.id=r.user_id WHERE r.token_hash=? AND r.used=FALSE AND r.expires_at>CURRENT_TIMESTAMP AND u.active=TRUE AND (u.role='ATHLETE' OR u.staff_role IN ('OWNER','MANAGER','INSTRUCTOR','FINANCE')) FOR UPDATE",hash(input.token()));
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Deze herstellink is ongeldig of verlopen. Vraag een nieuwe aan.");
        var user=rows.getFirst();jdbc.update("UPDATE cap_user SET password_hash=? WHERE id=?",passwords.encode(input.password()),user.get("user_id"));jdbc.update("UPDATE account_recovery SET used=TRUE WHERE user_id=?",user.get("user_id"));
        jdbc.update("UPDATE email_outbox SET expires_at=CURRENT_TIMESTAMP,body='' WHERE dedupe_key=? AND sent_at IS NULL","reset:"+hash(input.token()));
        for(Object principal:sessions.getAllPrincipals())if(principal instanceof UserDetails details&&details.getUsername().equals(user.get("email")))sessions.getAllSessions(principal,false).forEach(org.springframework.security.core.session.SessionInformation::expireNow);
        return ResponseEntity.noContent().build();
    }
}
