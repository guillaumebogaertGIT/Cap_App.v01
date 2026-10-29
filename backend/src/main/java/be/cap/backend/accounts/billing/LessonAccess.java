package be.cap.backend.accounts.billing;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Participates in PlanningService's transaction, after the session capacity lock. */
@Service
@Profile("accounts")
public class LessonAccess {
    private final JdbcTemplate jdbc;
    private final CatalogOrders orders;
    private final BillingCatalog catalog;
    private final BillingSettings settings;
    private final boolean enforce;
    public LessonAccess(JdbcTemplate jdbc,CatalogOrders orders,BillingCatalog catalog,BillingSettings settings,
                        @Value("${cap.billing.enforce-bookings:false}") boolean enforce) {
        this.jdbc=jdbc;this.orders=orders;this.catalog=catalog;this.settings=settings;this.enforce=enforce;
    }
    public boolean enabled() { return settings.enabled && enforce; }
    public void reserve(UUID owner,UUID session,Instant starts) {
        if (!enabled()) return;
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,owner);
        boolean membership=orders.hasPrepaidAccess(owner,starts) || jdbc.query("""
            SELECT price_id FROM billing_subscription WHERE user_id=? AND status='active' AND payment_status='paid'
            AND period_start<=? AND period_end>? AND (term_end IS NULL OR term_end>?)
            """,(r,i)->r.getString("price_id"),owner,Timestamp.from(starts),Timestamp.from(starts),Timestamp.from(starts))
            .stream().anyMatch(p->catalog.byPrice(p).isPresent());
        if (membership) return;
        if (jdbc.queryForObject("SELECT COUNT(*) FROM lesson_credit WHERE user_id=? AND session_id=?",Integer.class,owner,session)>0) return;
        var credits=jdbc.query("SELECT id FROM lesson_credit WHERE user_id=? AND session_id IS NULL ORDER BY id LIMIT 1 FOR UPDATE",(r,i)->r.getObject(1,UUID.class),owner);
        if (credits.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Voor deze groepsles heb je een geldig lidmaatschap op de lesdatum of een lescredit nodig. Bekijk de Shop.");
        jdbc.update("UPDATE lesson_credit SET session_id=? WHERE id=?",session,credits.getFirst());
    }
    public void release(UUID owner,UUID session) {
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE",UUID.class,owner);
        jdbc.update("UPDATE lesson_credit SET session_id=NULL WHERE user_id=? AND session_id=?",owner,session);
    }
    public void releaseSession(UUID session) {
        jdbc.query("SELECT user_id FROM lesson_credit WHERE session_id=? ORDER BY user_id",(r,i)->r.getObject(1,UUID.class),session)
            .forEach(owner->release(owner,session));
    }
}
