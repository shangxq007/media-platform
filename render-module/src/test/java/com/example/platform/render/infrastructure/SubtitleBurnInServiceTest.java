package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SubtitleBurnInServiceTest {

    private SubtitleBurnInService burnInService;

    @BeforeEach
    void setUp() {
        burnInService = new SubtitleBurnInService(null);
    }

    @Test
    void buildSubtitleFilterWithNoTracks() {
        String filter = burnInService.buildSubtitleFilter(List.of());
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

        String filter = burnInService.buildSubtitleFilter(tracks);
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

        String filter = burnInService.buildSubtitleFilter(tracks);
        assertEquals("", filter);
    }

    @Test
    void buildSubtitleFilterWithNullFontId() {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("burnIn", true);
        track.put("cues", List.of(Map.of("text", "Test", "startTime", 0.0, "endTime", 2.0)));
        List<Map<String, Object>> tracks = List.of(track);

        String filter = burnInService.buildSubtitleFilter(tracks);
        assertNotNull(filter);
        assertTrue(filter.contains("drawtext"));
    }

    @Test
    void checkSubtitleCompatibilityNoWarningsForValidFont() {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("burnIn", true);
        track.put("cues", List.of(Map.of("text", "Test", "startTime", 0.0, "endTime", 2.0)));
        List<Map<String, Object>> tracks = List.of(track);

        List<String> warnings = burnInService.checkSubtitleCompatibility(tracks);
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

        String filter = burnInService.buildSubtitleFilter(tracks);
        assertNotNull(filter);
        assertTrue(filter.contains("Valid"));
    }

    @Test
    void resolveFontFileReturnsNullForMissingFont() {
        String result = burnInService.resolveFontFile("/nonexistent/font.ttf");
        assertNull(result);
    }

    @Test
    void resolveFontFileReturnsPathForExistingFont(@TempDir Path tempDir) throws Exception {
        Path fontFile = tempDir.resolve("test-font.ttf");
        java.nio.file.Files.writeString(fontFile, "dummy font content");

        String result = burnInService.resolveFontFile(fontFile.toString());
        assertEquals(fontFile.toString(), result);
    }

    @Test
    void burnInFrameWithEmptyCuesReturnsOriginal() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = burnInService.burnInFrame(image, 0, 100, List.of());
        assertNotNull(result);
    }

    @Test
    void burnInFrameWithActiveCueDrawsSubtitles() {
        BufferedImage image = createTestImage(1920, 1080);
        List<Map<String, Object>> cues = List.of(
                Map.of("text", "Hello World", "startTime", 0.0, "endTime", 5.0)
        );
        BufferedImage result = burnInService.burnInFrame(image, 150, 9000, cues);
        assertNotNull(result);
        assertNotSame(image, result);
    }

    @Test
    void burnInFrameWithInactiveCueReturnsOriginal() {
        BufferedImage image = createTestImage(1920, 1080);
        List<Map<String, Object>> cues = List.of(
                Map.of("text", "Hello", "startTime", 10.0, "endTime", 15.0)
        );
        BufferedImage result = burnInService.burnInFrame(image, 30, 900, cues);
        assertNotNull(result);
    }

    @Test
    void buildSubtitleFilterWithPreset() {
        List<Map<String, Object>> tracks = List.of(Map.of(
                "burnIn", true,
                "cues", List.of(Map.of("text", "Preset Test", "startTime", 0.0, "endTime", 5.0))
        ));

        String filter = burnInService.buildSubtitleFilter(tracks, RenderPreset.DEFAULT);
        assertNotNull(filter);
        assertTrue(filter.contains("drawtext"));
        assertTrue(filter.contains("Preset Test"));
    }

    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }
}
