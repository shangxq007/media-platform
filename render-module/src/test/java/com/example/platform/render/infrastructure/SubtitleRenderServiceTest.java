package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubtitleRenderServiceTest {

    private SubtitleRenderService subtitleRenderService;

    @BeforeEach
    void setUp() {
        subtitleRenderService = new SubtitleRenderService(new SubtitleBurnInService(null));
    }

    @Test
    void delegatesProviderNeutralCompatibilityChecks() {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("burnIn", true);
        track.put("cues", List.of(Map.of("text", "Test", "startTime", 0.0, "endTime", 2.0)));

        List<String> warnings = subtitleRenderService.checkSubtitleCompatibility(List.of(track));

        assertTrue(warnings.stream().noneMatch(w -> w.contains("FONT_MISSING")));
    }

    @Test
    void resolveFontFileReturnsNullForMissingFont() {
        assertNull(subtitleRenderService.resolveFontFile("/nonexistent/font.ttf"));
    }

    @Test
    void resolveFontFileReturnsPathForExistingFont(@TempDir Path tempDir) throws Exception {
        Path fontFile = tempDir.resolve("test-font.ttf");
        java.nio.file.Files.writeString(fontFile, "dummy font content");

        assertEquals(fontFile.toString(), subtitleRenderService.resolveFontFile(fontFile.toString()));
    }
}
