package be.cap.backend.accounts;

import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController @Profile("accounts") @RequestMapping("/api/ai-coach/settings")
public class AiCoachSettings {
    public record Values(boolean consent,boolean ownProfile,boolean ownWorkouts,boolean ownNutrition,boolean ownBookings,boolean ownMealPlan,boolean coachActions){}
    public record View(int version,Values values){}
    private final JdbcTemplate jdbc;private final AccountService accounts;private final JsonMapper json=JsonMapper.builder().build();
    public AiCoachSettings(JdbcTemplate jdbc,AccountService accounts){this.jdbc=jdbc;this.accounts=accounts;}
    View read(AccountService.Account user){return jdbc.query("SELECT version,payload FROM ai_coach_settings WHERE user_id=?",(r,i)->new View(r.getInt(1),json.readValue(r.getString(2),Values.class)),user.id()).stream().findFirst().orElse(new View(0,new Values(false,false,false,false,false,false,false)));}
    @GetMapping public View get(Principal p){return read(accounts.account(p.getName()));}
    @PutMapping @Transactional public View save(Principal p,@RequestBody View input){var user=accounts.account(p.getName());
        if(input==null||input.values()==null||!user.role().equals("COACH")&&input.values().coachActions())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Controleer de instellingen.");
        jdbc.query("SELECT id FROM cap_user WHERE id=? FOR UPDATE",(r,i)->r.getObject(1),user.id());var old=read(user);
        if(old.version()!=input.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Instellingen zijn gewijzigd. Vernieuw eerst.");
        String payload=json.writeValueAsString(input.values());
        if(old.version()==0)jdbc.update("INSERT INTO ai_coach_settings(user_id,payload) VALUES(?,?)",user.id(),payload);
        else jdbc.update("UPDATE ai_coach_settings SET payload=?,version=version+1 WHERE user_id=?",payload,user.id());return read(user);
    }
}
