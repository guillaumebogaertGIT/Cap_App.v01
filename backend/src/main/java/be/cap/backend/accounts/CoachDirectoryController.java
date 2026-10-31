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
    public record Member(UUID id,String name,String title,boolean active) {}
    public record Contact(UUID id,String name,String title,String email,String phone,boolean active) {}
    public record Activation(Boolean active) {}
    private final AccountService accounts;
    private final JdbcTemplate jdbc;
    private final StaffManagementAccess management;
    private final StaffManagementService staff;
    private final JsonMapper json=JsonMapper.builder().build();
    public CoachDirectoryController(AccountService accounts,JdbcTemplate jdbc,StaffManagementAccess management,StaffManagementService staff) {
        this.accounts=accounts;this.jdbc=jdbc;this.management=management;this.staff=staff;
    }
    private void coach(Principal principal) {
        if (!"COACH".equals(accounts.account(principal.getName()).role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    @GetMapping
    public ResponseEntity<List<Member>> list(Principal principal) {
        coach(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jdbc.query(
            "SELECT id,display_name,coach_title,active FROM cap_user WHERE role='COACH' AND (active=TRUE OR ?=TRUE) ORDER BY LOWER(display_name),id",
            (row,index)->new Member(row.getObject("id",UUID.class),row.getString("display_name"),row.getString("coach_title"),row.getBoolean("active")),management.allows(accounts.account(principal.getName()))));
    }
    @GetMapping("/{id}")
    public ResponseEntity<Contact> contact(Principal principal,@PathVariable UUID id) {
        coach(principal);
        var contacts=jdbc.query("""
            SELECT u.id,u.display_name,u.coach_title,u.email,u.active,p.values_json
            FROM cap_user u LEFT JOIN account_profile p ON p.user_id=u.id WHERE u.id=? AND u.role='COACH' AND (u.active=TRUE OR ?=TRUE)
            """,(row,index)->{
                String profile=row.getString("values_json"),phone="";
                if(profile!=null) {
                    var value=json.readTree(profile).path("phone");
                    if(value.isTextual() && value.asText().matches("\\+?[0-9][0-9(). -]{2,38}")) phone=value.asText();
                }
                return new Contact(row.getObject("id",UUID.class),row.getString("display_name"),row.getString("coach_title"),row.getString("email"),phone,row.getBoolean("active"));
            },id,management.allows(accounts.account(principal.getName())));
        if(contacts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Collega niet gevonden.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contacts.getFirst());
    }
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(Principal principal,@PathVariable UUID id) {
        coach(principal);
        var photos=jdbc.query("""
            SELECT p.photo FROM cap_user u LEFT JOIN account_profile p ON p.user_id=u.id
            WHERE u.id=? AND u.role='COACH' AND (u.active=TRUE OR ?=TRUE)
            """,(row,index)->row.getBytes(1),id,management.allows(accounts.account(principal.getName())));
        if(photos.isEmpty() || photos.getFirst()==null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(photos.getFirst());
    }
    @PostMapping
    public ResponseEntity<Member> create(Principal principal,@RequestBody StaffManagementService.Create input) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(staff.create(accounts.account(principal.getName()),input));
    }
    @PutMapping("/{id}/active")
    public ResponseEntity<Void> activate(Principal principal,@PathVariable UUID id,@RequestBody Activation input) {
        staff.setActive(accounts.account(principal.getName()),id,input.active());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
