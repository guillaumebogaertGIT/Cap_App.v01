package be.cap.backend.accounts;

import java.security.Principal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("accounts") @RequestMapping("/api/nutrition-preferences")
public class NutritionPreferences {
    private static final String NOTICE_VERSION="coaching-ai-v1";
    public record Values(String allergies,String avoidedFoods,boolean aiShare,int version){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final StaffPolicy policy;
    public NutritionPreferences(JdbcTemplate jdbc,AccountService accounts,StaffPolicy policy){this.jdbc=jdbc;this.accounts=accounts;this.policy=policy;}
    private Values read(UUID id){return jdbc.query("SELECT allergies,avoided_foods,ai_share,version FROM nutrition_preference WHERE athlete_id=?",(r,i)->new Values(r.getString(1),r.getString(2),r.getBoolean(3),r.getInt(4)),id).stream().findFirst().orElse(new Values("","",false,0));}
    private AccountService.Account athlete(Principal p){var user=accounts.account(p.getName());if(!user.role().equals("ATHLETE"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return user;}
    @GetMapping("/self") public ResponseEntity<Values> self(Principal p){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(read(athlete(p).id()));}
    @PutMapping("/self") @Transactional public ResponseEntity<Values> save(Principal p,@RequestBody Values value){
        var user=athlete(p);if(value==null||value.version()<0||value.allergies()==null||value.avoidedFoods()==null||value.allergies().length()>500||value.avoidedFoods().length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Gebruik maximaal 500 tekens per veld.");
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),user.id());var previous=read(user.id());
        if(previous.version()!=value.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Je voorkeuren zijn gewijzigd. Vernieuw en probeer opnieuw.");
        if(previous.version()==0)jdbc.update("INSERT INTO nutrition_preference(athlete_id,allergies,avoided_foods,ai_share,ai_consent_at,ai_consent_notice) VALUES(?,?,?,?,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,CASE WHEN ? THEN ? ELSE NULL END)",user.id(),value.allergies().trim(),value.avoidedFoods().trim(),value.aiShare(),value.aiShare(),value.aiShare(),NOTICE_VERSION);
        else jdbc.update("UPDATE nutrition_preference SET allergies=?,avoided_foods=?,ai_share=?,ai_consent_at=CASE WHEN ? AND NOT ai_share THEN CURRENT_TIMESTAMP WHEN NOT ? THEN NULL ELSE ai_consent_at END,ai_consent_notice=CASE WHEN ? THEN ? ELSE NULL END,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE athlete_id=?",value.allergies().trim(),value.avoidedFoods().trim(),value.aiShare(),value.aiShare(),value.aiShare(),value.aiShare(),NOTICE_VERSION,user.id());
        if(previous.aiShare()!=value.aiShare())jdbc.update("INSERT INTO nutrition_ai_consent_event(id,athlete_id,granted,notice_version) VALUES(?,?,?,?)",UUID.randomUUID(),user.id(),value.aiShare(),NOTICE_VERSION);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(read(user.id()));
    }
    @GetMapping public ResponseEntity<Values> coach(Principal p,@RequestParam UUID athleteId){var actor=accounts.account(p.getName());policy.require(actor,StaffPolicy.Permission.NUTRITION_MANAGE);policy.require(actor,StaffPolicy.Permission.CLIENT_HEALTH);policy.requireClient(actor,athleteId.toString());return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(read(athleteId));}
    public Optional<Values> sharedWithAi(UUID athleteId){return Optional.of(read(athleteId)).filter(Values::aiShare);}
}
