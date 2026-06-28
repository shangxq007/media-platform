package com.example.platform.render.domain.caption;

import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CaptionTemplateTimelineAdapter.
 * Proves: mapping preserves timing, text, style, source product, output profile.
 */
class CaptionTemplateTimelineAdapterTest {

    private CaptionTemplateTimelineAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CaptionTemplateTimelineAdapter();
    }

    @Test
    @DisplayName("Valid request maps to TimelineSpec")
    void validRequestMaps() {
        CaptionTemplateRenderRequest request = minimalRequest();
        TimelineSpec spec = adapter.adapt(request);
        assertNotNull(spec);
        assertNotNull(spec.id());
        assertFalse(spec.tracks().isEmpty());
    }

    @Test
    @DisplayName("SourceProductId preserved in metadata")
    void sourceProductIdPreserved() {
        CaptionTemplateRenderRequest request = minimalRequest();
        TimelineSpec spec = adapter.adapt(request);
        assertEquals("prod-source-1", spec.metadata().get("sourceProductId"));
    }

    @Test
    @DisplayName("Caption segments map to text overlays")
    void segmentsMapToOverlays() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello"),
                        new CaptionSegmentSpec(3000, 6000, "World")),
                null, null, Map.of());

        TimelineSpec spec = adapter.adapt(request);

        assertFalse(spec.textOverlays().isEmpty());
        assertEquals(2, spec.textOverlays().size());
        assertEquals("Hello", spec.textOverlays().get(0).text());
        assertEquals("World", spec.textOverlays().get(1).text());
    }

    @Test
    @DisplayName("Timing preserved in overlays")
    void timingPreserved() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(1000, 4000, "Test")),
                null, null, Map.of());

        TimelineSpec spec = adapter.adapt(request);

        assertEquals(1.0, spec.textOverlays().get(0).startTime());
        assertEquals(3.0, spec.textOverlays().get(0).duration());
    }

    @Test
    @DisplayName("Default style applied when no template")
    void defaultStyleApplied() {
        CaptionTemplateRenderRequest request = minimalRequest();
        TimelineSpec spec = adapter.adapt(request);

        assertEquals("DejaVu Sans", spec.textOverlays().get(0).fontFamily());
        assertEquals(24, spec.textOverlays().get(0).fontSize());
        assertEquals("#FFFFFF", spec.textOverlays().get(0).color());
    }

    @Test
    @DisplayName("Custom style mapped correctly")
    void customStyleMapped() {
        CaptionStyleSpec style = new CaptionStyleSpec(
                CaptionPlacement.TOP_CENTER,
                new FontStyleSpec("Liberation Sans", 700, "#FFFF00", "#000000", 3, null),
                32, 3, 1.2, "center");
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 2000, "Test")),
                new CaptionTemplateSpec(null, "custom", style), null, Map.of());

        TimelineSpec spec = adapter.adapt(request);

        assertEquals("Liberation Sans", spec.textOverlays().get(0).fontFamily());
        assertEquals(32, spec.textOverlays().get(0).fontSize());
        assertEquals("#FFFF00", spec.textOverlays().get(0).color());
        assertEquals("top", spec.textOverlays().get(0).positionY());
    }

    @Test
    @DisplayName("Output profile mapped to TimelineOutputSpec")
    void outputProfileMapped() {
        CaptionOutputProfileSpec profile = CaptionOutputProfileSpec.hd720p();
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 2000, "Test")),
                null, profile, Map.of());

        TimelineSpec spec = adapter.adapt(request);

        assertEquals("mp4", spec.outputSpec().format());
        assertEquals("1280x720", spec.outputSpec().resolution());
        assertEquals(30.0, spec.outputSpec().frameRate());
    }

    @Test
    @DisplayName("Default output profile is 1080p")
    void defaultOutputProfile() {
        CaptionTemplateRenderRequest request = minimalRequest();
        TimelineSpec spec = adapter.adapt(request);

        assertEquals("1920x1080", spec.outputSpec().resolution());
        assertEquals(30.0, spec.outputSpec().frameRate());
    }

    @Test
    @DisplayName("Video track created with source product")
    void videoTrackCreated() {
        CaptionTemplateRenderRequest request = minimalRequest();
        TimelineSpec spec = adapter.adapt(request);

        assertFalse(spec.tracks().isEmpty());
        assertEquals(TimelineSpec.create("t", "n",
                com.example.platform.render.domain.timeline.TimelineOutputSpec.mp4_1080p30())
                .tracks().get(0).type(),
                spec.tracks().get(0).type());
    }

    @Test
    @DisplayName("Total duration derived from segments")
    void totalDurationDerived() {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 5000, "A"),
                        new CaptionSegmentSpec(5000, 10000, "B")),
                null, null, Map.of());

        TimelineSpec spec = adapter.adapt(request);

        assertEquals(10.0, spec.totalDuration());
    }

    // --- Helpers ---

    private CaptionTemplateRenderRequest minimalRequest() {
        return new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello World")),
                null, null, Map.of());
    }
}
