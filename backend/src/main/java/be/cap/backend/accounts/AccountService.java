package be.cap.backend.accounts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("accounts")
public class AccountService implements UserDetailsService {
    public record Account(UUID id, String email, String name, String role) {}
    public record Invitation(String token, Instant expiresAt) {
        @Override public String toString() { return "Invitation[token=redacted, expiresAt=" + expiresAt + "]"; }
    }
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();
    @org.springframework.beans.factory.annotation.Value("${cap.accounts.bootstrap-owner:false}")
    private boolean bootstrapOwner;
    @org.springframework.beans.factory.annotation.Value("${cap.accounts.development-access:false}")
    private boolean developmentAccess;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    private String email(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw invalid("Vul een geldig e-mailadres in.");
        return email;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw invalid("Gebruik een wachtwoord van minstens 12 tekens en maximaal 72 UTF-8 bytes.");
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public Account account(String email) {
        List<Account> users = jdbc.query("SELECT id, email, display_name, role FROM cap_user WHERE email = ?",
            (row, index) -> new Account(row.getObject("id", UUID.class), row.getString("email"), row.getString("display_name"), row.getString("role")), email);
        if (users.isEmpty()) throw new UsernameNotFoundException("Account niet gevonden.");
        return users.getFirst();
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        var users = jdbc.query("SELECT email, password_hash, role, active, staff_role FROM cap_user WHERE email = ?",
            (row, index) -> User.withUsername(row.getString("email")).password(row.getString("password_hash"))
                .roles(row.getString("role")).disabled(!row.getBoolean("active") || ("COACH".equals(row.getString("role"))
                    && ("UNASSIGNED".equals(row.getString("staff_role")) || "DEVELOPMENT".equals(row.getString("staff_role"))
                        && !(developmentAccess && environment.matchesProfiles("development-account") && !environment.matchesProfiles("production","prod"))))).build(), normalized);
        if (users.isEmpty()) throw new UsernameNotFoundException("Account niet gevonden.");
        return users.getFirst();
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        var hashes = jdbc.query("SELECT password_hash FROM cap_user WHERE email=? FOR UPDATE",
            (row,index) -> row.getString(1), email);
        if (currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length>72 || hashes.isEmpty()
            || !passwords.matches(currentPassword, hashes.getFirst()))
            throw invalid("Je huidige wachtwoord klopt niet.");
        if (passwords.matches(newPassword, hashes.getFirst()))
            throw invalid("Kies een ander wachtwoord dan je huidige wachtwoord.");
        jdbc.update("UPDATE cap_user SET password_hash=? WHERE email=?", passwords.encode(newPassword), email);
    }

    /** First coach comes from local environment settings, never a public signup. */
    @Transactional
    public void bootstrap(String rawEmail, String password) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE role = 'COACH'", Long.class) > 0) return;
        if (rawEmail == null || rawEmail.isBlank() || password == null || password.isBlank())
            throw new IllegalStateException("Stel CAP_COACH_EMAIL en CAP_COACH_PASSWORD in voor de eerste coach.");
        String email = email(rawEmail);
        validatePassword(password);
        jdbc.update("INSERT INTO cap_user (id, email, display_name, password_hash, role, created_at) VALUES (?, ?, ?, ?, 'COACH', ?)",
            UUID.randomUUID(), email, "CAP coach", passwords.encode(password), Timestamp.from(Instant.now()));
        if(bootstrapOwner) jdbc.update("UPDATE cap_user SET staff_role='OWNER' WHERE email=?",email);
    }

    @Transactional
    public Invitation invite(String rawEmail, String coachEmail) {
        String email = email(rawEmail);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM cap_user WHERE email = ?", Long.class, email) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dit e-mailadres heeft al een account.");
        Account coach = account(coachEmail);
        new StaffPolicy(jdbc).require(coach,StaffPolicy.Permission.CLIENT_SCOPE_MANAGE);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        Instant expires = Instant.now().plus(7, ChronoUnit.DAYS);
        // Reissuing an invitation invalidates the previous link.
        jdbc.update("DELETE FROM athlete_invitation WHERE email = ?", email);
        try {
            jdbc.update("INSERT INTO athlete_invitation (token_hash, email, invited_by, expires_at) VALUES (?, ?, ?, ?)",
                hash(token), email, coach.id(), Timestamp.from(expires));
        } catch (DuplicateKeyException concurrentInvite) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "De uitnodiging werd tegelijk gewijzigd. Probeer opnieuw.");
        }
        return new Invitation(token, expires);
    }

    @Transactional
    public Account accept(String token, String name, String password) {
        if (token == null || !token.matches("[0-9a-f]{64}")) throw invalid("Deze uitnodiging is ongeldig of verlopen.");
        if (name == null || name.isBlank() || name.trim().length() > 100) throw invalid("Vul een naam in van maximaal 100 tekens.");
        validatePassword(password);
        String tokenHash = hash(token);
        var emails = jdbc.query("SELECT email FROM athlete_invitation WHERE token_hash = ? AND accepted_at IS NULL AND expires_at > ? FOR UPDATE",
            (row, index) -> row.getString("email"), tokenHash, Timestamp.from(Instant.now()));
        if (emails.isEmpty()) throw invalid("Deze uitnodiging is ongeldig of verlopen.");
        String email = emails.getFirst();
        try {
            jdbc.update("INSERT INTO cap_user (id, email, display_name, password_hash, role, created_at) VALUES (?, ?, ?, ?, 'ATHLETE', ?)",
                UUID.randomUUID(), email, name.trim(), passwords.encode(password), Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dit e-mailadres heeft al een account.");
        }
        jdbc.update("UPDATE athlete_invitation SET accepted_at = ? WHERE token_hash = ?", Timestamp.from(Instant.now()), tokenHash);
        jdbc.update("INSERT INTO athlete_training (athlete_id) VALUES (?)", account(email).id());
        return account(email);
    }
}
