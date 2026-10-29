package be.cap.backend;

import java.util.UUID;
import be.cap.backend.accounts.AccountService.Account;
import be.cap.backend.accounts.MembershipReviewAccess;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MembershipReviewAccessTests {
    private Account account(String email,String role) { return new Account(UUID.randomUUID(),email,"Glenn Vlamynck",role); }
    @Test void denyByDefaultAndNeverTrustDisplayNamesOrAthleteEmailAlone() {
        var coach=account("glenn@example.test","COACH");
        assertFalse(new MembershipReviewAccess("").allows(coach));
        var policy=new MembershipReviewAccess(" Glenn@example.test, dieter@example.test ");
        assertTrue(policy.allows(coach));assertTrue(policy.allows(account("dieter@example.test","COACH")));
        assertFalse(policy.allows(account("another@example.test","COACH")));
        assertFalse(policy.allows(account("glenn@example.test","ATHLETE")));
    }
    @Test void rejectMoreThanTwoDesignatedCoachesAndInvalidConfiguration() {
        assertThrows(IllegalStateException.class,()->new MembershipReviewAccess("one@example.test,two@example.test,three@example.test"));
        assertThrows(IllegalStateException.class,()->new MembershipReviewAccess("not-an-email"));
    }
}
