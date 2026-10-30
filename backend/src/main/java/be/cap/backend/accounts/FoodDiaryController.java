package be.cap.backend.accounts;

import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Immutable portion snapshots, owned by the authenticated athlete. */
@RestController
@Profile("accounts")
@RequestMapping("/api/food-diary")
@Transactional
public class FoodDiaryController {
    public record Ingredient(String name, String foodId, Double amount, String unit,
        Double calories, Double proteinG, Double carbsG, Double fatG) {}
    public record Entry(UUID id, LocalDate date, String meal, String name, String foodId,
        Double amount, String unit, Double calories, Double proteinG, Double carbsG, Double fatG,
        List<Ingredient> ingredients) {}
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final Set<String> MEALS = Set.of("breakfast", "snack", "lunch", "dinner", "bedtime", "pre", "post");

    public FoodDiaryController(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc; this.accounts = accounts;
    }
    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Controleer de datum, maaltijd en voedingswaarden.");
    }
    private void number(Double value, boolean positive) {
        if (value == null || !Double.isFinite(value) || value < 0 || (positive && value == 0) || value > 1000000) throw invalid();
    }
    private void portion(String name, String unit, String foodId, Double amount, Double calories, Double protein, Double carbs, Double fat) {
        if (name == null || name.isBlank() || name.length() > 120 || unit == null || unit.isBlank() || unit.length() > 40
            || (foodId != null && foodId.length() > 120)) throw invalid();
        number(amount, true); number(calories, false); number(protein, false); number(carbs, false); number(fat, false);
    }
    private void valid(Entry entry) {
        if (entry == null || entry.id() == null || entry.date() == null
            || entry.date().isAfter(LocalDate.now(ZoneId.of("Europe/Brussels")))
            || entry.date().isBefore(LocalDate.of(1900, 1, 1)) || !MEALS.contains(entry.meal() == null ? "" : entry.meal())) throw invalid();
        portion(entry.name(), entry.unit(), entry.foodId(), entry.amount(), entry.calories(), entry.proteinG(), entry.carbsG(), entry.fatG());
        if (entry.ingredients() != null) {
            if (entry.ingredients().size() > 100) throw invalid();
            for (var item : entry.ingredients()) {
                if (item == null) throw invalid();
                portion(item.name(), item.unit(), item.foodId(), item.amount(), item.calories(), item.proteinG(), item.carbsG(), item.fatG());
            }
        }
    }
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<Entry>> get(Principal principal, @RequestParam LocalDate date, @RequestParam(required = false) UUID athleteId) {
        var user = accounts.account(principal.getName());
        if(user.role().equals("COACH"))new StaffPolicy(jdbc).require(user,StaffPolicy.Permission.NUTRITION_MANAGE);
        if (user.role().equals("ATHLETE")) {
            if (athleteId != null && !athleteId.equals(user.id())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            athleteId = user.id();
        }
        if (athleteId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kies eerst een sporter.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE'", Long.class, athleteId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var entries = jdbc.query("SELECT payload FROM food_diary_entry WHERE athlete_id=? AND entry_date=? ORDER BY saved_at,id",
            (row, index) -> json.readValue(row.getString(1), Entry.class), athleteId, date);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entries);
    }
    @PostMapping
    public ResponseEntity<Entry> add(Principal principal, @RequestBody Entry entry) {
        var user = accounts.account(principal.getName());
        if (!user.role().equals("ATHLETE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        valid(entry);
        // Serialize additions per owner, including duplicate retry checks.
        jdbc.queryForObject("SELECT id FROM cap_user WHERE id=? FOR UPDATE", UUID.class, user.id());
        var previous = jdbc.query("SELECT athlete_id,payload FROM food_diary_entry WHERE id=?",
            (row, index) -> Map.entry(row.getObject(1, UUID.class), row.getString(2)), entry.id());
        String payload = json.writeValueAsString(entry);
        if (!previous.isEmpty()) {
            var existing = previous.getFirst();
            if (!existing.getKey().equals(user.id()) || !existing.getValue().equals(payload))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Deze invoer bestaat al. Vernieuw het dagboek.");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entry);
        }
        jdbc.update("INSERT INTO food_diary_entry(id,athlete_id,entry_date,payload) VALUES(?,?,?,?)", entry.id(), user.id(), entry.date(), payload);
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(entry);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable UUID id) {
        var user = accounts.account(principal.getName());
        if (!user.role().equals("ATHLETE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (jdbc.update("DELETE FROM food_diary_entry WHERE id=? AND athlete_id=?", id, user.id()) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.noContent().build();
    }
}
