package be.cap.backend.accounts;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Compatibility facade for the existing module; authorization comes only from account-ID roles. */
@Component
@Profile("accounts")
public class MembershipReviewAccess {
    private final StaffPolicy policy;
    public MembershipReviewAccess(StaffPolicy policy){this.policy=policy;}
    public boolean allows(AccountService.Account user){return policy.allows(user,StaffPolicy.Permission.FINANCE_VIEW);}
}