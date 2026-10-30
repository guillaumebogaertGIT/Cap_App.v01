package be.cap.backend.accounts;

import java.security.Principal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("accounts")
@RequestMapping("/api/staff")
class StaffController {
    private final AccountService accounts;private final StaffPolicy policy;private final StaffManagementService staff;private final JdbcTemplate jdbc;
    StaffController(AccountService accounts,StaffPolicy policy,StaffManagementService staff,JdbcTemplate jdbc){this.accounts=accounts;this.policy=policy;this.staff=staff;this.jdbc=jdbc;}
    private AccountService.Account actor(Principal p){var user=accounts.account(p.getName());policy.require(user,StaffPolicy.Permission.STAFF_MANAGE);return user;}
    @GetMapping("/members")
    ResponseEntity<List<Map<String,Object>>> members(Principal p){actor(p);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jdbc.queryForList(
        "SELECT id,display_name AS name,email,staff_role AS role,active FROM cap_user WHERE role='COACH' ORDER BY display_name"));}
    record Role(String role){}
    @PutMapping("/{id}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void role(Principal p,@PathVariable UUID id,@RequestBody Role input){staff.role(actor(p),id,input.role());}
    @GetMapping("/{id}/clients")
    ResponseEntity<List<UUID>> clients(Principal p,@PathVariable UUID id){actor(p);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
        jdbc.query("SELECT client_id FROM instructor_client WHERE instructor_id=?",(r,i)->r.getObject(1,UUID.class),id));}
    private void validScope(UUID instructor,UUID client){
        if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='COACH' AND staff_role='INSTRUCTOR' AND active=TRUE",Integer.class,instructor)!=1
            || jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",Integer.class,client)!=1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een actieve lesgever en sporter.");
    }
    @PutMapping("/{id}/clients/{client}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void grant(Principal p,@PathVariable UUID id,@PathVariable UUID client){var actor=actor(p);staff.guard(actor);validScope(id,client);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM instructor_client WHERE instructor_id=? AND client_id=?",Integer.class,id,client)==0){
            jdbc.update("INSERT INTO instructor_client(instructor_id,client_id,granted_by,granted_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",id,client,actor.id());policy.audit(actor,"CLIENT_GRANT",id.toString(),client.toString());}}
    @DeleteMapping("/{id}/clients/{client}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void revoke(Principal p,@PathVariable UUID id,@PathVariable UUID client){var actor=actor(p);staff.guard(actor);
        if(jdbc.update("DELETE FROM instructor_client WHERE instructor_id=? AND client_id=?",id,client)>0)policy.audit(actor,"CLIENT_REVOKE",id.toString(),client.toString());}
    @GetMapping("/audit")
    ResponseEntity<List<Map<String,Object>>> audit(Principal p){actor(p);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jdbc.queryForList(
        "SELECT a.id,a.actor_id,COALESCE(u.display_name,'Goedgekeurde offline activatie') AS actor,a.action,a.target_id,a.detail,a.created_at FROM staff_audit a LEFT JOIN cap_user u ON u.id=a.actor_id ORDER BY a.created_at DESC LIMIT 100"));}
}
