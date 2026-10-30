package be.cap.backend.accounts;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Financial reporting is a separate permission from membership review. */
@Component
@Profile("accounts")
public class FinanceAccess {
    private final String email;
    public FinanceAccess(@Value("${cap.accounts.finance-reviewer:}") String configured) {
        email = configured.trim().toLowerCase(Locale.ROOT);
        if (!email.isEmpty() && (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")))
            throw new IllegalStateException("Finance reviewer must be one valid coach login email.");
    }
    public boolean allows(AccountService.Account account) {
        return "COACH".equals(account.role()) && email.equals(account.email().toLowerCase(Locale.ROOT));
    }
}
