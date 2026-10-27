package be.cap.backend;

import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Optional native PostgreSQL verification; defaults remain isolated H2 tests. */
final class PostgresTestDatabase {
    private static final String RUN = UUID.randomUUID().toString().replace("-", "").substring(0,8);
    static void configure(DynamicPropertyRegistry properties,String group) {
        String url=System.getenv("CAP_TEST_POSTGRES_URL");
        if(url==null||url.isBlank()) {
            if ("true".equalsIgnoreCase(System.getenv("CAP_REQUIRE_POSTGRES_TESTS"))) throw new IllegalStateException("CAP_TEST_POSTGRES_URL is required; PostgreSQL verification must not fall back to H2.");
            return;
        }
        String schema="cap_test_"+group+"_"+RUN;
        properties.add("spring.datasource.url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+schema);
        properties.add("spring.datasource.username",()->System.getenv().getOrDefault("CAP_TEST_POSTGRES_USER","cap_test"));
        properties.add("spring.datasource.password",()->System.getenv().getOrDefault("CAP_TEST_POSTGRES_PASSWORD",""));
        properties.add("spring.flyway.schemas",()->schema);
        properties.add("spring.flyway.default-schema",()->schema);
    }
}
