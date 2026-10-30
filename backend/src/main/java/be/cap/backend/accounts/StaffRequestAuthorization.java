package be.cap.backend.accounts;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import static be.cap.backend.accounts.StaffPolicy.Permission.*;

/** Explicit API action map; entity scope is additionally enforced by services. */
@Component
@Profile("accounts")
class StaffRequestAuthorization {
    private final StaffPolicy policy;
    StaffRequestAuthorization(StaffPolicy policy){this.policy=policy;}
    boolean allowed(AccountService.Account user,String method,String path) {
        boolean get=method.equals("GET"),athlete=user.role().equals("ATHLETE");
        if(path.equals("/api/accounts/me") || path.equals("/api/accounts/password") || path.startsWith("/api/accounts/profile")
            || path.equals("/api/measurements"))return true;
        if(path.equals("/api/accounts/invitations"))return policy.allows(user,CLIENT_SCOPE_MANAGE);
        if(path.equals("/api/finance"))return get && policy.allows(user,FINANCE_VIEW);
        if(path.equals("/api/billing/athletes") || path.startsWith("/api/billing/athletes/"))return get && policy.allows(user,FINANCE_VIEW);
        if(path.equals("/api/billing"))return get;
        if(path.matches("/api/billing/(checkout|portal|cancel|reconcile)"))return athlete && method.equals("POST");
        if(path.equals("/api/staff-notes"))return (get || method.equals("POST")) && policy.allows(user,get?NOTE_VIEW:NOTE_CREATE);
        if(path.startsWith("/api/staff/"))return policy.allows(user,STAFF_MANAGE);
        if(path.startsWith("/api/coaches"))return (get || path.equals("/api/coaches") && method.equals("POST")
            || path.matches("/api/coaches/[^/]+/active") && method.equals("PUT")) && policy.allows(user,get?CLASS_VIEW:STAFF_MANAGE);
        if(path.startsWith("/api/measurements/athletes/"))return policy.allows(user,CLIENT_HEALTH);
        if(path.startsWith("/api/nutrition-targets"))return athlete && get || policy.allows(user,NUTRITION_MANAGE);
        if(path.startsWith("/api/food-diary"))return athlete || get && policy.allows(user,NUTRITION_MANAGE);
        if(path.startsWith("/api/exercises"))return get?athlete || policy.allows(user,WORKOUT_VIEW):policy.allows(user,WORKOUT_TEMPLATE_MANAGE);
        if(path.startsWith("/api/workouts"))return get?athlete || policy.allows(user,WORKOUT_VIEW)
            :policy.allows(user,path.endsWith("/assignment")?WORKOUT_ASSIGN:WORKOUT_TEMPLATE_MANAGE);
        if(path.startsWith("/api/workout-assignments")) {
            if(path.endsWith("/note"))return false; // Historical text is retained read-only; new notes use attributed records.
            if(athlete)return get || method.equals("PUT") && path.endsWith("/schedule");
            return policy.allows(user,get?WORKOUT_VIEW:path.endsWith("/note")?WORKOUT_TEMPLATE_MANAGE:path.endsWith("/schedule")?WORKOUT_ASSIGN:WORKOUT_TEMPLATE_MANAGE);
        }
        if(path.equals("/api/records") || path.startsWith("/api/training"))return athlete || get && policy.allows(user,WORKOUT_VIEW);
        if(path.equals("/api/session-workouts"))return get && athlete;
        if(path.startsWith("/api/club/")) {
            if(athlete)return get || path.matches("/api/club/sessions/[^/]+/booking") && (method.equals("POST") || method.equals("DELETE"));
            return policy.allows(user,get?CLASS_VIEW:CLASS_MANAGE);
        }
        return false;
    }
}
