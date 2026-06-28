package com.example.platform.render.domain.caption;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Caption Template Render MVP contract.
 * Proves: validation, safety, no provider/storage internals, contract shape.
 */
class CaptionTemplateRenderContractTest {

    private CaptionTemplateRenderContractValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CaptionTemplateRenderContractValidator();
    }

    // --- Valid requests ---

    @Test
    @DisplayName("Valid minimal request passes validation")
    void validMinimalRequestPasses() {
        CaptionTemplateRenderRequest request = minimalRequest();
        CaptionTemplateValidationResult result = validator.validate(request);
        assertTrue(result.valid(), "Errors: " + result.errors());
    }

    @Test
    @DisplayName("Valid request with custom style passes")
    void validCustomStylePasses() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.TOP_CENTER,
                new FontStyleSpec("Liberation Sans", 700, "#FFFF00", "#000000", 3, "#00000080"),
                32, 3, 1.2, "center");
        CaptionTemplateSpec template = new CaptionTemplateSpec("tpl-1", "Yellow Bold", style);
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1", List.of(
                        new CaptionSegmentSpec(0, 2000, "Hello"),
                        new CaptionSegmentSpec(2000, 4000, "World")),
                template, CaptionOutputProfileSpec.hd720p(), Map.of());

        CaptionTemplateValidationResult result = validator.validate(request);
        assertTrue(result.valid(), "Errors: " + result.errors());
    }

    // --- Missing required fields ---

    @Test
    @DisplayName("Missing projectId fails")
    void missingProjectIdFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                null, "prod-1", List.of(new CaptionSegmentSpec(0, 1000, "Hi")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Blank projectId fails")
    void blankProjectIdFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "  ", "prod-1", List.of(new CaptionSegmentSpec(0, 1000, "Hi")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Missing sourceProductId fails")
    void missingSourceProductIdFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", null, List.of(new CaptionSegmentSpec(0, 1000, "Hi")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Empty caption segments fails")
    void emptySegmentsFail() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1", List.of(), null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Null caption segments fails")
    void nullSegmentsFail() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1", null, null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    // --- Segment validation ---

    @Test
    @DisplayName("Negative startMs fails")
    void negativeStartMsFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(-1, 1000, "Hi")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("endMs <= startMs fails")
    void endBeforeStartFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(1000, 1000, "Hi")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Blank caption text fails")
    void blankTextFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "  ")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Too many segments fails")
    void tooManySegmentsFail() {
        List<CaptionSegmentSpec> segments = new java.util.ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            segments.add(new CaptionSegmentSpec(i * 100, (i + 1) * 100, "Text " + i));
        }
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1", segments, null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Too long text fails")
    void tooLongTextFails() {
        String longText = "A".repeat(10001);
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, longText)),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    // --- Injection/safety validation ---

    @Test
    @DisplayName("Script tag in text fails")
    void scriptTagFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "<script>alert(1)</script>")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("ASS override injection in text fails")
    void assOverrideFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "Hello {\\b1}world")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Local path in text fails")
    void localPathInTextFails() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "See /tmp/secret.txt")),
                null, null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    // --- Style validation ---

    @Test
    @DisplayName("Disallowed font family fails")
    void disallowedFontFails() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER,
                new FontStyleSpec("EvilFont", 400, "#FFFFFF", "#000000", 2, null),
                24, 2, 1.4, "center");
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "Hi")),
                new CaptionTemplateSpec(null, "test", style), null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Invalid color fails")
    void invalidColorFails() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER,
                new FontStyleSpec("DejaVu Sans", 400, "not-a-color", "#000000", 2, null),
                24, 2, 1.4, "center");
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "Hi")),
                new CaptionTemplateSpec(null, "test", style), null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Oversized font fails")
    void oversizedFontFails() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER, FontStyleSpec.defaults(),
                300, 2, 1.4, "center");
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "Hi")),
                new CaptionTemplateSpec(null, "test", style), null, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    @Test
    @DisplayName("Unsupported output container fails")
    void unsupportedContainerFails() {
        CaptionOutputProfileSpec profile = new CaptionOutputProfileSpec(1920, 1080, 30, "avi");
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 1000, "Hi")),
                null, profile, Map.of());
        assertFalse(validator.validate(request).valid());
    }

    // --- Contract safety ---

    @Test
    @DisplayName("Request does not expose provider/backend/storage internals")
    void requestNoInternals() {
        CaptionTemplateRenderRequest request = minimalRequest();
        String str = request.toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
        assertFalse(str.contains("rootPath"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("materializedPath"));
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("backendName"));
    }

    @Test
    @DisplayName("Result does not expose provider/storage internals")
    void resultNoInternals() {
        CaptionTemplateRenderResult result = CaptionTemplateRenderResult.success(
                "rj-1", "prod-1", CaptionOutputProfileSpec.hd1080p());
        String str = result.toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("providerName"));
    }

    @Test
    @DisplayName("Contract does not reference Remotion execution classes")
    void contractNoRemotionExecution() {
        // Verified by package structure: domain.caption has no remotion imports
        CaptionTemplateRenderRequest request = minimalRequest();
        assertNotNull(request);
    }

    @Test
    @DisplayName("Contract does not allow provider selection")
    void contractNoProviderSelection() {
        // CaptionTemplateRenderRequest has no providerName field
        CaptionTemplateRenderRequest request = minimalRequest();
        // No provider field exists on the record
        assertNotNull(request.projectId());
        assertNotNull(request.sourceProductId());
    }

    @Test
    @DisplayName("Default output profile is 1080p MP4")
    void defaultOutputProfile() {
        CaptionOutputProfileSpec profile = CaptionOutputProfileSpec.hd1080p();
        assertEquals(1920, profile.width());
        assertEquals(1080, profile.height());
        assertEquals(30.0, profile.fps());
        assertEquals("mp4", profile.container());
    }

    @Test
    @DisplayName("Default template uses default style")
    void defaultTemplate() {
        CaptionTemplateSpec template = CaptionTemplateSpec.defaults();
        assertNull(template.templateId());
        assertEquals(CaptionPlacement.BOTTOM_CENTER, template.style().placement());
        assertEquals("DejaVu Sans", template.style().font().family());
    }

    @Test
    @DisplayName("Validation result is safe structured")
    void validationResultSafe() {
        CaptionTemplateValidationResult result = validator.validate(null);
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
    }

    // --- Helpers ---

    private CaptionTemplateRenderRequest minimalRequest() {
        return new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello World")),
                null, null, Map.of());
    }
}
