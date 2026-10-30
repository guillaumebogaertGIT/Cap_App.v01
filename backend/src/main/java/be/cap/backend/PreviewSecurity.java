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
    SecurityFilterChain previewFilterChain(HttpSecurity http,
        @org.springframework.beans.factory.annotation.Value("${server.address:127.0.0.1}") String address,
        org.springframework.core.env.Environment environment) throws Exception {
        if(!java.util.Set.of("127.0.0.1","localhost","::1").contains(address) || environment.matchesProfiles("prod","production"))
            throw new IllegalStateException("The unauthenticated preview is local-only. Enable the accounts profile for deployment.");
        return http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
}
