package be.cap.backend.accounts;

import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("accounts") @RequestMapping("/api/ai-coach")
public class AiCoachController {
    public record Input(UUID id,String message,List<AiCoachGateway.Turn> history,boolean consent,String mode) {}
    private final StaffPolicy policy;private final AiWorkoutDrafts drafts;
    private final JdbcTemplate jdbc;private final AccountService accounts;private final AiCoachGateway gateway;private final TransactionTemplate transaction;
    private final boolean enabled;private final Set<String> testers;
    public AiCoachController(JdbcTemplate jdbc,AccountService accounts,AiCoachGateway gateway,PlatformTransactionManager manager,StaffPolicy policy,AiWorkoutDrafts drafts,
        @Value("${cap.ai-coach.enabled:false}") boolean enabled,@Value("${cap.ai-coach.testers:}") String testers){
        this.jdbc=jdbc;this.accounts=accounts;this.gateway=gateway;this.policy=policy;this.drafts=drafts;transaction=new TransactionTemplate(manager);this.enabled=enabled;
        this.testers=new HashSet<>();for(String email:testers.split(","))if(!email.isBlank())this.testers.add(email.trim().toLowerCase(Locale.ROOT));
    }
    private AccountService.Account user(Principal p){if(p==null)throw error(HttpStatus.UNAUTHORIZED,"Log opnieuw in.");return accounts.account(p.getName());}
    private boolean allowed(AccountService.Account user){return Set.of("ATHLETE","COACH").contains(user.role())&&testers.contains(user.email().toLowerCase(Locale.ROOT));}
    private boolean ready(AccountService.Account user){return allowed(user)&&enabled&&gateway.configured();}
    private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
    @GetMapping public ResponseEntity<?> status(Principal p){var user=user(p);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("available",ready(user),"canDraft",ready(user)&&policy.allows(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE),"testUser",allowed(user),"remainingRequests",ready(user)?500-jdbc.queryForObject("SELECT reserved_cents FROM ai_coach_budget WHERE id=1",Integer.class):0));}
    @PostMapping public ResponseEntity<?> answer(Principal p,@RequestBody Input input){
        var user=user(p);if(!allowed(user))throw error(HttpStatus.FORBIDDEN,"Deze AI-test is nog niet beschikbaar voor jouw account.");
        if(!ready(user))throw error(HttpStatus.SERVICE_UNAVAILABLE,"De AI-test is nog niet ingeschakeld.");
        if(input==null||input.id()==null||!input.consent()||input.message()==null||input.message().isBlank()||input.message().length()>500)throw error(HttpStatus.BAD_REQUEST,"Bevestig de AI-melding en stel een vraag van maximaal 500 tekens.");
        var turns=new ArrayList<AiCoachGateway.Turn>();
        if(input.history()!=null){if(input.history().size()>6)throw error(HttpStatus.BAD_REQUEST,"Begin een nieuw gesprek.");for(var turn:input.history()){
            if(turn==null||!Set.of("user","assistant").contains(turn.role()==null?"":turn.role())||turn.content()==null||turn.content().isBlank()||turn.content().length()>8000)throw error(HttpStatus.BAD_REQUEST,"Ongeldig gesprek.");turns.add(turn);
        }}
        turns.add(new AiCoachGateway.Turn("user",input.message().trim()));
        boolean workout="workout".equals(input.mode())||("auto".equals(input.mode())&&policy.allows(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE));
        if(input.mode()!=null&&!Set.of("chat","workout","auto").contains(input.mode()))throw error(HttpStatus.BAD_REQUEST,"Onbekende AI-modus.");
        List<String> candidates=List.of();
        if(workout){policy.require(user,StaffPolicy.Permission.WORKOUT_TEMPLATE_MANAGE);try{candidates=drafts.candidates(user,input.message());}catch(Exception e){throw error(HttpStatus.SERVICE_UNAVAILABLE,"De oefenbibliotheek kon niet laden.");}}
        if(turns.stream().mapToInt(t->t.content().getBytes(StandardCharsets.UTF_8).length).sum()>8000)throw error(HttpStatus.BAD_REQUEST,"Dit gesprek is te lang. Kies Gesprek wissen.");
        transaction.executeWithoutResult(tx->{
            int used=jdbc.queryForObject("SELECT reserved_cents FROM ai_coach_budget WHERE id=1 FOR UPDATE",Integer.class);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM ai_coach_request WHERE id=?",Integer.class,input.id())>0)throw error(HttpStatus.CONFLICT,"Deze vraag is al verwerkt. Verstuur een nieuwe vraag.");
            if(used>=500)throw error(HttpStatus.TOO_MANY_REQUESTS,"Het gedeelde AI-testbudget is op. Er worden geen nieuwe AI-verzoeken verstuurd.");
            int day=jdbc.queryForObject("SELECT COUNT(*) FROM ai_coach_request WHERE user_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '24 hours'",Integer.class,user.id());
            int minute=jdbc.queryForObject("SELECT COUNT(*) FROM ai_coach_request WHERE user_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '1 minute'",Integer.class,user.id());
            if(day>=20||minute>=3)throw error(HttpStatus.TOO_MANY_REQUESTS,"Je AI-vragenlimiet is bereikt. Probeer later opnieuw.");
            jdbc.update("UPDATE ai_coach_budget SET reserved_cents=reserved_cents+1 WHERE id=1");
            jdbc.update("INSERT INTO ai_coach_request(id,user_id,state) VALUES(?,?,'reserved')",input.id(),user.id());
        });
        try{
            Map<String,Object> result=workout?drafts.validate(gateway.draft(List.copyOf(turns),candidates),candidates):Map.of("answer",gateway.answer(List.copyOf(turns)));
            jdbc.update("UPDATE ai_coach_request SET state='completed' WHERE id=?",input.id());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        }catch(Exception failure){
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            jdbc.update("UPDATE ai_coach_request SET state='failed' WHERE id=?",input.id());
            throw error(HttpStatus.SERVICE_UNAVAILABLE,"AI kon niet antwoorden. Je vraag wordt niet automatisch opnieuw verstuurd; de budgetreservering blijft behouden.");
        }
    }
}
