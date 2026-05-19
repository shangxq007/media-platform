package com.example.platform.render.app;

import com.example.platform.render.infrastructure.EffectMappingService;
import com.example.platform.render.infrastructure.SubtitleBurnInService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedEffectsPipelineTest {

    private AdvancedEffectsPipeline pipeline;

    @BeforeEach
    void setUp() {
        EffectMappingService effectMapping = new EffectMappingService();
        SubtitleBurnInService subtitleBurnInService = new SubtitleBurnInService(null);
        pipeline = new AdvancedEffectsPipeline(effectMapping, subtitleBurnInService);
    }

    @Test
    void applyFilterChainWithEmptyEffectsReturnsOriginal() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilterChain(image, 0, 100, List.of());
        assertSame(image, result);
    }

    @Test
    void applyBlurFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "blur",
                Map.of("radius", 5.0f));
        assertNotNull(result);
        assertEquals(1920, result.getWidth());
        assertEquals(1080, result.getHeight());
    }

    @Test
    void applyVignetteFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "vignette",
                Map.of("strength", 0.5f));
        assertNotNull(result);
    }

    @Test
    void applyBrightnessFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "brightness",
                Map.of("value", 0.3f));
        assertNotNull(result);
    }

    @Test
    void applyContrastFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "contrast",
                Map.of("value", 1.5f));
        assertNotNull(result);
    }

    @Test
    void applyGrayscaleFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "grayscale", Map.of());
        assertNotNull(result);
    }

    @Test
    void applySepiaFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "sepia", Map.of());
        assertNotNull(result);
    }

    @Test
    void applyChromaticFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 10, 100, "filter", "chromatic",
                Map.of("offset", 5));
        assertNotNull(result);
    }

    @Test
    void applyColorGradeFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "color-grade",
                Map.of("shadowsR", 1.2f, "midtonesG", 1.0f, "highlightsB", 0.9f));
        assertNotNull(result);
    }

    @Test
    void applySharpenFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "sharpen", Map.of());
        assertNotNull(result);
    }

    @Test
    void applySaturationFilter() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "saturation",
                Map.of("value", 0.5f));
        assertNotNull(result);
    }

    @Test
    void applyFadeInTransition() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 10, 100, "transition", "fade_in",
                Map.of("duration", 0.5));
        assertNotNull(result);
    }

    @Test
    void applyFadeOutTransition() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 10, 100, "transition", "fade_out",
                Map.of("duration", 0.5));
        assertNotNull(result);
    }

    @Test
    void applyDissolveTransition() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 10, 100, "transition", "dissolve",
                Map.of("duration", 0.5));
        assertNotNull(result);
    }

    @Test
    void applyWipeTransition() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 10, 100, "transition", "wipe",
                Map.of("duration", 0.5));
        assertNotNull(result);
    }

    @Test
    void applySlideTransition() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 10, 100, "transition", "slide",
                Map.of("duration", 0.5));
        assertNotNull(result);
    }

    @Test
    void applyZoomTransition() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 10, 100, "transition", "zoom",
                Map.of("duration", 0.5));
        assertNotNull(result);
    }

    @Test
    void applySubtitleBurnIn() {
        BufferedImage image = createTestImage(1920, 1080);
        List<Map<String, Object>> cues = List.of(
                Map.of("text", "Hello World", "startTime", 0.0, "endTime", 5.0)
        );
        BufferedImage result = pipeline.applyFilter(image, 150, 9000, "overlay", "subtitle_burn_in",
                Map.of("cues", cues));
        assertNotNull(result);
    }

    @Test
    void applyWatermarkOverlay() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "overlay", "watermark",
                Map.of("text", "Watermark", "opacity", 0.3f));
        assertNotNull(result);
    }

    @Test
    void applyPictureInPicture() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "overlay", "pip", Map.of());
        assertNotNull(result);
    }

    @Test
    void applyFilterChainWithMultipleEffects() {
        BufferedImage image = createTestImage(1920, 1080);
        List<Map<String, Object>> effects = List.of(
                Map.of("type", "filter", "name", "blur", "params", Map.of("radius", 3.0f)),
                Map.of("type", "filter", "name", "vignette", "params", Map.of("strength", 0.4f)),
                Map.of("type", "transition", "name", "fade_in", "params", Map.of("duration", 0.5))
        );
        BufferedImage result = pipeline.applyFilterChain(image, 10, 100, effects);
        assertNotNull(result);
        assertEquals(1920, result.getWidth());
        assertEquals(1080, result.getHeight());
    }

    @Test
    void applyFilterWithUnknownTypeReturnsOriginal() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "unknown", "effect", Map.of());
        assertSame(image, result);
    }

    @Test
    void applyFilterWithUnknownFilterReturnsOriginal() {
        BufferedImage image = createTestImage(1920, 1080);
        BufferedImage result = pipeline.applyFilter(image, 0, 100, "filter", "nonexistent", Map.of());
        assertSame(image, result);
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
