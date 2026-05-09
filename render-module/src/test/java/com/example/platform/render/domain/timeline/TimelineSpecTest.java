package com.example.platform.render.domain.timeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineSpecTest {

    @Test
    void shouldCreateMinimalTimeline() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test Timeline", output);

        assertEquals("tl-1", timeline.id());
        assertEquals("Test Timeline", timeline.name());
        assertEquals(output, timeline.outputSpec());
        assertEquals(1, timeline.tracks().size());
        assertEquals(TimelineTrack.TrackType.VIDEO, timeline.tracks().get(0).type());
    }

    @Test
    void shouldValidateValidTimeline() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(track),
                List.of(), output, 10.0, java.util.Map.of());

        TimelineValidationResult result = timeline.validate();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void shouldRejectTimelineWithNoTracks() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(),
                List.of(), output, 0, java.util.Map.of());

        TimelineValidationResult result = timeline.validate();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("at least one track")));
    }

    @Test
    void shouldRejectTimelineWithNoOutputSpec() {
        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(),
                List.of(), null, 0, java.util.Map.of());

        TimelineValidationResult result = timeline.validate();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Output specification")));
    }

    @Test
    void shouldRejectClipWithInvalidTiming() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        // out < in → invalid
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 10.0, 5.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(track),
                List.of(), output, 0, java.util.Map.of());

        TimelineValidationResult result = timeline.validate();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("invalid timing")));
    }

    @Test
    void shouldWarnAboutEmptyTrack() {
        TimelineTrack track = TimelineTrack.of("tr-1", "Empty", TimelineTrack.TrackType.VIDEO);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(track),
                List.of(), output, 0, java.util.Map.of());

        TimelineValidationResult result = timeline.validate();
        assertTrue(result.valid());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("no clips")));
    }

    @Test
    void shouldComputeDuration() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineClip clip1 = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineClip clip2 = TimelineClip.of("clip-2", asset, 5.0, 0.0, 15.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip1, clip2), false, false);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(track),
                List.of(), output, 0, java.util.Map.of());

        assertEquals(20.0, timeline.computeDuration(), 0.001);
    }

    @Test
    void shouldParseOutputResolution() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        assertEquals(1920, output.width());
        assertEquals(1080, output.height());
    }

    @Test
    void shouldRejectTextOverlayWithEmptyText() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("ov-1", "", 0.0, 5.0);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(),
                List.of(overlay), output, 0, java.util.Map.of());

        TimelineValidationResult result = timeline.validate();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("empty text")));
    }

    @Test
    void shouldRejectTextOverlayWithInvalidDuration() {
        TimelineTextOverlay overlay = new TimelineTextOverlay(
                "ov-1", "Hello", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", 0.0, -1.0, null);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test", null, List.of(),
                List.of(overlay), output, 0, java.util.Map.of());

        TimelineValidationResult result = timeline.validate();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("invalid duration")));
    }
}
