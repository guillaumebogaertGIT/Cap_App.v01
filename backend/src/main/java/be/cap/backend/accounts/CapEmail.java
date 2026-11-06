package be.cap.backend.accounts;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** SMTP is opt-in. No recipient, body or reset link is ever logged. */
@Service @Profile("accounts") @EnableScheduling
public class CapEmail {
    private final JdbcTemplate jdbc;private final ObjectProvider<JavaMailSender> senders;private final TransactionTemplate tx;
    public final boolean enabled;public final String publicUrl;private final String from;
    public CapEmail(JdbcTemplate jdbc,ObjectProvider<JavaMailSender> senders,PlatformTransactionManager manager,
        @Value("${cap.mail.enabled:false}") boolean enabled,@Value("${cap.mail.public-url:}") String publicUrl,@Value("${cap.mail.from:}") String from){
        this.jdbc=jdbc;this.senders=senders;this.tx=new TransactionTemplate(manager);this.enabled=enabled;this.publicUrl=publicUrl.replaceAll("/+$","");this.from=from;
        if(enabled&&(!this.publicUrl.matches("https://[A-Za-z0-9.-]+(?::[0-9]+)?")||!from.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))throw new IllegalStateException("Configure CAP mail public HTTPS URL and sender before enabling delivery.");
    }
    public void queue(String key,String recipient,String subject,String body,Instant expires){
        if(!enabled)return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM email_outbox WHERE dedupe_key=?",Integer.class,key)==0)
            jdbc.update("INSERT INTO email_outbox(id,dedupe_key,recipient,subject,body,expires_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),key,recipient,subject,body,Timestamp.from(expires));
    }
    @Scheduled(fixedDelayString="${cap.mail.poll-ms:60000}",initialDelay=60000)
    public void deliver(){
        if(!enabled||senders.getIfAvailable()==null)return;
        // A row lock spans SMTP delivery, so separate app instances cannot send the same row concurrently.
        tx.executeWithoutResult(ignored->{
            jdbc.update("UPDATE email_outbox SET body='' WHERE sent_at IS NULL AND expires_at<CURRENT_TIMESTAMP AND body<>''");
            var rows=jdbc.queryForList("SELECT * FROM email_outbox WHERE sent_at IS NULL AND expires_at>CURRENT_TIMESTAMP AND next_attempt<=CURRENT_TIMESTAMP AND attempts<5 ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED");
            for(var row:rows){
                UUID id=(UUID)row.get("id");
                String key=(String)row.get("dedupe_key");
                if(key.startsWith("assignment:")){
                    var recipients=jdbc.query("SELECT id FROM cap_user WHERE email=? AND active=TRUE",(r,i)->r.getObject(1,UUID.class),row.get("recipient"));
                    if(recipients.isEmpty()||!EmailPreferences.wants(jdbc,recipients.getFirst(),true)){jdbc.update("UPDATE email_outbox SET expires_at=CURRENT_TIMESTAMP,body='' WHERE id=?",id);continue;}
                }
                if(key.startsWith("reminder:")){
                    var parts=key.split(":");boolean valid=false;
                    try{UUID session=UUID.fromString(parts[1]),user=UUID.fromString(parts[2]);long expected=Long.parseLong(parts[3]);
                        var booked=jdbc.queryForList("SELECT s.session_date,s.start_time FROM club_session s JOIN session_booking b ON b.session_id=s.id JOIN cap_user u ON u.id=b.athlete_id WHERE s.id=? AND b.athlete_id=? AND b.cancelled=FALSE AND u.active=TRUE",session,user);
                        if(!booked.isEmpty()){var current=booked.getFirst();long actual=java.time.LocalDateTime.of(((java.sql.Date)current.get("session_date")).toLocalDate(),((java.sql.Time)current.get("start_time")).toLocalTime()).atZone(ZoneId.of("Europe/Brussels")).toEpochSecond();valid=actual==expected&&EmailPreferences.wants(jdbc,user,false);}
                    }catch(Exception ignoredError){}
                    if(!valid){jdbc.update("UPDATE email_outbox SET expires_at=CURRENT_TIMESTAMP,body='' WHERE id=?",id);continue;}
                }
                try{var message=new SimpleMailMessage();message.setFrom(from);message.setTo((String)row.get("recipient"));message.setSubject((String)row.get("subject"));message.setText((String)row.get("body"));senders.getObject().send(message);jdbc.update("UPDATE email_outbox SET sent_at=CURRENT_TIMESTAMP,body='',attempts=attempts+1 WHERE id=?",id);}
                catch(Exception error){jdbc.update("UPDATE email_outbox SET attempts=attempts+1,next_attempt=? WHERE id=?",Timestamp.from(Instant.now().plusSeconds(300)),id);}
            }
        });
    }
}
