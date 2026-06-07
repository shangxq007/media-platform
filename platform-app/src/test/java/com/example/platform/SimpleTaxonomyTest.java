package com.example.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "app.security.enabled=false",
        "app.identity.api-key-auth-enabled=false",
        "app.outbox.dispatch-interval-ms=999999999",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always"
})
class SimpleTaxonomyTest {

    @Test
    void contextLoads() {
        // Just verify the application context loads
        System.out.println("Application context loaded successfully");
    }
}
