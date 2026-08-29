package com.example.platform.artifact.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class S3ArtifactAccessGrantProviderTest {

    @Test
    void acceptsRepositoryDefaultDurationSyntax() {
        assertEquals(Duration.ofMinutes(15), S3ArtifactAccessGrantProvider.parseDuration("15m"));
        assertEquals(Duration.ofMinutes(15), S3ArtifactAccessGrantProvider.parseDuration("PT15M"));
    }

    @Test
    void invalidDurationFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> S3ArtifactAccessGrantProvider.parseDuration("eventually"));
    }
}
