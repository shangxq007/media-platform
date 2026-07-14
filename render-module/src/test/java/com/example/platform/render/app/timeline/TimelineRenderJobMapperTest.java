package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.testsupport.TimelineCoreSmokeFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TimelineRenderJobMapper} covering all fail-closed validation rules.
 *
 * <p>Covers:
 * <ul>
 *   <li>null/blank input rejection</li>
 *   <li>duration, fps, canvas dimension bounds</li>
 *   <li>unsupported output format</li>
 *   <li>empty/unrenderable timeline</li>
 *   <li>unsafe asset paths (traversal, home, absolute, file://, remote URL)</li>
 *   <li>internal provider selection rejection</li>
 *   <li>provenance preservation in mapped request</li>
 * </ul>
 */
class TimelineRenderJobMapperTest {

    private TimelineRenderJobMapper mapper;

    @BeforeEach
    void setUp() {
        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extensionsReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);
    }

    // --- Valid input ---

    @Test
    void validMinimalVideoTimelineMapsSuccessfully() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");

        assertNotNull(result.request());
        assertEquals("ten_1", result.request().tenantId());
        assertEquals("prj_1", result.request().projectId());
        assertEquals("default_1080p", result.request().profile());
        assertNotNull(result.request().prompt());
        assertTrue(result.request().prompt().contains("tl_smoke_001"));
        assertEquals(TimelineCoreSmokeFixture.TIMELINE_ID, result.timelineId());
        assertFalse(result.hasSubtitles());
        assertEquals(10.0, result.duration(), 0.01);
        assertEquals(30, result.fps());
        assertEquals(1920, result.width());
        assertEquals(1080, result.height());
        assertEquals("mp4", result.outputFormat());
    }

    @Test
    void validVideoWithSubtitleTimelineMapsSuccessfully() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createVideoWithSubtitleTimeline();

        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, null);

        assertNotNull(result.request());
        assertTrue(result.hasSubtitles());
        assertEquals("default_1080p", result.request().profile());
    }

    @Test
    void profileDefaultsTo1080pWhenBlank() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, "");

        assertEquals("default_1080p", result.request().profile());
    }

    @Test
    void profileDefaultsTo1080pWhenNull() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, null);

        assertEquals("default_1080p", result.request().profile());
    }

    @Test
    void provenancePreservedInPromptJson() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");

        String prompt = result.request().prompt();
        assertTrue(prompt.contains("\"id\" : \"tl_smoke_001\"") || prompt.contains("\"id\":\"tl_smoke_001\"")
                || prompt.contains("tl_smoke_001"), "Prompt must contain timelineId");
        assertTrue(prompt.contains("1920"), "Prompt must contain width");
        assertTrue(prompt.contains("1080"), "Prompt must contain height");
        assertTrue(prompt.contains("mp4"), "Prompt must contain format");
    }

    // --- Null/blank rejection ---

    @Test
    void nullSpecThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", null, "default_1080p"));
    }

    @Test
    void blankTimelineIdThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec("", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void nullTimelineIdThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec(null, "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void blankProjectIdThrowsIllegalArgument() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "", spec, "default_1080p"));
    }

    @Test
    void blankTenantIdThrowsIllegalArgument() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("", "prj_1", spec, "default_1080p"));
    }

    @Test
    void nullTenantIdThrowsIllegalArgument() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest(null, "prj_1", spec, "default_1080p"));
    }

    // --- Duration validation ---

    @Test
    void zeroDurationThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void negativeDurationThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), -5.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void excessiveDurationThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), 7200.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    // --- FPS validation ---

    @Test
    void zeroFpsThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "1920x1080", 0, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void negativeFpsThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "1920x1080", -30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void excessiveFpsThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "1920x1080", 240, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    // --- Canvas validation ---

    @Test
    void zeroWidthThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "0x1080", 30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void zeroHeightThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "1920x0", 30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void excessiveCanvasThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "8192x4320", 30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    // --- Format validation ---

    @Test
    void unsupportedFormatThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "avi", "1920x1080", 30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void nullFormatThrowsIllegalArgument() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                null, "1920x1080", 30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void nullOutputSpecThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), null, 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    // --- Track/clip validation ---

    @Test
    void emptyTracksThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void nullTracksThrowsIllegalArgument() {
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                null, List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void noClipsThrowsIllegalArgument() {
        TimelineTrack emptyTrack = TimelineTrack.of("t1", "Empty", TimelineTrack.TrackType.VIDEO);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(emptyTrack), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    // --- Asset path safety ---

    @Test
    void pathTraversalRejects() {
        TimelineAssetRef ref = new TimelineAssetRef("ast_1", "../etc/passwd",
                "mp4", 10, 1920, 1080, Map.<String,String>of(), null);
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack("t1", "V", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
        assertTrue(ex.getMessage().contains("path traversal"));
    }

    @Test
    void homeDirectoryRejects() {
        TimelineAssetRef ref = new TimelineAssetRef("ast_1", "~/media/file.mp4",
                "mp4", 10, 1920, 1080, Map.<String,String>of(), null);
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack("t1", "V", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
        assertTrue(ex.getMessage().contains("home directory"));
    }

    @Test
    void absolutePathRejects() {
        TimelineAssetRef ref = new TimelineAssetRef("ast_1", "/etc/passwd",
                "mp4", 10, 1920, 1080, Map.<String,String>of(), null);
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack("t1", "V", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
        assertTrue(ex.getMessage().contains("absolute path"));
    }

    @Test
    void fileUriRejects() {
        TimelineAssetRef ref = new TimelineAssetRef("ast_1", "file:///tmp/test.mp4",
                "mp4", 10, 1920, 1080, Map.<String,String>of(), null);
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack("t1", "V", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
        assertTrue(ex.getMessage().contains("file://"));
    }

    @Test
    void remoteUrlRejects() {
        TimelineAssetRef ref = new TimelineAssetRef("ast_1", "https://evil.com/media.mp4",
                "mp4", 10, 1920, 1080, Map.<String,String>of(), null);
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack("t1", "V", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
        assertTrue(ex.getMessage().contains("remote URL"));
    }

    @Test
    void httpUrlRejects() {
        TimelineAssetRef ref = new TimelineAssetRef("ast_1", "http://evil.com/media.mp4",
                "mp4", 10, 1920, 1080, Map.<String,String>of(), null);
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack("t1", "V", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
        assertTrue(ex.getMessage().contains("remote URL"));
    }

    @Test
    void assetSchemeAccepted() {
        TimelineAssetRef ref = new TimelineAssetRef("ast_1", "asset://ast_001",
                "mp4", 10, 1920, 1080, Map.<String,String>of(), null);
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack("t1", "V", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_1", "name", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.<String,String>of());

        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");
        assertNotNull(result.request());
    }

    // --- JSON parsing ---

    @Test
    void toRenderJobRequestFromJsonValidInput() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String json = TimelineCoreSmokeFixture.toJson(spec);

        var result = mapper.toRenderJobRequestFromJson("ten_1", "prj_1", json, "default_1080p");

        assertNotNull(result.request());
        assertEquals(TimelineCoreSmokeFixture.TIMELINE_ID, result.timelineId());
    }

    @Test
    void toRenderJobRequestFromJsonBlankJsonThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequestFromJson("ten_1", "prj_1", "", "default_1080p"));
    }

    @Test
    void toRenderJobRequestFromJsonNullJsonThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequestFromJson("ten_1", "prj_1", null, "default_1080p"));
    }

    @Test
    void toRenderJobRequestFromJsonInvalidJsonThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequestFromJson("ten_1", "prj_1", "not json", "default_1080p"));
    }

    // --- Request does not expose internal provider selection ---

    @Test
    void requestDoesNotSetPreferredProviders() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");

        // The request has no preferredProviders/blockedProviders fields in SubmitRenderJobRequest
        // (those are on RenderJob infrastructure record, not the API DTO)
        // The prompt field carries timeline JSON, not provider selection
        assertNotNull(result.request().prompt());
        assertFalse(result.request().prompt().contains("preferredProviders"),
                "Request must not embed provider selection in prompt");
        assertFalse(result.request().prompt().contains("blockedProviders"),
                "Request must not embed provider selection in prompt");
    }
}
