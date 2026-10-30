package be.cap.backend.accounts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

/** Serve the original application with only the current user's authorized data. */
@RestController
@Profile("accounts")
class RealAppController {
    private final AccountService accounts;
    private final PlanningService planning;
    private final AccountWorkoutService workouts;
    private final MembershipReviewAccess membershipReview;
    private final FinanceAccess financeAccess;
    @Value("${cap.frontend-location:classpath:/static/app/}index.html")
    private Resource appTemplate;

    RealAppController(AccountService accounts, PlanningService planning, AccountWorkoutService workouts, MembershipReviewAccess membershipReview, FinanceAccess financeAccess) {
        this.financeAccess = financeAccess;
        this.accounts = accounts; this.planning = planning; this.workouts = workouts; this.membershipReview = membershipReview;
    }

    @GetMapping(value = {"/app/", "/app/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> app(Principal principal) throws IOException {
        if (principal == null) return ResponseEntity.status(302)
            .header(HttpHeaders.LOCATION, "/accounts/index.html").build();
        var account = accounts.account(principal.getName());
        var data = new LinkedHashMap<String, Object>();
        data.put("account", account);
        data.put("permissions", java.util.Map.of("reviewMemberships", membershipReview.allows(account), "reviewFinance", financeAccess.allows(account)));
        data.put("people", account.role().equals("COACH") ? planning.people(account) : List.of());
        data.put("sessions", planning.list(account, null, null));
        data.put("bookings", planning.bookings(account));
        data.put("draft", account.role().equals("ATHLETE") ? workouts.active(account) : null);
        String json = JsonMapper.builder().build().writeValueAsString(data)
            .replace("&", "\\u0026").replace("<", "\\u003c")
            .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        String html = appTemplate.getContentAsString(StandardCharsets.UTF_8);
        html = html.replace("<head>", "<head><script id=\"cap-account-data\" type=\"application/json\">" + json
            + "</script><script src=\"/app/js/real-app.js\"></script><script defer src=\"/app/js/real-init.js\"></script>")
            .replace("js/auth-preview.js", "js/real-auth.js");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(html);
    }
}
