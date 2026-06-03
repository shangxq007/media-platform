package com.example.platform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class SimpleTaxonomyTest {

    @Test
    void contextLoads() {
        // Just verify the application context loads
        System.out.println("Application context loaded successfully");
    }
}