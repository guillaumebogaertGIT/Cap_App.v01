package be.cap.backend.accounts;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** A separate staff permission, never inferred from an editable display name. */
@Component
@Profile("accounts")
public class MembershipReviewAccess {
    private final Set<String> emails;
    public MembershipReviewAccess(@Value("${cap.accounts.membership-reviewers:}") String configured) {
        var reviewers=new HashSet<String>();
        for (String value:configured.split(",")) {
            String email=value.trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty()) continue;
            if (email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                throw new IllegalStateException("Membership reviewers must be valid coach login email addresses.");
            reviewers.add(email);
        }
        if (reviewers.size()>2) throw new IllegalStateException("At most two coaches may review memberships.");
        emails=Set.copyOf(reviewers);
    }
    public boolean allows(AccountService.Account user) {
        return "COACH".equals(user.role()) && emails.contains(user.email().toLowerCase(Locale.ROOT));
    }
}
