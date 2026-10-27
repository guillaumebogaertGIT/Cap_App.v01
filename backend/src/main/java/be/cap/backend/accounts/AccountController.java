package be.cap.backend.accounts;

import java.security.Principal;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("accounts")
@RequestMapping("/api/accounts")
public class AccountController {
    record InviteRequest(String email) {}
    record AcceptRequest(String token, String name, String password) {
        @Override public String toString() { return "AcceptRequest[redacted]"; }
    }
    record CsrfResponse(String headerName, String token) {
        @Override public String toString() { return "CsrfResponse[redacted]"; }
    }
    private final AccountService accounts;

    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @Bean
    ApplicationRunner firstCoach(@Value("${cap.accounts.bootstrap-email:}") String email,
                                 @Value("${cap.accounts.bootstrap-password:}") String password) {
        return args -> accounts.bootstrap(email, password);
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @GetMapping("/me")
    AccountService.Account me(Principal principal) { return accounts.account(principal.getName()); }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    AccountService.Invitation invite(@RequestBody InviteRequest request, Principal principal) {
        return accounts.invite(request.email(), principal.getName());
    }

    @PostMapping("/accept")
    @ResponseStatus(HttpStatus.CREATED)
    AccountService.Account accept(@RequestBody AcceptRequest request) {
        return accounts.accept(request.token(), request.name(), request.password());
    }
}
