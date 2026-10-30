package be.cap.backend.accounts;

import java.security.Principal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Coach-only directory; never serializes whole accounts or private profile values. */
@RestController
@Profile("accounts")
@RequestMapping("/api/coaches")
public class CoachDirectoryController {
    public record Member(UUID id,String name,String title) {}
    public record Contact(UUID id,String name,String title,String email,String phone) {}
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    public CoachDirectoryController(AccountService accounts,JdbcTemplate jdbc) { this.accounts=accounts;this.jdbc=jdbc; }
    private void coach(Principal principal) {
        if (!"COACH".equals(accounts.account(principal.getName()).role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    @GetMapping
    public ResponseEntity<List<Member>> list(Principal principal) {
        coach(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jdbc.query(
            "SELECT id,display_name,coach_title FROM cap_user WHERE role='COACH' ORDER BY LOWER(display_name),id",
            (row,index)->new Member(row.getObject("id",UUID.class),row.getString("display_name"),row.getString("coach_title"))));
    }
    @GetMapping("/{id}")
    public ResponseEntity<Contact> contact(Principal principal,@PathVariable UUID id) {
        coach(principal);
        var contacts=jdbc.query("""
            SELECT u.id,u.display_name,u.coach_title,u.email,p.values_json
            FROM cap_user u LEFT JOIN account_profile p ON p.user_id=u.id WHERE u.id=? AND u.role='COACH'
            """,(row,index)->{
                String profile=row.getString("values_json"),phone="";
                if(profile!=null) {
                    var value=json.readTree(profile).path("phone");
                    if(value.isTextual() && value.asText().matches("\\+?[0-9][0-9(). -]{2,38}")) phone=value.asText();
                }
                return new Contact(row.getObject("id",UUID.class),row.getString("display_name"),row.getString("coach_title"),row.getString("email"),phone);
            },id);
        if(contacts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Collega niet gevonden.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contacts.getFirst());
    }
}
