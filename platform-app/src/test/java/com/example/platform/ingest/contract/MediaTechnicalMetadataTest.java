package com.example.platform.ingest.contract;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaTechnicalMetadataTest {

    @Test
    void testVideoMetadata() {
        var video = new VideoStreamMetadata("h264", 1920, 1080, new BigDecimal("30"), null, "yuv420p", "bt709", 0, 0);
        var audio = new AudioStreamMetadata("aac", 44100, 2, null, 1);
        var probe = new MediaProbeSummary(MediaProbeProvider.FFPROBE, "6.0", MediaProbeStatus.SUCCESS, 5000L, null, null, List.of());

        var metadata = new MediaTechnicalMetadata(
            MediaCategory.VIDEO, 5000L, "mp4", "MOV/MP4/M4A/3GP", 5000000L, 1024000L,
            true, true, false, 1, 1, 0,
            "h264", "aac", 1920, 1080, new BigDecimal("30"), 44100, 2, 0,
            "yuv420p", "bt709",
            List.of(video), List.of(audio), List.of(), probe
        );

        assertEquals(MediaCategory.VIDEO, metadata.mediaCategory());
        assertTrue(metadata.hasVideo());
        assertTrue(metadata.hasAudio());
        assertFalse(metadata.hasSubtitle());
        assertEquals("h264", metadata.primaryVideoCodec());
        assertEquals("aac", metadata.primaryAudioCodec());
        assertEquals(1920, metadata.width());
        assertEquals(1080, metadata.height());
    }

    @Test
    void testAudioOnlyMetadata() {
        var probe = new MediaProbeSummary(MediaProbeProvider.FFPROBE, "6.0", MediaProbeStatus.SUCCESS, 3000L, null, null, List.of());

        var metadata = new MediaTechnicalMetadata(
            MediaCategory.AUDIO, 3000L, "mp3", "MP2/3", 128000L, 512000L,
            false, true, false, 0, 1, 0,
            null, "mp3", null, null, null, 44100, 2, null,
            null, null,
            List.of(), List.of(new AudioStreamMetadata("mp3", 44100, 2, 128000L, 0)), List.of(), probe
        );

        assertEquals(MediaCategory.AUDIO, metadata.mediaCategory());
        assertFalse(metadata.hasVideo());
        assertTrue(metadata.hasAudio());
        assertEquals("mp3", metadata.primaryAudioCodec());
    }

    @Test
    void testNoSensitiveFields() {
        var metadata = new MediaTechnicalMetadata(
            MediaCategory.UNKNOWN, null, null, null, null, null,
            false, false, false, 0, 0, 0,
            null, null, null, null, null, null, null, null,
            null, null,
            List.of(), List.of(), List.of(), null
        );

        assertNull(metadata.durationMs());
        assertNull(metadata.containerFormat());
        assertNull(metadata.probe());
    }
}
