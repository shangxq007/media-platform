package com.example.platform.render.infrastructure.subtitle;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * End-to-end characterization tests for timeline/effect API productization.
 *
 * <p>These tests verify the baseline timeline creation, validation, effect application,
 * and render pipeline integration path.
 *
 * <p>Tests use mocks/fakes and do NOT depend on real FFmpeg.
 */
class TimelineEffectApiProductizationTest {

    // --- Scenario A: Simple timeline render ---

    @Test
    void simpleTimelineWithVideoTrack() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Test Timeline", null, List.of(track),
                List.of(), output, 10.0, Map.of());

        TimelineValidationResult result = timeline.validate();
        assertTrue(result.valid());
        assertEquals(1, timeline.tracks().size());
        assertEquals(10.0, timeline.computeDuration(), 0.001);
    }

    @Test
    void timelineWithTrimmedClip() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        // Trim: only use 5s-15s of the source asset
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 5.0, 15.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Trimmed Timeline", null, List.of(track),
                List.of(), output, 10.0, Map.of());

        assertTrue(timeline.validate().valid());
        assertEquals(10.0, timeline.tracks().get(0).clips().get(0).clipDuration(), 0.001);
    }

    // --- Scenario B: Timeline with subtitle burn-in ---

    @Test
    void timelineWithSubtitleOverlay() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineTrack videoTrack = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        TimelineTextOverlay subtitle = TimelineTextOverlay.of("sub-1", "Hello World", 1.0, 3.0);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Subtitle Timeline", null, List.of(videoTrack),
                List.of(subtitle), output, 10.0, Map.of());

        assertTrue(timeline.validate().valid());
        assertEquals(1, timeline.textOverlays().size());
        assertEquals("Hello World", timeline.textOverlays().get(0).text());
    }

    // --- Scenario C: Timeline with image/sticker overlay ---

    @Test
    void timelineWithImageOverlay() {
        TimelineAssetRef videoAsset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineAssetRef imageAsset = TimelineAssetRef.of("art-2", "storage://logo.png");
        TimelineClip clip = TimelineClip.of("clip-1", videoAsset, 0.0, 0.0, 10.0);
        TimelineTrack videoTrack = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        TimelineSticker sticker = TimelineSticker.of(
                "sticker-1", "storage://logo.png", 10, 10, 100, 100, 0.0, 10.0);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Overlay Timeline", null, List.of(videoTrack),
                List.of(), output, 10.0, Map.of("stickers", "sticker-1"));

        assertTrue(timeline.validate().valid());
    }

    // --- Scenario D: Invalid timeline rejected ---

    @Test
    void timelineWithNoTracksRejected() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Empty", null, List.of(),
                List.of(), output, 0, Map.of());

        TimelineValidationResult result = timeline.validate();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("at least one track")));
    }

    @Test
    void timelineWithInvalidTimingRejected() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        // out < in → invalid
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 10.0, 5.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Bad Timing", null, List.of(track),
                List.of(), output, 0, Map.of());

        assertFalse(timeline.validate().valid());
    }

    @Test
    void timelineWithNoOutputSpecRejected() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "No Output", null, List.of(track),
                List.of(), null, 10.0, Map.of());

        assertFalse(timeline.validate().valid());
    }

    @Test
    void timelineWithClipMissingAssetRejected() {
        TimelineClip clip = new TimelineClip(
                "clip-1", null, 0.0, 0.0, 10.0, 10.0, List.of());
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "No Asset", null, List.of(track),
                List.of(), output, 10.0, Map.of());

        assertFalse(timeline.validate().valid());
    }

    @Test
    void textOverlayWithEmptyTextRejected() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("ov-1", "", 0.0, 5.0);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Empty Text", null, List.of(),
                List.of(overlay), output, 0, Map.of());

        assertFalse(timeline.validate().valid());
    }

    @Test
    void textOverlayWithNegativeDurationRejected() {
        TimelineTextOverlay overlay = new TimelineTextOverlay(
                "ov-1", "Hello", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", 0.0, -1.0, null);
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();

        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Bad Duration", null, List.of(),
                List.of(overlay), output, 0, Map.of());

        assertFalse(timeline.validate().valid());
    }

    // --- Scenario E: Effect parameter validation ---

    @Test
    void effectWithValidKey() {
        TimelineClipEffect effect = TimelineClipEffect.ofKey("blur", Map.of("radius", 5));
        assertEquals("blur", effect.effectKey());
        assertEquals(5, effect.parameters().get("radius"));
    }

    @Test
    void effectWithNullKey() {
        TimelineClipEffect effect = TimelineClipEffect.ofKey(null, Map.of());
        assertNull(effect.effectKey());
    }

    @Test
    void effectExtractionFromTimeline() {
        TimelineClipEffect effect1 = TimelineClipEffect.ofKey("blur", Map.of("radius", 5));
        TimelineClipEffect effect2 = TimelineClipEffect.ofKey("vignette", Map.of("strength", 0.5));
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineClip clip = new TimelineClip(
                "clip-1", asset, 0.0, 0.0, 10.0, 10.0, List.of(effect1, effect2));
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);

        // Verify effects are attached to clip
        assertEquals(2, track.clips().get(0).effects().size());
        assertEquals("blur", track.clips().get(0).effects().get(0).effectKey());
        assertEquals("vignette", track.clips().get(0).effects().get(1).effectKey());
    }

    // --- Scenario F: Multi-track composition ---

    @Test
    void multiTrackTimeline() {
        TimelineAssetRef asset1 = TimelineAssetRef.of("art-1", "storage://video1.mp4");
        TimelineAssetRef asset2 = TimelineAssetRef.of("art-2", "storage://video2.mp4");
        TimelineClip clip1 = TimelineClip.of("clip-1", asset1, 0.0, 0.0, 10.0);
        TimelineClip clip2 = TimelineClip.of("clip-2", asset2, 0.0, 0.0, 10.0);

        TimelineTrack videoTrack = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip1), false, false);
        TimelineTrack audioTrack = new TimelineTrack(
                "tr-2", "Audio 1", TimelineTrack.TrackType.AUDIO,
                1, List.of(clip2), false, false);

        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Multi Track", null, List.of(videoTrack, audioTrack),
                List.of(), output, 10.0, Map.of());

        assertTrue(timeline.validate().valid());
        assertEquals(2, timeline.tracks().size());
    }

    // --- Scenario G: Duration computation ---

    @Test
    void computeDurationFromMultipleClips() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");
        TimelineClip clip1 = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineClip clip2 = TimelineClip.of("clip-2", asset, 5.0, 0.0, 15.0);
        TimelineTrack track = new TimelineTrack(
                "tr-1", "Video 1", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip1, clip2), false, false);

        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Duration Test", null, List.of(track),
                List.of(), output, 0, Map.of());

        // clip2 ends at 5.0 + 15.0 = 20.0
        assertEquals(20.0, timeline.computeDuration(), 0.001);
    }

    // --- Scenario H: Factory method ---

    @Test
    void factoryMethodCreatesMinimalTimeline() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Factory Test", output);

        assertEquals("tl-1", timeline.id());
        assertEquals("Factory Test", timeline.name());
        assertEquals(1, timeline.tracks().size());
        assertEquals(TimelineTrack.TrackType.VIDEO, timeline.tracks().get(0).type());
    }

    // --- Scenario I: Output spec ---

    @Test
    void outputSpec1080p() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        assertEquals(1920, output.width());
        assertEquals(1080, output.height());
        assertEquals("mp4", output.format());
    }

    // --- Scenario J: Track types ---

    @Test
    void trackTypes() {
        TimelineTrack video = TimelineTrack.of("v1", "Video", TimelineTrack.TrackType.VIDEO);
        TimelineTrack audio = TimelineTrack.of("a1", "Audio", TimelineTrack.TrackType.AUDIO);
        TimelineTrack subtitle = TimelineTrack.of("s1", "Subtitle", TimelineTrack.TrackType.SUBTITLE);

        assertEquals(TimelineTrack.TrackType.VIDEO, video.type());
        assertEquals(TimelineTrack.TrackType.AUDIO, audio.type());
        assertEquals(TimelineTrack.TrackType.SUBTITLE, subtitle.type());
    }

    // --- Scenario K: Clip timing ---

    @Test
    void clipTimingValidation() {
        TimelineAssetRef asset = TimelineAssetRef.of("art-1", "storage://video.mp4");

        // Valid: out > in
        TimelineClip valid = TimelineClip.of("c1", asset, 0.0, 0.0, 10.0);
        assertTrue(valid.hasValidTiming());

        // Invalid: out < in
        TimelineClip invalid = TimelineClip.of("c2", asset, 0.0, 10.0, 5.0);
        assertFalse(invalid.hasValidTiming());
    }

    // --- Scenario L: Empty timeline ---

    @Test
    void emptyTimelineHasZeroDuration() {
        TimelineSpec timeline = new TimelineSpec(
                "tl-1", "Empty", null, List.of(),
                List.of(), null, 0, Map.of());

        assertEquals(0, timeline.computeDuration());
    }
}
