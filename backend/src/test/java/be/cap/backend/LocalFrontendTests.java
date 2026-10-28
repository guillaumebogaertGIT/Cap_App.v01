package be.cap.backend;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles({"accounts", "local-frontend"})
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:localfrontend;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class LocalFrontendTests extends AccountApiTests {
    @TempDir static Path frontend;

    @DynamicPropertySource static void frontendPath(DynamicPropertyRegistry registry) throws Exception {
        Path source = Path.of("../frontend");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path target = frontend.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else Files.copy(path, target);
            }
        }
        registry.add("cap.frontend-location", () -> frontend.toUri().toString());
    }

    @Test void savedFilesAppearWithoutRestartAndKeepAccountInjection() throws Exception {
        var coach = client();
        login(coach, "coach@example.test", "Coach-test-password-123");
        var template = frontend.resolve("index.html");
        Files.writeString(template, Files.readString(template).replace("<head>", "<head><!-- live-edit -->"));
        var page = request(coach, "GET", "/app/index.html", null, null, false);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("<!-- live-edit -->"));
        assertTrue(page.body().contains("cap-account-data"));
        assertTrue(page.body().contains("js/real-auth.js"));
        Files.writeString(frontend.resolve("live-check.css"), "body { color: red; }");
        var first = request(coach, "GET", "/app/live-check.css", null, null, false);
        assertEquals(200, first.statusCode());
        assertTrue(first.headers().firstValue("cache-control").orElse("").contains("no-store"));
        Files.writeString(frontend.resolve("live-check.css"), "body { color: blue; }");
        assertTrue(request(coach, "GET", "/app/live-check.css", null, null, false).body().contains("blue"));
        assertEquals(302, request(client(), "GET", "/app/index.html", null, null, false).statusCode());
        var login = frontend.resolve("accounts/index.html");
        Files.writeString(login, Files.readString(login).replace("<head>", "<head><!-- live-login -->"));
        var loginPage = request(client(), "GET", "/accounts/index.html", null, null, false);
        assertEquals(200, loginPage.statusCode());
        assertTrue(loginPage.body().contains("<!-- live-login -->"));
        assertTrue(loginPage.headers().firstValue("cache-control").orElse("").contains("no-store"));
        Files.writeString(frontend.resolve("css/live-shared.css"), "/* shared stylesheet edit */");
        assertTrue(request(client(), "GET", "/accounts/shared/live-shared.css", null, null, false).body().contains("shared stylesheet edit"));
        Files.writeString(frontend.resolve("accounts/live-check.js"), "/* login script edit */");
        assertTrue(request(client(), "GET", "/accounts/live-check.js", null, null, false).body().contains("login script edit"));
    }
}
