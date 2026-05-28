package com.example.platform.shared.media;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class MediaProbeResultTest {

    // ========== hasAudioStream() tests ==========

    @Test
    void hasAudioStream_returnsTrueWhenAudioChannelsAndCodecPresent() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", "aac",
                44100, 2, 0, "bt709", 8_000_000, false, 2,
                true, false, List.of(), null);

        assertTrue(result.hasAudioStream(),
                "hasAudioStream should be true when audioChannels > 0 and audioCodec is present");
    }

    @Test
    void hasAudioStream_returnsTrueWhenOnlyCodecPresent() {
        // Edge case: codec present but channels = 0 (unusual but possible in some containers)
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", "aac",
                0, 0, 0, "bt709", 8_000_000, false, 1,
                true, false, List.of(), null);

        assertTrue(result.hasAudioStream(),
                "hasAudioStream should be true when audioCodec is present even if channels is 0");
    }

    @Test
    void hasAudioStream_returnsTrueWhenOnlyChannelsPresent() {
        // Edge case: channels present but codec is null
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", null,
                44100, 2, 0, "bt709", 8_000_000, false, 1,
                true, false, List.of(), null);

        assertTrue(result.hasAudioStream(),
                "hasAudioStream should be true when audioChannels > 0 even if codec is null");
    }

    @Test
    void hasAudioStream_returnsFalseWhenNoAudioInfo() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", null,
                0, 0, 0, "bt709", 8_000_000, false, 1,
                true, false, List.of(), null);

        assertFalse(result.hasAudioStream(),
                "hasAudioStream should be false when both channels and codec are absent");
    }

    // ========== hasUsableAudio() tests ==========

    @Test
    void hasUsableAudio_returnsTrueWhenChannelsAndCodecPresent() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", "aac",
                44100, 2, 0, "bt709", 8_000_000, false, 2,
                true, false, List.of(), null);

        assertTrue(result.hasUsableAudio(),
                "hasUsableAudio should be true when audioChannels > 0 and audioCodec is present");
    }

    @Test
    void hasUsableAudio_returnsFalseWhenCodecIsNull() {
        // hasAudioStream=true (channels > 0), but hasUsableAudio=false (codec is null)
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", null,
                44100, 2, 0, "bt709", 8_000_000, false, 1,
                true, false, List.of(), null);

        assertTrue(result.hasAudioStream(),
                "hasAudioStream should be true (channels > 0)");
        assertFalse(result.hasUsableAudio(),
                "hasUsableAudio should be false when codec is null");
    }

    @Test
    void hasUsableAudio_returnsFalseWhenCodecIsEmpty() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", "",
                44100, 2, 0, "bt709", 8_000_000, false, 1,
                true, false, List.of(), null);

        assertFalse(result.hasUsableAudio(),
                "hasUsableAudio should be false when codec is empty");
    }

    @Test
    void hasUsableAudio_returnsFalseWhenChannelsIsZero() {
        // hasAudioStream=true (codec present), but hasUsableAudio=false (channels=0)
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", "aac",
                44100, 0, 0, "bt709", 8_000_000, false, 1,
                true, false, List.of(), null);

        assertTrue(result.hasAudioStream(),
                "hasAudioStream should be true (codec present)");
        assertFalse(result.hasUsableAudio(),
                "hasUsableAudio should be false when channels is 0");
    }

    @Test
    void hasUsableAudio_returnsFalseWhenOnlyChannelsPresent() {
        // hasAudioStream=true (channels > 0), but hasUsableAudio=false (codec is null)
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 100, 1000,
                640, 480, 25.0, "h264", null,
                22050, 1, 0, "", 1_000_000, false, 1,
                false, false, List.of(), null);

        assertTrue(result.hasAudioStream(),
                "hasAudioStream should be true (channels > 0)");
        assertFalse(result.hasUsableAudio(),
                "hasUsableAudio should be false when codec is null");
    }

    // ========== hasVideo() tests ==========

    @Test
    void hasVideo_returnsTrueWhenVideoPresent() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///video.mp4", true, "mp4", 1000, 5000,
                1920, 1080, 30.0, "h264", "aac",
                44100, 2, 0, "bt709", 8_000_000, false, 2,
                true, false, List.of(), null);

        assertTrue(result.hasVideo());
    }

    @Test
    void hasVideo_returnsFalseWhenNoVideoCodec() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///audio.mp3", true, "mp3", 100, 5000,
                0, 0, 0, null, "aac",
                44100, 2, 0, "", 0, false, 1,
                false, false, List.of(), null);

        assertFalse(result.hasVideo());
    }

    // ========== failed result tests ==========

    @Test
    void failedResult_hasAudioStreamFalse() {
        var result = MediaProbePort.MediaProbeResult.failed("file:///broken.mp4", "probe error");

        assertFalse(result.hasAudioStream(), "failed result should have hasAudioStream=false");
        assertFalse(result.hasUsableAudio(), "failed result should have hasUsableAudio=false");
        assertFalse(result.hasVideo(), "failed result should have hasVideo=false");
        assertFalse(result.valid(), "failed result should be invalid");
    }

    // ========== edge cases ==========

    @Test
    void noAudioTrackVideo_hasAudioStreamFalse() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///no-audio.mp4", true, "mp4", 5000, 30000,
                1920, 1080, 25.0, "h264", null,
                0, 0, 0, "bt709", 5_000_000, false, 1,
                true, false, List.of(), null);

        assertTrue(result.hasVideo(), "should have video");
        assertFalse(result.hasAudioStream(), "should NOT have audio stream when codec is null and channels is 0");
        assertFalse(result.hasUsableAudio(), "should NOT have usable audio");
    }

    @Test
    void multiAudioTrackVideo_hasUsableAudioTrue() {
        var result = new MediaProbePort.MediaProbeResult(
                "file:///multi-audio.mp4", true, "mp4", 10000, 60000,
                3840, 2160, 60.0, "h264", "aac",
                48000, 6, 0, "bt2020", 20_000_000, false, 3,
                true, false, List.of(), null);

        assertTrue(result.hasVideo());
        assertTrue(result.hasAudioStream(), "should have audio stream with 6 channels and aac codec");
        assertTrue(result.hasUsableAudio(), "should have usable audio with 6 channels and aac codec");
    }

    @Test
    void audioStreamAndUsableAudio_areConsistent() {
        // hasUsableAudio should imply hasAudioStream
        var withAudio = new MediaProbePort.MediaProbeResult(
                "file:///a.mp4", true, "mp4", 100, 1000,
                640, 480, 25.0, "h264", "mp3",
                22050, 2, 0, "", 1_000_000, false, 1,
                false, false, List.of(), null);

        if (withAudio.hasUsableAudio()) {
            assertTrue(withAudio.hasAudioStream(),
                    "hasUsableAudio=true must imply hasAudioStream=true");
        }
    }

    @Test
    void audioStreamDoesNotImplyUsableAudio() {
        // hasAudioStream can be true while hasUsableAudio is false (when codec is null)
        var streamOnly = new MediaProbePort.MediaProbeResult(
                "file:///a.mp4", true, "mp4", 100, 1000,
                640, 480, 25.0, "h264", null,
                44100, 2, 0, "", 1_000_000, false, 1,
                false, false, List.of(), null);

        assertTrue(streamOnly.hasAudioStream(),
                "hasAudioStream should be true when channels > 0");
        assertFalse(streamOnly.hasUsableAudio(),
                "hasUsableAudio should be false when codec is null");
    }
}
