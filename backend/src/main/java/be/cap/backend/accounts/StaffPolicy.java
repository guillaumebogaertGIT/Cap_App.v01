package be.cap.backend.accounts;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Current database grants, never editable labels or stale session authorities. */
@Component
@Profile("accounts")
public class StaffPolicy {
    public enum Permission { WORKOUT_VIEW, WORKOUT_TEMPLATE_MANAGE, WORKOUT_ASSIGN, CLASS_VIEW,
        CLASS_MANAGE, SCHEDULE_VIEW_OWN, SCHEDULE_MANAGE, NOTE_VIEW, NOTE_CREATE,
        NUTRITION_MANAGE, FINANCE_VIEW, STAFF_MANAGE, CLIENT_SCOPE_MANAGE, CLIENT_HEALTH }
    private static final Set<Permission> INSTRUCTOR=Set.of(Permission.WORKOUT_VIEW,Permission.WORKOUT_ASSIGN,
        Permission.CLASS_VIEW,Permission.SCHEDULE_VIEW_OWN,Permission.NOTE_VIEW,Permission.NOTE_CREATE);
    private final JdbcTemplate jdbc;
    public StaffPolicy(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String role(AccountService.Account user) {
        if(!"COACH".equals(user.role()))return "CLIENT";
        return jdbc.query("SELECT staff_role FROM cap_user WHERE id=? AND active=TRUE",(r,i)->r.getString(1),user.id())
            .stream().findFirst().orElse("UNASSIGNED");
    }
    public boolean allows(AccountService.Account user,Permission permission) {
        return allows(role(user),permission);
    }
    private boolean allows(String role,Permission permission) {
        return switch(role) {
            case "OWNER","MANAGER" -> true;
            case "INSTRUCTOR" -> INSTRUCTOR.contains(permission);
            case "FINANCE" -> permission==Permission.FINANCE_VIEW;
            default -> false;
        };
    }
    public void require(AccountService.Account user,Permission permission) {
        if(!allows(user,permission))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Je hebt geen toestemming voor deze actie.");
    }
    public boolean clientAllowed(AccountService.Account user,UUID client) {
        if(allows(user,Permission.CLIENT_SCOPE_MANAGE))return true;
        return role(user).equals("INSTRUCTOR") && jdbc.queryForObject(
            "SELECT COUNT(*) FROM instructor_client WHERE instructor_id=? AND client_id=?",Integer.class,user.id(),client)>0;
    }
    public void requireClient(AccountService.Account user,String client) {
        UUID id;try{id=UUID.fromString(client);}catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Sporter niet gevonden.");}
        if(!clientAllowed(user,id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Deze sporter is niet aan jou gekoppeld.");
    }
    public Map<String,Boolean> capabilities(AccountService.Account user) {
        var values=new LinkedHashMap<String,Boolean>();String role=role(user);
        for(var permission:Permission.values())values.put(permission.name(),allows(role,permission));
        values.put("reviewFinance",allows(role,Permission.FINANCE_VIEW));
        values.put("reviewMemberships",allows(role,Permission.FINANCE_VIEW));
        values.put("manageCoaches",allows(role,Permission.STAFF_MANAGE));
        return values;
    }
    public Set<UUID> scopedClients(AccountService.Account user) {
        if(role(user).equals("INSTRUCTOR"))return new HashSet<>(jdbc.query("SELECT client_id FROM instructor_client WHERE instructor_id=?",(r,i)->r.getObject(1,UUID.class),user.id()));
        require(user,Permission.CLIENT_SCOPE_MANAGE);
        return new HashSet<>(jdbc.query("SELECT id FROM cap_user WHERE role='ATHLETE'",(r,i)->r.getObject(1,UUID.class)));
    }
    public void audit(AccountService.Account actor,String action,String target,String detail) {
        jdbc.update("INSERT INTO staff_audit(id,actor_id,action,target_id,detail,created_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
            UUID.randomUUID(),actor.id(),action,target,detail);
    }
}
