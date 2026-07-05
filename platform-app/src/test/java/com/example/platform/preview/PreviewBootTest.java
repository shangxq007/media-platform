package com.example.platform.preview;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Test that the application context can start with preview profile.
 * Uses 'test' profile for database configuration.
 */
@SpringBootTest
@ActiveProfiles({"test", "preview"})
class PreviewBootTest {

    @Test
    void contextLoads() {
        // If this test passes, the ApplicationContext starts successfully
        // with the preview profile
    }
}
