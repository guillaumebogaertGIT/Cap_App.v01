package be.cap.backend.accounts;

import java.security.Principal;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("accounts")
class AthleteAccessController {
    private final AccountService accounts;private final AthleteFeatureAccess access;
    AthleteAccessController(AccountService accounts,AthleteFeatureAccess access){this.accounts=accounts;this.access=access;}
    @GetMapping("/api/accounts/access") ResponseEntity<?> access(Principal p){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("fullAccess",access.fullAccess(accounts.account(p.getName()))));}
}
