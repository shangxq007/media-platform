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
    void checkSubtitleCompatibilityNoWarningsWhenNoFontIsRequested() {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("burnIn", true);
        track.put("cues", List.of(Map.of("text", "Test", "startTime", 0.0, "endTime", 2.0)));

        List<String> warnings = burnInService.checkSubtitleCompatibility(List.of(track));

        assertTrue(warnings.stream().noneMatch(w -> w.contains("FONT_MISSING")));
    }

    @Test
    void resolveFontFileReturnsNullForMissingFont() {
        assertNull(burnInService.resolveFontFile("/nonexistent/font.ttf"));
    }

    @Test
    void resolveFontFileReturnsPathForExistingFont(@TempDir Path tempDir) throws Exception {
        Path fontFile = tempDir.resolve("test-font.ttf");
        java.nio.file.Files.writeString(fontFile, "dummy font content");

        assertEquals(fontFile.toString(), burnInService.resolveFontFile(fontFile.toString()));
    }

    @Test
    void burnInFrameWithEmptyCuesReturnsOriginal() {
        BufferedImage image = createTestImage(1920, 1080);
        assertSame(image, burnInService.burnInFrame(image, 0, 100, List.of()));
    }

    @Test
    void burnInFrameWithActiveCueDrawsSemanticText() {
        BufferedImage image = createTestImage(1920, 1080);
        List<Map<String, Object>> cues = List.of(
                Map.of("text", "Hello World", "startTime", 0.0, "endTime", 5.0));

        BufferedImage result = burnInService.burnInFrame(image, 150, 9000, cues);

        assertNotNull(result);
        assertNotSame(image, result);
    }

    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }
}
