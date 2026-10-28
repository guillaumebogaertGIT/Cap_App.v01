package be.cap.backend.accounts.billing;

import java.time.Instant;
import java.util.Set;

/** Temporary business rules. No existing CAP feature checks this entitlement. */
public final class MembershipPolicy {
    public static final String PLAN="CAP Test Membership";
    public static final int MONTHLY_CENTS=1000;
    public static final String CURRENCY="EUR";
    private MembershipPolicy() {}
    public static boolean grantsAccess(String status, String paymentStatus, Instant end, Instant now) {
        return "active".equals(status) && "paid".equals(paymentStatus) && end!=null && end.isAfter(now);
    }
    public static boolean blocksAnotherSubscription(String status) {
        return !Set.of("canceled","incomplete_expired").contains(status);
    }
}
