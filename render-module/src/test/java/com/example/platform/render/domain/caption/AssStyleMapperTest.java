package com.example.platform.render.domain.caption;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AssStyleMapper — maps CaptionTemplateSpec to ASS subtitle format.
 *
 * <p>Covers: style mapping, color conversion, time formatting, text sanitization,
 * alignment, bounds validation, and safety properties.</p>
 */
class AssStyleMapperTest {

    private AssStyleMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AssStyleMapper();
    }

    // --- 1. Default style maps correctly ---

    @Test
    @DisplayName("Default template maps to safe ASS style with default values")
    void defaultTemplateMapsToSafeAssStyle() {
        AssStyleParams params = mapper.mapToAssStyle(null);

        assertEquals("DejaVu Sans", params.fontFamily());
        assertEquals(24, params.fontSize());
        assertEquals(0, params.bold());
        assertEquals(2, params.alignment()); // bottom-center
        assertEquals(2, params.outlineWidth());
        assertTrue(params.isWithinBounds(), "Default params must be within bounds");
    }

    // --- 2. Color conversion: hex #RRGGBB → ASS &H00BBGGRR ---

    @Test
    @DisplayName("White #FFFFFF maps to ASS &H00FFFFFF (BGR: 255,255,255)")
    void whiteColorMapping() {
        // ASS format: alpha=00, B=FF, G=FF, R=FF → 0x00FFFFFF
        assertEquals(0x00FFFFFFL, mapper.hexToAssColor("#FFFFFF"));
    }

    @Test
    @DisplayName("Black #000000 maps to ASS &H00000000")
    void blackColorMapping() {
        assertEquals(0x00000000L, mapper.hexToAssColor("#000000"));
    }

    @Test
    @DisplayName("Red #FF0000 maps to ASS &H000000FF (B=00,G=00,R=FF)")
    void redColorMapping() {
        // Red: R=FF, G=00, B=00 → ASS: A=00, B=00, G=00, R=FF → 0x000000FF
        assertEquals(0x000000FFL, mapper.hexToAssColor("#FF0000"));
    }

    @Test
    @DisplayName("Blue #0000FF maps to ASS &H00FF0000 (B=FF,G=00,R=00)")
    void blueColorMapping() {
        // Blue: R=00, G=00, B=FF → ASS: A=00, B=FF, G=00, R=00 → 0x00FF0000
        assertEquals(0x00FF0000L, mapper.hexToAssColor("#0000FF"));
    }

    @Test
    @DisplayName("Green #00FF00 maps to ASS &H0000FF00 (B=00,G=FF,R=00)")
    void greenColorMapping() {
        assertEquals(0x0000FF00L, mapper.hexToAssColor("#00FF00"));
    }

    @Test
    @DisplayName("Yellow #FFFF00 maps to ASS &H0000FFFF")
    void yellowColorMapping() {
        // Yellow: R=FF, G=FF, B=00 → ASS: A=00, B=00, G=FF, R=FF → 0x0000FFFF
        assertEquals(0x0000FFFFL, mapper.hexToAssColor("#FFFF00"));
    }

    // --- 3. Font style mapping ---

    @Test
    @DisplayName("Bold font (weight 700+) maps to ASS bold=1")
    void boldFontMapping() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER,
                new FontStyleSpec("DejaVu Sans", 700, "#FFFFFF", "#000000", 2, null),
                24, 2, 1.4, "center");
        CaptionTemplateSpec template = new CaptionTemplateSpec("t1", "test", style);

        AssStyleParams params = mapper.mapToAssStyle(template);
        assertEquals(1, params.bold());
    }

    @Test
    @DisplayName("Normal font (weight < 700) maps to ASS bold=0")
    void normalFontMapping() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER,
                new FontStyleSpec("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                24, 2, 1.4, "center");
        CaptionTemplateSpec template = new CaptionTemplateSpec("t1", "test", style);

        AssStyleParams params = mapper.mapToAssStyle(template);
        assertEquals(0, params.bold());
    }

    @Test
    @DisplayName("Custom font family preserved in ASS style")
    void customFontFamilyPreserved() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER,
                new FontStyleSpec("Liberation Serif", 400, "#FFFFFF", "#000000", 2, null),
                24, 2, 1.4, "center");
        CaptionTemplateSpec template = new CaptionTemplateSpec("t1", "test", style);

        AssStyleParams params = mapper.mapToAssStyle(template);
        assertEquals("Liberation Serif", params.fontFamily());
    }

    // --- 4. Alignment mapping (placement × text-align → ASS numpad) ---

    @Test
    @DisplayName("Bottom-center placement with center align → ASS alignment 2")
    void bottomCenterAlignment() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER, FontStyleSpec.defaults(),
                24, 2, 1.4, "center");
        AssStyleParams params = mapper.mapToAssStyle(new CaptionTemplateSpec("t1", "test", style));
        assertEquals(2, params.alignment());
    }

    @Test
    @DisplayName("Top-center placement with center align → ASS alignment 8")
    void topCenterAlignment() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.TOP_CENTER, FontStyleSpec.defaults(),
                24, 2, 1.4, "center");
        AssStyleParams params = mapper.mapToAssStyle(new CaptionTemplateSpec("t1", "test", style));
        assertEquals(8, params.alignment());
    }

    @Test
    @DisplayName("Center placement with left align → ASS alignment 4")
    void centerLeftAlignment() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.CENTER, FontStyleSpec.defaults(),
                24, 2, 1.4, "left");
        AssStyleParams params = mapper.mapToAssStyle(new CaptionTemplateSpec("t1", "test", style));
        assertEquals(4, params.alignment());
    }

    @Test
    @DisplayName("Bottom placement with right align → ASS alignment 3")
    void bottomRightAlignment() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER, FontStyleSpec.defaults(),
                24, 2, 1.4, "right");
        AssStyleParams params = mapper.mapToAssStyle(new CaptionTemplateSpec("t1", "test", style));
        assertEquals(3, params.alignment());
    }

    @Test
    @DisplayName("Top placement with right align → ASS alignment 9")
    void topRightAlignment() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.TOP_CENTER, FontStyleSpec.defaults(),
                24, 2, 1.4, "right");
        AssStyleParams params = mapper.mapToAssStyle(new CaptionTemplateSpec("t1", "test", style));
        assertEquals(9, params.alignment());
    }

    // --- 5. Font size bounds enforcement ---

    @Test
    @DisplayName("Font size clamped to minimum 8")
    void fontSizeClampedMin() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER, FontStyleSpec.defaults(),
                2, 2, 1.4, "center"); // below MIN_FONT_SIZE
        AssStyleParams params = mapper.mapToAssStyle(new CaptionTemplateSpec("t1", "test", style));
        assertEquals(8, params.fontSize());
        assertTrue(params.isWithinBounds());
    }

    @Test
    @DisplayName("Font size clamped to maximum 200")
    void fontSizeClampedMax() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.BOTTOM_CENTER, FontStyleSpec.defaults(),
                300, 2, 1.4, "center"); // above MAX_FONT_SIZE
        AssStyleParams params = mapper.mapToAssStyle(new CaptionTemplateSpec("t1", "test", style));
        assertEquals(200, params.fontSize());
        assertTrue(params.isWithinBounds());
    }

    // --- 6. Time formatting ---

    @Test
    @DisplayName("Zero seconds formats to 0:00:00.00")
    void zeroTimeFormatting() {
        assertEquals("0:00:00.00", mapper.formatAssTime(0.0));
    }

    @Test
    @DisplayName("1.5 seconds formats to 0:00:01.50")
    void oneAndHalfSecondsFormatting() {
        assertEquals("0:00:01.50", mapper.formatAssTime(1.5));
    }

    @Test
    @DisplayName("61.25 seconds formats to 0:01:01.25")
    void overOneMinuteFormatting() {
        assertEquals("0:01:01.25", mapper.formatAssTime(61.25));
    }

    @Test
    @DisplayName("3661.99 seconds formats to 1:01:01.99")
    void overOneHourFormatting() {
        assertEquals("1:01:01.99", mapper.formatAssTime(3661.99));
    }

    @Test
    @DisplayName("Negative time clamped to 0:00:00.00")
    void negativeTimeClamped() {
        assertEquals("0:00:00.00", mapper.formatAssTime(-5.0));
    }

    @Test
    @DisplayName("Time format matches ASS regex pattern")
    void timeFormatMatchesAssPattern() {
        String time = mapper.formatAssTime(123.45);
        assertTrue(time.matches("\\d:\\d{2}:\\d{2}\\.\\d{2}"),
                "Time format must match ASS H:MM:SS.cc pattern: " + time);
    }

    // --- 7. Dialogue event mapping ---

    @Test
    @DisplayName("Single segment maps to valid dialogue event")
    void singleSegmentMapping() {
        List<CaptionSegmentSpec> segments = List.of(
                new CaptionSegmentSpec(0, 3000, "Hello World"));

        List<AssDialogueEvent> events = mapper.mapToDialogueEvents(segments);
        assertEquals(1, events.size());
        assertEquals("0:00:00.00", events.get(0).start());
        assertEquals("0:00:03.00", events.get(0).end());
        assertEquals("Hello World", events.get(0).text());
        assertTrue(events.get(0).isValid());
    }

    @Test
    @DisplayName("Multiple segments map to ordered dialogue events")
    void multipleSegmentsMapping() {
        List<CaptionSegmentSpec> segments = List.of(
                new CaptionSegmentSpec(0, 2000, "First"),
                new CaptionSegmentSpec(2000, 5000, "Second"));

        List<AssDialogueEvent> events = mapper.mapToDialogueEvents(segments);
        assertEquals(2, events.size());
        assertEquals("First", events.get(0).text());
        assertEquals("0:00:02.00", events.get(0).end());
        assertEquals("0:00:02.00", events.get(1).start());
        assertEquals("Second", events.get(1).text());
    }

    @Test
    @DisplayName("Empty segments returns empty list")
    void emptySegmentsReturnsEmpty() {
        List<AssDialogueEvent> events = mapper.mapToDialogueEvents(List.of());
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("Null segments returns empty list")
    void nullSegmentsReturnsEmpty() {
        List<AssDialogueEvent> events = mapper.mapToDialogueEvents(null);
        assertTrue(events.isEmpty());
    }

    // --- 8. Text sanitization ---

    @Test
    @DisplayName("Backslashes escaped in dialogue text")
    void backslashEscaping() {
        String sanitized = mapper.sanitizeDialogueText("path\\to\\file");
        assertEquals("path\\\\to\\\\file", sanitized);
    }

    @Test
    @DisplayName("Newlines converted to ASS line break \\N")
    void newlineConversion() {
        String sanitized = mapper.sanitizeDialogueText("Line1\nLine2");
        assertEquals("Line1\\NLine2", sanitized);
    }

    @Test
    @DisplayName("Carriage returns removed")
    void carriageReturnRemoval() {
        String sanitized = mapper.sanitizeDialogueText("Text\r\nMore");
        assertEquals("Text\\NMore", sanitized);
    }

    @Test
    @DisplayName("Null text sanitized to empty string")
    void nullTextSanitized() {
        assertEquals("", mapper.sanitizeDialogueText(null));
    }

    // --- 9. Safety: output is bounded ---

    @Test
    @DisplayName("ASS style params always within bounds for valid input")
    void styleParamsAlwaysWithinBounds() {
        // Test with extreme but valid input
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.TOP_CENTER,
                new FontStyleSpec("Arial", 700, "#FF0000", "#0000FF", 10, "#000000"),
                200, 10, 3.0, "right");
        CaptionTemplateSpec template = new CaptionTemplateSpec("t1", "extreme", style);

        AssStyleParams params = mapper.mapToAssStyle(template);
        assertTrue(params.isWithinBounds(), "Extreme valid input must produce bounded output");
        assertTrue(params.fontSize() >= AssStyleParams.MIN_FONT_SIZE);
        assertTrue(params.fontSize() <= AssStyleParams.MAX_FONT_SIZE);
        assertTrue(params.alignment() >= AssStyleParams.MIN_ALIGNMENT);
        assertTrue(params.alignment() <= AssStyleParams.MAX_ALIGNMENT);
    }

    @Test
    @DisplayName("Dialogue events always valid for non-empty text")
    void dialogueEventsAlwaysValid() {
        List<CaptionSegmentSpec> segments = List.of(
                new CaptionSegmentSpec(0, 1000, "Test"),
                new CaptionSegmentSpec(5000, 10000, "Another"));
        List<AssDialogueEvent> events = mapper.mapToDialogueEvents(segments);

        for (AssDialogueEvent event : events) {
            assertTrue(event.isValid(), "All dialogue events must be valid: " + event);
        }
    }

    // --- 10. Integration: full template → ASS mapping ---

    @Test
    @DisplayName("Full template with custom style produces bounded ASS output")
    void fullTemplateMapping() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.TOP_CENTER,
                new FontStyleSpec("Liberation Sans", 700, "#FFFF00", "#000000", 3, null),
                32, 3, 1.2, "center");
        CaptionTemplateSpec template = new CaptionTemplateSpec("tpl-1", "Yellow Bold", style);

        AssStyleParams params = mapper.mapToAssStyle(template);
        assertTrue(params.isWithinBounds());
        assertEquals("Liberation Sans", params.fontFamily());
        assertEquals(32, params.fontSize());
        assertEquals(1, params.bold());
        assertEquals(8, params.alignment()); // top-center
        assertEquals(3, params.outlineWidth());

        // Yellow #FFFF00 → ASS: B=00, G=FF, R=FF → 0x0000FFFF
        assertEquals(0x0000FFFFL, params.primaryColor());
    }

    @Test
    @DisplayName("Complete round-trip: template + segments → ASS style + dialogues")
    void completeRoundTrip() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello"),
                        new CaptionSegmentSpec(3000, 6000, "World")),
                new CaptionTemplateSpec("tpl", "test",
                        new CaptionStyleSpec(CaptionPlacement.BOTTOM_CENTER,
                                new FontStyleSpec("DejaVu Sans", 400,
                                        "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                CaptionOutputProfileSpec.hd1080p(), Map.of());

        AssStyleParams style = mapper.mapToAssStyle(request.effectiveTemplate());
        List<AssDialogueEvent> dialogues = mapper.mapToDialogueEvents(request.captionSegments());

        assertTrue(style.isWithinBounds());
        assertEquals(2, dialogues.size());
        assertTrue(dialogues.stream().allMatch(AssDialogueEvent::isValid));
        assertEquals("Hello", dialogues.get(0).text());
        assertEquals("World", dialogues.get(1).text());
    }
}
