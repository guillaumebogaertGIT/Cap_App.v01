package be.cap.backend.accounts;

import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/** Explicit local CLI only. Never exposed through HTTP or run during app startup. */
public final class CoachProvisioner {
    public record Coach(String email,String name,String password) {
        @Override public String toString() { return "Coach[credentials redacted]"; }
    }
    public static List<String> create(Connection connection,List<Coach> coaches,PasswordEncoder encoder) throws SQLException {
        var results=new ArrayList<String>();
        var emails=new HashSet<String>();
        for (var coach:coaches) {
            if (coach.email()==null || !coach.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || coach.email().length()>254 || !coach.email().equals(coach.email().toLowerCase(Locale.ROOT))
                || !emails.add(coach.email()) || coach.name()==null || coach.name().isBlank() || coach.name().length()>100
                || coach.password()==null || coach.password().length()<12 || coach.password().getBytes(StandardCharsets.UTF_8).length>72)
                throw new IllegalArgumentException("Invalid coach input; no accounts changed.");
        }
        connection.setAutoCommit(false);
        try {
            // Serializes repeated executions of this local tool, without locking unrelated app data.
            try (var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(648205310)")) { lock.execute(); }
            for (var coach:coaches) {
                try (var existing=connection.prepareStatement("SELECT role FROM cap_user WHERE email=?")) {
                    existing.setString(1,coach.email());
                    try (var row=existing.executeQuery()) {
                        if (row.next()) {
                            if (!"COACH".equals(row.getString(1))) throw new IllegalStateException("A supplied email already has a non-coach account. No accounts changed.");
                            results.add("KEPT "+coach.email()); continue;
                        }
                    }
                }
                try (var insert=connection.prepareStatement("INSERT INTO cap_user(id,email,display_name,password_hash,role,created_at) VALUES(?,?,?,?,'COACH',?)")) {
                    insert.setObject(1,UUID.randomUUID()); insert.setString(2,coach.email()); insert.setString(3,coach.name());
                    insert.setString(4,encoder.encode(coach.password())); insert.setTimestamp(5,Timestamp.from(Instant.now())); insert.executeUpdate();
                }
                results.add("CREATED "+coach.email());
            }
            connection.commit(); return results;
        } catch (SQLException | RuntimeException error) { connection.rollback(); throw error; }
        finally { connection.setAutoCommit(true); }
    }
    public static void main(String[] args) {
        try {
            String url=System.getenv("CAP_DATABASE_URL");
            if (url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/(cap|cap_test)"))
                throw new IllegalArgumentException("This tool requires an explicit local development database.");
            var coaches=JsonMapper.builder().build().readValue(System.in,Coach[].class);
            if (coaches.length==0 || coaches.length>20) throw new IllegalArgumentException("Invalid batch.");
            try (var connection=DriverManager.getConnection(url,System.getenv("CAP_DATABASE_USER"),System.getenv("CAP_DATABASE_PASSWORD"))) {
                create(connection,List.of(coaches),new BCryptPasswordEncoder(12)).forEach(System.out::println);
            }
        } catch (Exception error) {
            // JDBC exception details and input must never reach the console.
            System.err.println("Coach creation failed; transaction rolled back. Check the local database, existing account roles and input. No passwords printed.");
            System.exit(1);
        }
    }
}
