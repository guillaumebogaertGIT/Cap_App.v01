package be.cap.backend;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties={"spring.datasource.url=jdbc:h2:mem:recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","cap.mail.enabled=true","cap.mail.public-url=https://cap.example.test","cap.mail.from=cap@example.test"})
class RecoveryTests extends AccountApiTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.mail.javamail.JavaMailSender sender;
    @org.springframework.beans.factory.annotation.Autowired be.cap.backend.accounts.EmailPreferences emails;
    @org.springframework.beans.factory.annotation.Autowired be.cap.backend.accounts.CapEmail delivery;
    @Test void remindersDeduplicateAndCancelledBookingsAreNotSent() throws Exception{
        var owner=client();login(owner,"coach@example.test","Coach-test-password-123");var athlete=client();UUID user=UUID.fromString(acceptTestAthlete(owner,athlete,"reminder-"+UUID.randomUUID()+"@example.test"));
        var starts=java.time.Instant.now().plusSeconds(1800).atZone(java.time.ZoneId.of("Europe/Brussels")).withSecond(0).withNano(0);UUID session=UUID.randomUUID();
        jdbc.update("INSERT INTO club_session(id,title,session_date,start_time,end_time,coach_id,location,capacity,session_type,category,program) SELECT ?,'Reminder fixture',?,?,?,id,'Test',8,'Groep','Strength','' FROM cap_user WHERE email='coach@example.test'",session,starts.toLocalDate(),starts.toLocalTime(),java.time.LocalTime.of(23,59,59));
        jdbc.update("INSERT INTO session_booking(session_id,athlete_id) VALUES(?,?)",session,user);emails.reminders();emails.reminders();
        String key="reminder:"+session+":"+user+":"+starts.toEpochSecond();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM email_outbox WHERE dedupe_key=?",Integer.class,key));
        jdbc.update("UPDATE session_booking SET cancelled=TRUE WHERE session_id=? AND athlete_id=?",session,user);delivery.deliver();
        assertEquals("",jdbc.queryForObject("SELECT body FROM email_outbox WHERE dedupe_key=?",String.class,key));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM email_outbox WHERE dedupe_key=? AND sent_at IS NOT NULL",Integer.class,key));
    }
    @Test void recoveryIsGenericSingleUseAndInvalidatesOldPassword() throws Exception{
        var c=client();String known="{\"email\":\"coach@example.test\"}",unknown="{\"email\":\"nobody@example.test\"}";
        assertEquals(403,request(c,"POST","/api/accounts/recovery",known,null,false).statusCode());
        assertEquals(202,request(c,"POST","/api/accounts/recovery",unknown,csrf(c),false).statusCode());
        assertEquals(202,request(c,"POST","/api/accounts/recovery",known,csrf(c),false).statusCode());
        String body=jdbc.queryForObject("SELECT body FROM email_outbox WHERE dedupe_key LIKE 'reset:%' ORDER BY created_at DESC LIMIT 1",String.class);
        String token=body.split("#reset=")[1].split("\\n")[0];assertFalse(jdbc.queryForObject("SELECT token_hash FROM account_recovery ORDER BY expires_at DESC LIMIT 1",String.class).equals(token));
        var loginClient=client();login(loginClient,"coach@example.test","Coach-test-password-123");
        String reset=json.writeValueAsString(Map.of("token",token,"password","New-recovery-password-123"));
        assertEquals(204,request(c,"POST","/api/accounts/recovery/confirm",reset,csrf(c),false).statusCode());
        assertEquals(400,request(c,"POST","/api/accounts/recovery/confirm",reset,csrf(c),false).statusCode());
        assertEquals(401,request(loginClient,"GET","/api/accounts/me",null,null,false).statusCode());
        var fresh=client();login(fresh,"coach@example.test","New-recovery-password-123");
        // Restore this fake fixture's password so inherited API tests remain independent.
        jdbc.update("UPDATE cap_user SET password_hash=? WHERE email='coach@example.test'",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode("Coach-test-password-123"));
    }
}
