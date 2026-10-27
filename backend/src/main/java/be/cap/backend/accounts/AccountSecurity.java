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
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain accountFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/accounts/**", "/app/**").permitAll()
                .requestMatchers("/api/health", "/api/accounts/csrf", "/api/accounts/accept", "/api/accounts/login").permitAll()
                .requestMatchers("/api/accounts/invitations").hasRole("COACH")
                .requestMatchers("/api/accounts/me").authenticated()
                .requestMatchers("/api/club/**").authenticated()
                .requestMatchers("/api/workouts/**", "/api/workout-assignments/**", "/api/training/**", "/api/records", "/api/session-workouts").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/exercises").authenticated()
                .requestMatchers("/api/exercises/**").hasRole("COACH")
                // Legacy endpoints still use the example athlete. Deny them until migrated.
                .anyRequest().denyAll())
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> response.sendError(401))
                .accessDeniedHandler((request, response, error) -> response.sendError(403)))
            .formLogin(login -> login.loginPage("/api/accounts/login").loginProcessingUrl("/api/accounts/login").usernameParameter("email")
                .successHandler((request, response, auth) -> response.setStatus(204))
                .failureHandler((request, response, error) -> response.sendError(401)))
            .logout(logout -> logout.logoutUrl("/api/accounts/logout")
                .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
            .build();
    }
}
