package be.cap.backend.accounts;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import be.cap.backend.accounts.billing.BillingService;

/** A login is not a membership. Staff permissions remain a separate boundary. */
@Service @Profile("accounts")
public class AthleteFeatureAccess {
    private final BillingService billing;private final JdbcTemplate jdbc;private final boolean enforce;
    public AthleteFeatureAccess(BillingService billing,JdbcTemplate jdbc,@Value("${cap.accounts.enforce-membership:true}") boolean enforce){this.billing=billing;this.jdbc=jdbc;this.enforce=enforce;}
    public boolean fullAccess(AccountService.Account user){
        if(!enforce||!user.role().equals("ATHLETE"))return true;
        var summary=billing.summary(user);
        if(summary.credits()>0||summary.membershipAccess()&&summary.periodStart()!=null&&!summary.periodStart().isAfter(Instant.now())||be.cap.backend.accounts.billing.ComplimentaryMemberships.access(jdbc,user.id(),Instant.now()))return true;
        // Spending the final credit on a future booking must not immediately lock its owner out.
        var now=LocalDateTime.now(ZoneId.of("Europe/Brussels"));
        return jdbc.queryForObject("SELECT COUNT(*) FROM lesson_credit c JOIN club_session s ON s.id=c.session_id JOIN session_booking b ON b.session_id=s.id AND b.athlete_id=c.user_id WHERE c.user_id=? AND b.cancelled=FALSE AND (s.session_date>? OR (s.session_date=? AND s.end_time>?))",Integer.class,user.id(),now.toLocalDate(),now.toLocalDate(),now.toLocalTime())>0;
    }
    public boolean allowed(AccountService.Account user,String method,String path){
        // Essential account and purchase operations stay usable without paid access.
        if(path.equals("/api/accounts/access")||path.equals("/api/accounts/me")||path.equals("/api/accounts/password")||path.equals("/api/accounts/logout")||path.equals("/api/billing")||path.matches("/api/billing/(checkout|portal|cancel|reconcile)"))return true;
        if(method.equals("GET")&&(path.equals("/api/accounts/profile")||path.equals("/api/accounts/profile/photo")||path.equals("/api/club/sessions")||path.equals("/api/club/bookings")))return true;
        return fullAccess(user);
    }
}
