package be.cap.backend.accounts;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import be.cap.backend.accounts.billing.BillingService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Stored client data only; agreed tariffs never create or change payments. */
@RestController @Profile("accounts") @RequestMapping("/api/client-directory")
public class ClientDirectoryController {
    public record Client(UUID id,String name,boolean active,String plan,String status,boolean access,int credits,Instant paidUntil,Long cents,String period,int version){}
    public record Tariff(Long cents,String period,Integer version){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;private final BillingService billing;
    public ClientDirectoryController(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy,BillingService billing){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;this.billing=billing;}
    @GetMapping public ResponseEntity<List<Client>> list(Principal p){
        var actor=accounts.account(p.getName());policy.require(actor,StaffPolicy.Permission.FINANCE_VIEW);
        var clients=new ArrayList<Client>();
        for(var row:jdbc.queryForList("SELECT id,email,display_name,active FROM cap_user WHERE role='ATHLETE' ORDER BY LOWER(display_name),id")){
            UUID id=(UUID)row.get("id");var value=billing.summary(new AccountService.Account(id,(String)row.get("email"),(String)row.get("display_name"),"ATHLETE"));
            var prices=jdbc.queryForList("SELECT cents,period,version FROM client_tariff WHERE user_id=?",id);var price=prices.isEmpty()?null:prices.getFirst();
            clients.add(new Client(id,(String)row.get("display_name"),(Boolean)row.get("active"),value.status().equals("none")?(value.credits()>0?"Beurtenkaart":"Geen lidmaatschap"):value.plan(),value.status(),value.membershipAccess(),value.credits(),value.periodEnd(),price==null?null:((Number)price.get("cents")).longValue(),price==null?null:(String)price.get("period"),price==null?0:((Number)price.get("version")).intValue()));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(clients);
    }
    @PutMapping("/{id}/tariff") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
    public void tariff(Principal p,@PathVariable UUID id,@RequestBody Tariff input){
        var actor=accounts.account(p.getName());if(!policy.role(actor).equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(input.cents()==null||input.cents()<0||input.cents()>10000000||input.period()==null||!Set.of("month","quarter","year","session","total").contains(input.period())||input.version()==null||input.version()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Controleer het bedrag en de tariefperiode.");
        if(jdbc.query("SELECT id FROM cap_user WHERE id=? AND role='ATHLETE' FOR UPDATE",(r,i)->r.getObject(1),id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!policy.role(accounts.account(p.getName())).equals("OWNER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var versions=jdbc.queryForList("SELECT version FROM client_tariff WHERE user_id=?",id);int version=versions.isEmpty()?0:((Number)versions.getFirst().get("version")).intValue();
        if(version!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Het tarief is inmiddels gewijzigd. Vernieuw de lijst en probeer opnieuw.");
        if(version==0)jdbc.update("INSERT INTO client_tariff(user_id,cents,period,updated_by) VALUES(?,?,?,?)",id,input.cents(),input.period(),actor.id());
        else jdbc.update("UPDATE client_tariff SET cents=?,period=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?",input.cents(),input.period(),actor.id(),id);
        policy.audit(actor,"CLIENT_TARIFF_SET",id.toString(),input.cents()+" EUR cents / "+input.period());
    }
}
