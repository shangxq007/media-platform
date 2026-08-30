package com.example.platform.render.app.aaf;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class AafConversionServiceTest {

    @Test
    void missingConverterNeverQueuesOrManufacturesManifest(@TempDir Path tempDir) throws Exception {
        Path aaf = tempDir.resolve("edit.aaf");
        Files.writeString(aaf, "AAF-PLACEHOLDER");

        AafConversionService service = new AafConversionService();
        ReflectionTestUtils.setField(service, "converterEnabled", true);
        ReflectionTestUtils.setField(service, "converterCommand", "");

        assertThrows(AafConversionUnavailableException.class,
                () -> service.enqueue(aaf.toString(), "file:///tmp/fallback.mp4", "tenant-1"));
        assertTrue(service.poll().isEmpty());

        AafConversionJob job = new AafConversionJob(
                "aaf-test", aaf.toString(), "file:///tmp/fallback.mp4", "tenant-1",
                java.time.Instant.now());
        AafConversionResult result = service.process(job);
        assertFalse(result.success());
        assertNull(result.manifestJson());
        assertEquals("FAILED", result.status());
        assertTrue(result.errorMessage().contains("converter-command"));
    }
}
