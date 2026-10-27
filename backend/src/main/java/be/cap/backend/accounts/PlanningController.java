package be.cap.backend.accounts;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("accounts")
@RequestMapping("/api/club")
class PlanningController {
    private final AccountService accounts;
    private final PlanningService planning;
    PlanningController(AccountService accounts, PlanningService planning) { this.accounts = accounts; this.planning = planning; }
    @GetMapping("/people")
    List<PlanningService.Person> people(Principal user) { return planning.people(accounts.account(user.getName())); }
    @GetMapping("/sessions")
    List<PlanningService.Session> sessions(Principal user, @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate through) {
        return planning.list(accounts.account(user.getName()), from, through);
    }
    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED)
    List<PlanningService.Session> create(Principal user, @RequestBody PlanningService.Input input) { return planning.save(null, input, accounts.account(user.getName())); }
    @PutMapping("/sessions/{id}")
    List<PlanningService.Session> update(Principal user, @PathVariable UUID id, @RequestBody PlanningService.Input input) { return planning.save(id, input, accounts.account(user.getName())); }
    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Principal user, @PathVariable UUID id) { planning.delete(id, accounts.account(user.getName())); }
    @PostMapping("/sessions/{id}/booking") @ResponseStatus(HttpStatus.NO_CONTENT)
    void book(Principal user, @PathVariable UUID id) { planning.book(id, accounts.account(user.getName()), false); }
    @DeleteMapping("/sessions/{id}/booking") @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(Principal user, @PathVariable UUID id) { planning.book(id, accounts.account(user.getName()), true); }
    @GetMapping("/bookings")
    List<PlanningService.Booking> bookings(Principal user) { return planning.bookings(accounts.account(user.getName())); }
    record Attendance(String status) {}
    @PutMapping("/sessions/{id}/bookings/{athleteId}/attendance") @ResponseStatus(HttpStatus.NO_CONTENT)
    void attendance(Principal user, @PathVariable UUID id, @PathVariable UUID athleteId, @RequestBody Attendance input) {
        planning.attendance(id, athleteId, input.status(), accounts.account(user.getName()));
    }
    @DeleteMapping("/sessions/{id}/bookings/{athleteId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelBooking(Principal user,@PathVariable UUID id,@PathVariable UUID athleteId) {planning.cancelBooking(id,athleteId,accounts.account(user.getName()));}
}
