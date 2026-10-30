package be.cap.backend.accounts;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Staff administration is independent of titles, finances and membership review. */
@Component
@Profile("accounts")
public class StaffManagementAccess {
    private final String email;
    public StaffManagementAccess(@Value("${cap.accounts.staff-manager:}") String configured) {
        email=configured.trim().toLowerCase(Locale.ROOT);
        if(!email.isEmpty() && (email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            throw new IllegalStateException("Staff manager must be one valid coach login email.");
    }
    public boolean allows(AccountService.Account user) {
        return "COACH".equals(user.role()) && email.equals(user.email().toLowerCase(Locale.ROOT));
    }
}
