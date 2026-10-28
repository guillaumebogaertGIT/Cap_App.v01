package be.cap.backend.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Read local frontend edits on each request; packaged deployments keep bundled assets. */
@Configuration
@Profile("accounts & local-frontend")
class LocalFrontendConfiguration implements WebMvcConfigurer {
    private final String location;

    LocalFrontendConfiguration(@Value("${cap.frontend-location}") String location) {
        this.location = location;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
            .addResourceLocations(location)
            .setCachePeriod(0)
            .resourceChain(false);
        registry.addResourceHandler("/accounts/shared/**")
            .addResourceLocations(location + "css/", location + "js/", location + "img/")
            .setCachePeriod(0)
            .resourceChain(false);
        registry.addResourceHandler("/accounts/**")
            .addResourceLocations(location + "accounts/")
            .setCachePeriod(0)
            .resourceChain(false);
    }
}
