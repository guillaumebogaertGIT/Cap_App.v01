package be.cap.backend.accounts;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("accounts")
public class StaffManagementService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(StaffManagementService.class);
    public record Create(String name,String email,String title,String password,String staffRole) {
        @Override public String toString(){return "CreateCoach[credentials redacted]";}
    }
    private final JdbcTemplate jdbc;
    private final StaffManagementAccess access;
    private final PasswordEncoder passwords;
    private final SessionRegistry sessions;
    public StaffManagementService(JdbcTemplate jdbc,StaffManagementAccess access,PasswordEncoder passwords,SessionRegistry sessions) {
        this.jdbc=jdbc;this.access=access;this.passwords=passwords;this.sessions=sessions;
    }
    private void requireManager(AccountService.Account actor) {
        if(!access.allows(actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Je mag geen coachaccounts beheren.");
    }
    @Transactional
    public CoachDirectoryController.Member create(AccountService.Account actor,Create input) {
        requireManager(actor);
        guard(actor);
        String role=input.staffRole()==null?"INSTRUCTOR":input.staffRole();
        permittedRole(actor,role);
        String name=input.name()==null?"":input.name().trim();
        String email=input.email()==null?"":input.email().trim().toLowerCase(Locale.ROOT);
        if(name.isBlank() || name.length()>100 || email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vul een naam en geldig e-mailadres in.");
        if(!Set.of("Coach","Trainer","Assistent · Trainer","Manager · Trainer","Standaard · Trainer").contains(input.title()==null?"":input.title()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een geldige coachtitel.");
        if(input.password()==null || input.password().length()<12 || input.password().getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Gebruik een tijdelijk wachtwoord van minstens 12 tekens en maximaal 72 UTF-8 bytes.");
        UUID id=UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at,coach_title,staff_role) VALUES(?,?,?,?,'COACH',?,?,?)",
                id,email,name,passwords.encode(input.password()),Timestamp.from(Instant.now()),input.title(),role);
        } catch(DuplicateKeyException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Dit e-mailadres heeft al een account. Activeer een gedeactiveerde coach opnieuw in plaats van een tweede account te maken.");
        }
        LOG.info("CAP coach account created: actor={}, target={}",actor.id(),id);
        new StaffPolicy(jdbc).audit(actor,"STAFF_CREATE",id.toString(),role);
        return new CoachDirectoryController.Member(id,name,input.title(),true);
    }
    @Transactional
    public void setActive(AccountService.Account actor,UUID id,Boolean active) {
        requireManager(actor);
        guard(actor);checkTarget(actor,id);
        if(active==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies activeren of deactiveren.");
        if(!active && actor.id().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Je kunt je eigen coachaccount niet deactiveren.");
        var emails=jdbc.query("SELECT email FROM cap_user WHERE id=? AND role='COACH' FOR UPDATE",(row,index)->row.getString(1),id);
        if(emails.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Coach niet gevonden.");
        jdbc.update("UPDATE cap_user SET active=? WHERE id=?",active,id);
        if(!active)lastOwner();
        new StaffPolicy(jdbc).audit(actor,"STAFF_ACTIVE",id.toString(),active.toString());
        // Uses the existing session registry: old sessions stay expired even after reactivation.
        for(Object principal:sessions.getAllPrincipals()) {
            if(principal instanceof UserDetails user && user.getUsername().equals(emails.getFirst()))
                sessions.getAllSessions(principal,false).forEach(org.springframework.security.core.session.SessionInformation::expireNow);
        }
        LOG.info("CAP coach activation changed: actor={}, target={}, active={}",actor.id(),id,active);
    }
    // Serialize changes to the small staff set, including concurrent last-owner changes.
    private void lockStaff(){jdbc.query("SELECT id FROM cap_user WHERE role='COACH' ORDER BY id FOR UPDATE",(r,i)->r.getObject(1));}
    public void guard(AccountService.Account actor){lockStaff();requireManager(actor);}
    private void permittedRole(AccountService.Account actor,String role) {
        if(!Set.of("OWNER","MANAGER","INSTRUCTOR","FINANCE").contains(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Kies een geldige medewerkersrol.");
        if(!new StaffPolicy(jdbc).role(actor).equals("OWNER") && !Set.of("INSTRUCTOR","FINANCE").contains(role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Alleen de eigenaar mag deze rol toekennen.");
    }
    private void checkTarget(AccountService.Account actor,UUID target) {
        var roles=jdbc.query("SELECT staff_role FROM cap_user WHERE id=? AND role='COACH'",(r,i)->r.getString(1),target);
        if(roles.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(roles.getFirst().equals("DEVELOPMENT"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Een ontwikkelaccount kan niet tot medewerker worden verheven.");
        if(!new StaffPolicy(jdbc).role(actor).equals("OWNER") && !Set.of("INSTRUCTOR","FINANCE").contains(roles.getFirst()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Deze medewerker wordt door de eigenaar beheerd.");
    }
    private void lastOwner(){if(jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE active=TRUE AND staff_role='OWNER'",Integer.class)==0)
        throw new ResponseStatusException(HttpStatus.CONFLICT,"Minstens één actieve eigenaar is vereist.");}
    @Transactional
    public void role(AccountService.Account actor,UUID target,String role) {
        requireManager(actor);guard(actor);checkTarget(actor,target);permittedRole(actor,role);
        if(actor.id().equals(target))throw new ResponseStatusException(HttpStatus.CONFLICT,"Je kunt je eigen rol niet wijzigen.");
        jdbc.update("UPDATE cap_user SET staff_role=? WHERE id=?",role,target);lastOwner();
        new StaffPolicy(jdbc).audit(actor,"STAFF_ROLE",target.toString(),role);
        String email=jdbc.queryForObject("SELECT email FROM cap_user WHERE id=?",String.class,target);
        for(Object principal:sessions.getAllPrincipals())if(principal instanceof UserDetails user && user.getUsername().equals(email))
            sessions.getAllSessions(principal,false).forEach(org.springframework.security.core.session.SessionInformation::expireNow);
    }
}
