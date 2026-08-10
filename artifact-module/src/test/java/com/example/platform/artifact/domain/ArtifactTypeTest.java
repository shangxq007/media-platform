package com.example.platform.artifact.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ArtifactTypeTest {

    @Test
    void shouldContainTimelineTypes() {
        assertNotNull(ArtifactType.TIMELINE_JSON);
        assertNotNull(ArtifactType.TIMELINE_OTIO);
        assertNotNull(ArtifactType.MLT_PROJECT_XML);
    }

    @Test
    void shouldContainRenderTypes() {
        assertNotNull(ArtifactType.FFMPEG_COMMAND_SPEC);
        assertNotNull(ArtifactType.RENDER_LOG);
        assertNotNull(ArtifactType.VIDEO_MEZZANINE);
        assertNotNull(ArtifactType.VIDEO_MP4);
        assertNotNull(ArtifactType.VIDEO_PROXY);
        assertNotNull(ArtifactType.THUMBNAIL);
    }

    @Test
    void shouldContainSubtitleTypes() {
        assertNotNull(ArtifactType.SUBTITLE_SRT);
        assertNotNull(ArtifactType.SUBTITLE_VTT);
    }

    @Test
    void shouldContainAudioTypes() {
        assertNotNull(ArtifactType.AUDIO_MIXDOWN);
    }

    @Test
    void shouldContainHlsTypes() {
        assertNotNull(ArtifactType.HLS_MANIFEST);
        assertNotNull(ArtifactType.HLS_SEGMENT);
    }

    @Test
    void shouldContainDashTypes() {
        assertNotNull(ArtifactType.DASH_MANIFEST);
        assertNotNull(ArtifactType.DASH_SEGMENT);
    }

    @Test
    void shouldContainCmafType() {
        assertNotNull(ArtifactType.CMAF_CHUNK);
    }

    @Test
    void shouldContainQcType() {
        assertNotNull(ArtifactType.QC_REPORT);
    }

    @Test
    void shouldContainProbeType() {
        assertNotNull(ArtifactType.MEDIA_PROBE_JSON);
    }

    @Test
    void shouldContainLegacyTypes() {
        assertNotNull(ArtifactType.GENERIC);
        assertNotNull(ArtifactType.VIDEO);
        assertNotNull(ArtifactType.AUDIO);
        assertNotNull(ArtifactType.IMAGE);
        assertNotNull(ArtifactType.DOCUMENT);
    }

    @Test
    void shouldHaveExpectedTypeCount() {
        ArtifactType[] types = ArtifactType.values();
        assertTrue(types.length >= 24, "Should have at least 24 artifact types, got " + types.length);
    }
}
