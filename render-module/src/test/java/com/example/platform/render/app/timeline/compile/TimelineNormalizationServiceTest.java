package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TimelineNormalizationService}.
 *
 * <p>Proves:
 * <ul>
 *   <li>Single input timeline normalizes deterministically</li>
 *   <li>Track order is stable (sorted by layer, then type)</li>
 *   <li>Clip order is stable (sorted by timelineStart)</li>
 *   <li>Output profile is normalized with defaults</li>
 *   <li>Missing input fails closed</li>
 *   <li>Unsupported effects fail closed</li>
 *   <li>Normalization does not expose storage internals</li>
 *   <li>Normalization does not bind provider</li>
 * </ul>
 */
class TimelineNormalizationServiceTest {

    private TimelineNormalizationService normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TimelineNormalizationService();
    }

    @Test
    @DisplayName("Single video clip normalizes deterministically")
    void singleVideoClipNormalizesDeterministically() {
        TimelineSpec spec = createSingleClipTimeline();
        String projectId = "prj-001";

        NormalizedTimeline result1 = normalizer.normalize(spec, projectId);
        NormalizedTimeline result2 = normalizer.normalize(spec, projectId);

        assertNotNull(result1);
        assertEquals(result1.timelineId(), result2.timelineId());
        assertEquals(result1.tracks().size(), result2.tracks().size());
        assertEquals(result1.totalDuration(), result2.totalDuration());
        assertEquals(result1.outputProfile().format(), result2.outputProfile().format());
        assertEquals(result1.tracks().get(0).clips().get(0).assetRef().assetId(),
                result2.tracks().get(0).clips().get(0).assetRef().assetId());
    }

    @Test
    @DisplayName("Track order is stable — sorted by layer then type")
    void trackOrderIsStable() {
        // Create timeline with tracks in reverse layer order
        TimelineTrack videoTrack = new TimelineTrack("trk-video", "Video", TimelineTrack.TrackType.VIDEO, 2,
                List.of(createClip("clip-1", "asset-1", 0, 0, 5)), false, false);
        TimelineTrack audioTrack = new TimelineTrack("trk-audio", "Audio", TimelineTrack.TrackType.AUDIO, 1,
                List.of(), false, false);
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(videoTrack, audioTrack), List.of(),
                TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        NormalizedTimeline result = normalizer.normalize(spec, "prj-001");

        assertEquals(2, result.tracks().size());
        // Audio (layer 1) should come before Video (layer 2)
        assertEquals(NormalizedTrack.TrackType.AUDIO, result.tracks().get(0).type());
        assertEquals(NormalizedTrack.TrackType.VIDEO, result.tracks().get(1).type());
    }

    @Test
    @DisplayName("Clip order is stable — sorted by timelineStart")
    void clipOrderIsStable() {
        TimelineClip clip1 = createClip("clip-2", "asset-2", 5, 0, 5);
        TimelineClip clip2 = createClip("clip-1", "asset-1", 0, 0, 5);
        TimelineTrack track = new TimelineTrack("trk-1", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip1, clip2), false, false);
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        NormalizedTimeline result = normalizer.normalize(spec, "prj-001");

        assertEquals(1, result.tracks().size());
        List<NormalizedClip> clips = result.tracks().get(0).clips();
        assertEquals(2, clips.size());
        // clip-1 (start=0) should come before clip-2 (start=5)
        assertEquals("clip-1", clips.get(0).clipId());
        assertEquals("clip-2", clips.get(1).clipId());
    }

    @Test
    @DisplayName("Output profile is normalized with defaults")
    void outputProfileIsNormalized() {
        TimelineOutputSpec outputSpec = new TimelineOutputSpec(
                "mp4", "1920x1080", 30.0, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(createTrack("trk-1")), List.of(), outputSpec, 5.0, Map.of());

        NormalizedTimeline result = normalizer.normalize(spec, "prj-001");

        NormalizedOutputProfile profile = result.outputProfile();
        assertEquals("mp4", profile.format());
        assertEquals("1920x1080", profile.resolution());
        assertEquals(30.0, profile.frameRate());
        assertEquals("h264", profile.videoCodec());
        assertEquals(8000, profile.videoBitrate());
        assertEquals("aac", profile.audioCodec());
        assertEquals(48000, profile.sampleRate());
        assertEquals(2, profile.channels());
        assertEquals(128, profile.audioBitrate());
        assertEquals("yuv420p", profile.pixelFormat());
    }

    @Test
    @DisplayName("Missing tracks fails closed")
    void missingTracksFailsClosed() {
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(), List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.of());

        TimelineCompileException ex = assertThrows(TimelineCompileException.class,
                () -> normalizer.normalize(spec, "prj-001"));
        assertEquals("MISSING_FIELD", ex.errorCode());
    }

    @Test
    @DisplayName("Missing outputSpec fails closed")
    void missingOutputSpecFailsClosed() {
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(createTrack("trk-1")), List.of(), null, 5.0, Map.of());

        TimelineCompileException ex = assertThrows(TimelineCompileException.class,
                () -> normalizer.normalize(spec, "prj-001"));
        assertEquals("MISSING_FIELD", ex.errorCode());
    }

    @Test
    @DisplayName("Unsupported clip effects fail closed")
    void unsupportedEffectsFailClosed() {
        TimelineClipEffect effect = new TimelineClipEffect("fx-1", "blur", null, null, List.of(), Map.of());
        TimelineClip clip = new TimelineClip("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"),
                0, 0, 5, 5, List.of(effect));
        TimelineTrack track = new TimelineTrack("trk-1", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        TimelineCompileException ex = assertThrows(TimelineCompileException.class,
                () -> normalizer.normalize(spec, "prj-001"));
        assertEquals("UNSUPPORTED_CONSTRUCT", ex.errorCode());
        assertTrue(ex.getMessage().contains("clipEffect"));
    }

    @Test
    @DisplayName("Clip with no assetRef fails closed")
    void clipWithNoAssetRefFailsClosed() {
        TimelineClip clip = new TimelineClip("clip-1", null, 0, 0, 5, 5, List.of());
        TimelineTrack track = new TimelineTrack("trk-1", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        TimelineCompileException ex = assertThrows(TimelineCompileException.class,
                () -> normalizer.normalize(spec, "prj-001"));
        assertEquals("MISSING_FIELD", ex.errorCode());
    }

    @Test
    @DisplayName("Clip with invalid timing fails closed")
    void clipWithInvalidTimingFailsClosed() {
        TimelineClip clip = new TimelineClip("clip-1",
                TimelineAssetRef.of("asset-1", "asset://asset-1"),
                0, 5, 3, -2, List.of()); // out < in, negative duration
        TimelineTrack track = new TimelineTrack("trk-1", "Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        TimelineCompileException ex = assertThrows(TimelineCompileException.class,
                () -> normalizer.normalize(spec, "prj-001"));
        assertEquals("INVALID_DATA", ex.errorCode());
    }

    @Test
    @DisplayName("Normalization does not expose storage internals")
    void normalizationDoesNotExposeStorageInternals() {
        TimelineSpec spec = createSingleClipTimeline();

        NormalizedTimeline result = normalizer.normalize(spec, "prj-001");

        // Verify no storage paths, signed URLs, or provider details in output
        String json = result.toString();
        assertFalse(json.contains("signedUrl"), "Must not contain signedUrl");
        assertFalse(json.contains("storageReferenceId"), "Must not contain storageReferenceId");
        assertFalse(json.contains("rootPath"), "Must not contain rootPath");
        assertFalse(json.contains("relativePath"), "Must not contain relativePath");
    }

    @Test
    @DisplayName("Normalization does not bind provider")
    void normalizationDoesNotBindProvider() {
        TimelineSpec spec = createSingleClipTimeline();

        NormalizedTimeline result = normalizer.normalize(spec, "prj-001");

        // Verify no provider-specific references
        String json = result.toString();
        assertFalse(json.contains("ffmpeg"), "Must not contain provider names");
        assertFalse(json.contains("remotion"), "Must not contain provider names");
        assertFalse(json.contains("blender"), "Must not contain provider names");
    }

    @Test
    @DisplayName("Text overlays normalize to caption layers")
    void textOverlaysNormalizeToCaptionLayers() {
        TimelineTextOverlay overlay = TimelineTextOverlay.of("overlay-1", "Hello World", 1.0, 3.0);
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(createTrack("trk-1")), List.of(overlay),
                TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        NormalizedTimeline result = normalizer.normalize(spec, "prj-001");

        assertTrue(result.hasCaptions(), "Must have captions");
        assertEquals(1, result.captionLayers().size());
        assertEquals("Hello World", result.captionLayers().get(0).text());
        assertEquals(1.0, result.captionLayers().get(0).startTime());
        assertEquals(3.0, result.captionLayers().get(0).duration());
    }

    @Test
    @DisplayName("Empty text in overlay fails closed")
    void emptyTextInOverlayFailsClosed() {
        TimelineTextOverlay overlay = new TimelineTextOverlay("overlay-1", "", "DejaVu Sans", 24,
                "#FFFFFF", "center", "bottom", 1.0, 3.0, null);
        TimelineSpec spec = new TimelineSpec("tl-1", "Test", null,
                List.of(createTrack("trk-1")), List.of(overlay),
                TimelineOutputSpec.mp4_1080p30(), 5.0, Map.of());

        TimelineCompileException ex = assertThrows(TimelineCompileException.class,
                () -> normalizer.normalize(spec, "prj-001"));
        assertEquals("INVALID_DATA", ex.errorCode());
    }

    @Test
    @DisplayName("Null spec fails closed")
    void nullSpecFailsClosed() {
        TimelineCompileException ex = assertThrows(TimelineCompileException.class,
                () -> normalizer.normalize(null, "prj-001"));
        assertEquals("MISSING_FIELD", ex.errorCode());
    }

    // ── Helper methods ──

    private TimelineSpec createSingleClipTimeline() {
        TimelineTrack track = createTrack("trk-1");
        return new TimelineSpec("tl-1", "Test Timeline", "Test description",
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 5.0,
                Map.of("testKey", "testValue"));
    }

    private TimelineTrack createTrack(String trackId) {
        TimelineClip clip = createClip("clip-1", "asset-1", 0, 0, 5);
        return new TimelineTrack(trackId, "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
    }

    private TimelineClip createClip(String clipId, String assetId, double start, double in, double out) {
        return TimelineClip.of(clipId,
                TimelineAssetRef.of(assetId, "asset://" + assetId),
                start, in, out);
    }
}
