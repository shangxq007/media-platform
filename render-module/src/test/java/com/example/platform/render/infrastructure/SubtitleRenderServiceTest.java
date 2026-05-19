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
        SubtitleBurnInService burnInService = new SubtitleBurnInService(null);
        subtitleRenderService = new SubtitleRenderService(burnInService);
    }

    @Test
    void buildSubtitleFilterWithNoTracks() {
        String filter = subtitleRenderService.buildSubtitleFilter(List.of());
        assertEquals("", filter);
    }

    @Test
    void buildSubtitleFilterWithBurnInTrack() {
        List<Map<String, Object>> tracks = List.of(Map.of(
                "burnIn", true,
                "cues", List.of(
                        Map.of("text", "Hello", "startTime", 1.0, "endTime", 3.0),
                        Map.of("text", "World", "startTime", 4.0, "endTime", 6.0)
                )
        ));

        String filter = subtitleRenderService.buildSubtitleFilter(tracks);
        assertNotNull(filter);
        assertTrue(filter.contains("drawtext"));
        assertTrue(filter.contains("Hello"));
        assertTrue(filter.contains("World"));
    }

    @Test
    void buildSubtitleFilterSkipsExternalTracks() {
        List<Map<String, Object>> tracks = List.of(Map.of(
                "burnIn", false,
                "cues", List.of(Map.of("text", "External", "startTime", 1.0, "endTime", 3.0))
        ));

        String filter = subtitleRenderService.buildSubtitleFilter(tracks);
        assertEquals("", filter);
    }

    @Test
    void buildSubtitleFilterWithNullFontId() {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("burnIn", true);
        track.put("cues", List.of(Map.of("text", "Test 测试", "startTime", 0.0, "endTime", 2.0)));
        List<Map<String, Object>> tracks = List.of(track);

        String filter = subtitleRenderService.buildSubtitleFilter(tracks);
        assertNotNull(filter);
        assertTrue(filter.contains("drawtext"));
    }

    @Test
    void checkSubtitleCompatibilityNoWarningsForValidFont() {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("burnIn", true);
        track.put("cues", List.of(Map.of("text", "Test", "startTime", 0.0, "endTime", 2.0)));
        List<Map<String, Object>> tracks = List.of(track);

        List<String> warnings = subtitleRenderService.checkSubtitleCompatibility(tracks);
        assertTrue(warnings.stream().noneMatch(w -> w.contains("FONT_MISSING")));
    }

    @Test
    void buildSubtitleFilterHandlesEmptyCues() {
        List<Map<String, Object>> tracks = List.of(Map.of(
                "burnIn", true,
                "cues", List.of(
                        Map.of("text", "", "startTime", 0.0, "endTime", 2.0),
                        Map.of("text", "Valid", "startTime", 3.0, "endTime", 5.0)
                )
        ));

        String filter = subtitleRenderService.buildSubtitleFilter(tracks);
        assertNotNull(filter);
        assertTrue(filter.contains("Valid"));
    }

    @Test
    void resolveFontFileReturnsNullForMissingFont() {
        String result = subtitleRenderService.resolveFontFile("/nonexistent/font.ttf");
        assertNull(result);
    }

    @Test
    void resolveFontFileReturnsPathForExistingFont(@TempDir Path tempDir) throws Exception {
        // Create a dummy file
        Path fontFile = tempDir.resolve("test-font.ttf");
        java.nio.file.Files.writeString(fontFile, "dummy font content");

        String result = subtitleRenderService.resolveFontFile(fontFile.toString());
        assertEquals(fontFile.toString(), result);
    }
}
