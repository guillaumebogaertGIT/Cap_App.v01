package be.cap.backend.accounts;

import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("accounts")
class AccountSecurity {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AccountSecurity.class);
    private static void error(jakarta.servlet.http.HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean
    org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }
    @Bean
    org.springframework.security.web.session.HttpSessionEventPublisher sessionEvents() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain accountFilterChain(HttpSecurity http, org.springframework.security.core.session.SessionRegistry registry,org.springframework.jdbc.core.JdbcTemplate jdbc) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/accounts/**", "/app/**").permitAll()
                .requestMatchers("/api/health", "/api/accounts/csrf", "/api/accounts/accept", "/api/accounts/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/billing/webhook").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/billing/checkout", "/api/billing/portal", "/api/billing/reconcile", "/api/billing/cancel").hasRole("ATHLETE")
                .requestMatchers("/api/billing", "/api/billing/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/finance").hasRole("COACH")
                .requestMatchers(HttpMethod.GET, "/api/coaches", "/api/coaches/**").hasRole("COACH")
                .requestMatchers(HttpMethod.POST, "/api/coaches").hasRole("COACH")
                .requestMatchers(HttpMethod.PUT, "/api/coaches/*/active").hasRole("COACH")
                .requestMatchers("/api/accounts/invitations").hasRole("COACH")
                .requestMatchers("/api/accounts/me", "/api/accounts/profile/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/accounts/password").authenticated()
                .requestMatchers("/api/club/**").authenticated()
                .requestMatchers("/api/workouts/**", "/api/workout-assignments/**", "/api/training/**", "/api/records", "/api/session-workouts").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/nutrition-targets").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/food-diary").authenticated()
                .requestMatchers("/api/food-diary/**").hasRole("ATHLETE")
                .requestMatchers("/api/nutrition-targets/**").hasRole("COACH")
                .requestMatchers(HttpMethod.GET, "/api/exercises").authenticated()
                .requestMatchers("/api/exercises/**").hasRole("COACH")
                // Legacy endpoints still use the example athlete. Deny them until migrated.
                .anyRequest().denyAll())
            // Only Stripe's signature-verified endpoint bypasses browser CSRF protection.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/billing/webhook"))
            .requestCache(cache -> cache.disable())
            .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(registry)
                .expiredSessionStrategy(event -> error(event.getResponse(),401,"Je sessie is beëindigd. Log opnieuw in.")))
            .addFilterAfter(new ActiveAccountFilter(jdbc),org.springframework.security.web.session.ConcurrentSessionFilter.class)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> error(response, 401, "Log in om verder te gaan."))
                .accessDeniedHandler((request, response, exception) -> error(response, 403, "Deze actie is niet toegestaan. Vernieuw de pagina als je sessie is verlopen.")))
            .formLogin(login -> login.loginPage("/api/accounts/login").loginProcessingUrl("/api/accounts/login").usernameParameter("email")
                .successHandler((request, response, auth) -> { LOG.info("CAP login succeeded"); response.setStatus(204); })
                .failureHandler((request, response, exception) -> { LOG.info("CAP login rejected"); error(response, 401, "Controleer je e-mailadres en wachtwoord."); }))
            .logout(logout -> logout.logoutUrl("/api/accounts/logout")
                .logoutSuccessHandler((request, response, auth) -> { LOG.info("CAP logout completed"); response.setStatus(204); }))
            .build();
    }
}
