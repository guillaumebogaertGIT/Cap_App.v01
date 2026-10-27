package be.cap.backend;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Preserve the existing loopback-only design preview, never production access. */
@Configuration
@Profile("!accounts")
class PreviewSecurity {
    @Bean
    SecurityFilterChain previewFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
