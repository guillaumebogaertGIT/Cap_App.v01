package be.cap.backend.accounts.billing;

import java.security.Principal;
import java.time.*;
import java.util.*;
import be.cap.backend.accounts.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Current-month operational counters; read-only and independent of Stripe availability. */
@RestController @Profile("accounts")
public class FinanceOverviewController {
    public record Counter(long value,long previous){}
    public record Overview(LocalDate today,Instant currentStart,Instant currentEnd,Instant previousStart,Instant previousEnd,long activeClients,Counter newClients,Counter participants,boolean billingEnabled){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;private final BillingSettings settings;
    public FinanceOverviewController(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy,BillingSettings settings){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;this.settings=settings;}
    private long registrations(Instant start,Instant end){return jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE role='ATHLETE' AND created_at>=? AND created_at<?",Long.class,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end));}
    private long participants(LocalDate start,ZonedDateTime end){return jdbc.queryForObject("SELECT COUNT(DISTINCT b.athlete_id) FROM session_booking b JOIN club_session s ON s.id=b.session_id WHERE b.cancelled=FALSE AND b.attendance='Aanwezig' AND s.session_date>=? AND (s.session_date<? OR (s.session_date=? AND s.start_time<?))",Long.class,start,end.toLocalDate(),end.toLocalDate(),end.toLocalTime());}
    @GetMapping("/api/finance/overview") public ResponseEntity<Overview> overview(Principal p){
        policy.require(accounts.account(p.getName()),StaffPolicy.Permission.FINANCE_VIEW);
        var zone=ZoneId.of("Europe/Brussels");var now=ZonedDateTime.now(zone);var month=YearMonth.from(now);var previous=month.minusMonths(1);
        var start=month.atDay(1).atStartOfDay(zone);var previousStart=previous.atDay(1).atStartOfDay(zone);
        var previousEnd=previous.atDay(Math.min(now.getDayOfMonth(),previous.lengthOfMonth())).atTime(now.toLocalTime()).atZone(zone);
        long active=jdbc.queryForObject("""
            SELECT COUNT(*) FROM cap_user u WHERE u.role='ATHLETE' AND u.active=TRUE AND (
                EXISTS(SELECT 1 FROM complimentary_membership g WHERE g.athlete_id=u.id AND g.revoked=FALSE AND g.starts_on<=? AND g.ends_on>=?)
                OR EXISTS(SELECT 1 FROM lesson_credit c WHERE c.user_id=u.id AND c.session_id IS NULL)
                OR EXISTS(SELECT 1 FROM billing_order o WHERE o.user_id=u.id AND o.status='paid' AND o.subscription_id IS NULL AND o.paid_at<=? AND o.period_end>?)
                OR EXISTS(SELECT 1 FROM billing_subscription s WHERE s.user_id=u.id AND s.status='active' AND s.payment_status='paid' AND s.period_end>? AND (s.term_end IS NULL OR s.term_end>?)))
            """,Long.class,now.toLocalDate(),now.toLocalDate(),java.sql.Timestamp.from(now.toInstant()),java.sql.Timestamp.from(now.toInstant()),java.sql.Timestamp.from(now.toInstant()),java.sql.Timestamp.from(now.toInstant()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Overview(now.toLocalDate(),start.toInstant(),now.toInstant(),previousStart.toInstant(),previousEnd.toInstant(),active,new Counter(registrations(start.toInstant(),now.toInstant()),registrations(previousStart.toInstant(),previousEnd.toInstant())),new Counter(participants(start.toLocalDate(),now),participants(previousStart.toLocalDate(),previousEnd)),settings.enabled));
    }
}
