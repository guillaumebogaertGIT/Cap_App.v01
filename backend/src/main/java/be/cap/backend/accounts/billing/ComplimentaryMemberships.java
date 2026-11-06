package be.cap.backend.accounts.billing;

import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import be.cap.backend.accounts.*;

@RestController @Profile("accounts") @RequestMapping("/api/complimentary-memberships")
public class ComplimentaryMemberships {
    public record Grant(UUID id,UUID athleteId,String offer,LocalDate start,LocalDate end,String reason,boolean revoked){}
    public record Input(UUID athleteId,String offer,LocalDate start,LocalDate end,String reason){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;private final BillingCatalog catalog;
    public ComplimentaryMemberships(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy,BillingCatalog catalog){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;this.catalog=catalog;}
    private AccountService.Account owner(Principal p){var user=accounts.account(p.getName());if(!policy.role(user).equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return user;}
    private List<Grant> grants(UUID athlete){return jdbc.query("SELECT * FROM complimentary_membership WHERE athlete_id=? ORDER BY starts_on DESC,id",(r,i)->new Grant(r.getObject("id",UUID.class),athlete,r.getString("offer_code"),r.getObject("starts_on",LocalDate.class),r.getObject("ends_on",LocalDate.class),r.getString("reason"),r.getBoolean("revoked")),athlete);}
    @GetMapping public ResponseEntity<Map<String,Object>> list(Principal p,@RequestParam(required=false) UUID athleteId){var user=accounts.account(p.getName());if(user.role().equals("ATHLETE")){if(athleteId!=null&&!athleteId.equals(user.id()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);athleteId=user.id();}else owner(p);if(athleteId==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("grants",grants(athleteId),"offers",catalog.offers().stream().filter(o->o.credits()==0).toList()));}
    @PutMapping("/{id}") @Transactional
    public Grant grant(Principal p,@PathVariable UUID id,@RequestBody Input input){
        var actor=owner(p);jdbc.query("SELECT id FROM cap_user WHERE role='COACH' ORDER BY id FOR UPDATE",(r,i)->r.getObject(1));owner(p);
        if(input.athleteId()==null||input.start()==null||input.end()==null||input.end().isBefore(input.start())||input.end().isAfter(input.start().plusYears(5))||input.reason()==null||input.reason().isBlank()||input.reason().length()>500||catalog.require(input.offer()).credits()!=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een lidmaatschap, geldige periode en reden.");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE id=? AND role='ATHLETE' AND active=TRUE",Integer.class,input.athleteId())!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var existing=jdbc.queryForList("SELECT * FROM complimentary_membership WHERE id=?",id);
        if(!existing.isEmpty()){var same=grants(input.athleteId()).stream().filter(g->g.id().equals(id)).findFirst();if(same.isPresent())return same.get();throw new ResponseStatusException(HttpStatus.CONFLICT);}
        jdbc.update("INSERT INTO complimentary_membership(id,athlete_id,offer_code,starts_on,ends_on,reason,granted_by) VALUES(?,?,?,?,?,?,?)",id,input.athleteId(),input.offer(),input.start(),input.end(),input.reason().trim(),actor.id());policy.audit(actor,"MEMBERSHIP_GIFT",id.toString(),input.athleteId().toString());return grants(input.athleteId()).stream().filter(g->g.id().equals(id)).findFirst().orElseThrow();
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void revoke(Principal p,@PathVariable UUID id){var actor=owner(p);jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),actor.id());owner(p);if(jdbc.update("UPDATE complimentary_membership SET revoked=TRUE WHERE id=? AND revoked=FALSE",id)>0)policy.audit(actor,"MEMBERSHIP_GIFT_REVOKE",id.toString(),"REVOKED");}
    public static boolean access(JdbcTemplate jdbc,UUID user,Instant when){var day=when.atZone(ZoneId.of("Europe/Brussels")).toLocalDate();return jdbc.queryForObject("SELECT COUNT(*) FROM complimentary_membership WHERE athlete_id=? AND revoked=FALSE AND starts_on<=? AND ends_on>=?",Integer.class,user,day,day)>0;}
}
