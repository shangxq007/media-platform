package com.example.platform.render.app.aaf;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class AafConversionServiceTest {

    @Test
    void stubManifestFromBinaryAaf(@TempDir Path tempDir) throws Exception {
        Path aaf = tempDir.resolve("edit.aaf");
        Files.writeString(aaf, "AAF-PLACEHOLDER");

        AafConversionService service = new AafConversionService();
        ReflectionTestUtils.setField(service, "converterEnabled", true);
        ReflectionTestUtils.setField(service, "converterCommand", "");

        String conversionId = service.enqueue(aaf.toString(), "file:///tmp/fallback.mp4", "tenant-1");
        AafConversionJob job = service.poll().orElseThrow();
        assertEquals(conversionId, job.conversionId());

        AafConversionResult result = service.process(job);
        assertTrue(result.success());
        assertTrue(result.manifestJson().contains("aaf-stub"));
        assertEquals("MANIFEST_JSON", result.status());
    }
}
