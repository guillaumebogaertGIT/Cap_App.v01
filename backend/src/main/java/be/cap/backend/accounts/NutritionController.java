package be.cap.backend.accounts;

import java.security.Principal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Saved coach targets. Calculator drafts are not published to the athlete. */
@RestController
@Profile("accounts")
@RequestMapping("/api/nutrition-targets")
@Transactional
public class NutritionController {
    public record Targets(Double calories, Double proteinG, Double carbsG, Double fatG, Double waterMl) {}
    public record Input(Targets targets, String origin) {}
    public record Plan(UUID athleteId, String name, Targets targets, String origin, String savedAt) {}
    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public NutritionController(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc; this.accounts = accounts;
    }
    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Geen sporter gevonden.");
    }
    private Plan read(UUID athleteId) {
        var plans = jdbc.query("""
            SELECT n.*, u.display_name FROM nutrition_target n
            JOIN cap_user u ON u.id=n.athlete_id WHERE n.athlete_id=?
            """, (row, index) -> new Plan(athleteId, row.getString("display_name"),
            new Targets(row.getDouble("calories"), row.getDouble("protein_g"), row.getDouble("carbs_g"),
                row.getDouble("fat_g"), row.getDouble("water_ml")), row.getString("origin"),
            row.getTimestamp("saved_at").toInstant().toString()), athleteId);
        return plans.isEmpty() ? null : plans.getFirst();
    }
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Plan> get(Principal principal, @RequestParam(required = false) UUID athleteId) {
        var user = accounts.account(principal.getName());
        if (user.role().equals("ATHLETE")) {
            if (athleteId != null && !athleteId.equals(user.id())) throw missing();
            athleteId = user.id();
        } else if (athleteId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kies eerst een sporter.");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE'", Long.class, athleteId) == 0)
            throw missing();
        var plan = read(athleteId);
        return plan == null ? ResponseEntity.noContent().build() : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(plan);
    }
    private void valid(Double value, double minimum) {
        if (value == null || !Double.isFinite(value) || value < minimum)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vul alle vijf dagdoelen correct in.");
    }
    @PutMapping("/{athleteId}")
    public Plan save(Principal principal, @PathVariable UUID athleteId, @RequestBody Input input) {
        var coach = accounts.account(principal.getName());
        if (!coach.role().equals("COACH")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (input.targets() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vul alle vijf dagdoelen in.");
        var t = input.targets();
        valid(t.calories(), 1); valid(t.proteinG(), 0); valid(t.carbsG(), 0); valid(t.fatG(), 0); valid(t.waterMl(), 0);
        if (!Set.of("handmatig", "berekend", "berekend en aangepast").contains(input.origin() == null ? "" : input.origin()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kies handmatige of berekende dagdoelen.");
        // Lock the athlete before update/insert so two coaches cannot race the first save.
        var people = jdbc.query("SELECT id FROM cap_user WHERE id=? AND role='ATHLETE' FOR UPDATE",
            (row, index) -> row.getObject(1), athleteId);
        if (people.isEmpty()) throw missing();
        var savedAt = Timestamp.from(Instant.now());
        int updated = jdbc.update("""
            UPDATE nutrition_target SET coach_id=?,calories=?,protein_g=?,carbs_g=?,fat_g=?,water_ml=?,origin=?,saved_at=?
            WHERE athlete_id=?
            """, coach.id(), t.calories(), t.proteinG(), t.carbsG(), t.fatG(), t.waterMl(), input.origin(), savedAt, athleteId);
        if (updated == 0) jdbc.update("""
            INSERT INTO nutrition_target(athlete_id,coach_id,calories,protein_g,carbs_g,fat_g,water_ml,origin,saved_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            """, athleteId, coach.id(), t.calories(), t.proteinG(), t.carbsG(), t.fatG(), t.waterMl(), input.origin(), savedAt);
        return read(athleteId);
    }
}
