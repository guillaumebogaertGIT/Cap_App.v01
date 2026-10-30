package be.cap.backend.accounts;

import java.security.Principal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("accounts")
@RequestMapping("/api/measurements")
public class MeasurementController {
    private final AccountService accounts;
    private final MeasurementService measurements;
    private final ProfileController profiles;
    private final JdbcTemplate jdbc;
    public MeasurementController(AccountService accounts,MeasurementService measurements,ProfileController profiles,JdbcTemplate jdbc) {
        this.accounts=accounts;this.measurements=measurements;this.profiles=profiles;this.jdbc=jdbc;
    }
    @GetMapping
    public ResponseEntity<MeasurementService.Snapshot> own(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(measurements.snapshot(accounts.account(principal.getName()).id()));
    }
    @PostMapping
    public ResponseEntity<Map<String,Object>> save(Principal principal,@RequestBody Map<String,Double> input) throws java.io.IOException {
        measurements.save(accounts.account(principal.getName()),input);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.get(principal));
    }
    private AccountService.Account requireAthlete(Principal principal,UUID id) {
        if(!accounts.account(principal.getName()).role().equals("COACH"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return jdbc.query("SELECT id,email,display_name,role FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",
            (row,index)->new AccountService.Account(row.getObject("id",UUID.class),row.getString("email"),row.getString("display_name"),row.getString("role")),id)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Sporter niet gevonden."));
    }
    @GetMapping("/athletes/{id}")
    public ResponseEntity<MeasurementService.Snapshot> athlete(Principal principal,@PathVariable UUID id) {
        requireAthlete(principal,id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(measurements.snapshot(id));
    }
    @PostMapping("/athletes/{id}")
    public ResponseEntity<MeasurementService.Snapshot> saveAthlete(Principal principal,@PathVariable UUID id,@RequestBody Map<String,Double> input) {
        measurements.save(requireAthlete(principal,id),input);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(measurements.snapshot(id));
    }
}
