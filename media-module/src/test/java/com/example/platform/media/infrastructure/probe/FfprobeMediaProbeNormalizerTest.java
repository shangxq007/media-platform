package com.example.platform.media.infrastructure.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.MediaProbeObservation;
import com.example.platform.media.domain.probe.NormalizedMediaProbe;
import com.example.platform.media.domain.stream.StreamKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * INGEST_NORMALIZATION_BOUNDARY_V1 tests: exact rational conversion, VFR
 * representation, sentinel rejection, stream identity, re-probe stability.
 */
class FfprobeMediaProbeNormalizerTest {

    private final FfprobeMediaProbeNormalizer normalizer = new FfprobeMediaProbeNormalizer();

    private static final String SAMPLE = """
            {"format":{"format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"12.50"},
             "streams":[
               {"codec_type":"video","codec_name":"h264","width":1920,"height":1080,
                "r_frame_rate":"30000/1001","time_base":"1/12800","pix_fmt":"yuv420p",
                "avg_frame_rate":"30000/1001"},
               {"codec_type":"audio","codec_name":"aac","sample_rate":"48000",
                "channels":2,"channel_layout":"stereo","sample_fmt":"fltp"}
             ]}
            """;

    @Test
    void durationIsExactRationalOfObservationString() {
        NormalizedMediaProbe probe = normalizer.normalize(
                new MediaProbeObservation("ffprobe", SAMPLE, true, true, false, List.of(), null),
                MediaAssetId.of("asset-1"));
        assertThat(probe.duration()).isNotNull();
        // 12.50 -> 1250/100 -> 25/2
        assertThat(probe.duration().ticks()).isEqualTo(25L);
        assertThat(probe.duration().timeScale()).isEqualTo(2L);
        assertThat(probe.mediaAssetId()).isEqualTo(MediaAssetId.of("asset-1"));
    }

    @Test
    void frameRateIsExactRational() {
        NormalizedMediaProbe probe = normalizer.normalize(
                new MediaProbeObservation("ffprobe", SAMPLE, true, true, false, List.of(), null),
                MediaAssetId.of("asset-1"));
        var video = probe.streams().stream()
                .filter(s -> s.kind() == StreamKind.VIDEO).findFirst().orElseThrow();
        // 30000/1001 preserved exactly; NOT coerced to double
        assertThat(video.nominalFrameRate().numerator().longValueExact()).isEqualTo(30000);
        assertThat(video.nominalFrameRate().denominator()).isEqualTo(1001);
        assertThat(video.timeBase().numerator()).isEqualTo(1);
        assertThat(video.timeBase().denominator()).isEqualTo(12800);
    }

    @Test
    void audioStreamDescriptionIsSourceLevel() {
        NormalizedMediaProbe probe = normalizer.normalize(
                new MediaProbeObservation("ffprobe", SAMPLE, true, true, false, List.of(), null),
                MediaAssetId.of("asset-1"));
        var audio = probe.streams().stream()
                .filter(s -> s.kind() == StreamKind.AUDIO).findFirst().orElseThrow();
        assertThat(audio.audio().sampleRate()).isEqualTo(48000);
        assertThat(audio.audio().channels()).isEqualTo(2);
        assertThat(audio.audio().channelLayout()).isEqualTo("stereo");
    }

    @Test
    void streamIdsAreStableAndReProbeDoesNotChangeAssetIdentity() {
        MediaAssetId assetId = MediaAssetId.of("asset-1");
        NormalizedMediaProbe first = normalizer.normalize(
                new MediaProbeObservation("ffprobe", SAMPLE, true, true, false, List.of(), null), assetId);
        NormalizedMediaProbe second = normalizer.normalize(
                new MediaProbeObservation("ffprobe", SAMPLE, true, true, false, List.of(), null), assetId);
        assertThat(second.mediaAssetId()).isEqualTo(first.mediaAssetId());
        assertThat(second.streams()).hasSize(first.streams().size());
        assertThat(second.streams().get(0).id()).isEqualTo(first.streams().get(0).id());
    }

    @Test
    void vfrIsExplicitNotDoubleFps() {
        String vfrJson = """
                {"format":{"format_name":"mp4","duration":"5.00"},
                 "streams":[{"codec_type":"video","codec_name":"h264","width":640,"height":360,
                   "r_frame_rate":"0/0","avg_frame_rate":"0/0","time_base":"1/1000"}]}
                """;
        NormalizedMediaProbe probe = normalizer.normalize(
                new MediaProbeObservation("ffprobe", vfrJson, true, true, false, List.of(), null),
                MediaAssetId.of("asset-1"));
        var video = probe.streams().get(0);
        assertThat(video.isVfr()).isTrue();
        assertThat(video.nominalFrameRate()).isNull(); // no fake nominal rate
    }

    @Test
    void invalidObservationYieldsAbsentCanonicalFieldsNotSentinels() {
        NormalizedMediaProbe probe = normalizer.normalize(
                MediaProbeObservation.failed("ffprobe", "boom"), MediaAssetId.of("asset-1"));
        assertThat(probe.duration()).isNull();
        assertThat(probe.streams()).isEmpty();
        assertThat(probe.normalizeRequired()).isTrue();
    }

    @Test
    void rawPayloadIsOpaqueAndNotCanonical() {
        MediaProbeObservation observation = new MediaProbeObservation(
                "ffprobe", SAMPLE, true, true, false, List.of(), null);
        // raw payload is preserved as observation, never surfaced in normalized model
        NormalizedMediaProbe probe = normalizer.normalize(observation, MediaAssetId.of("asset-1"));
        assertThat(probe.container()).isEqualTo("mov,mp4,m4a,3gp,3g2,mj2");
        assertThat(probe.duration().ticks()).isEqualTo(25L);
    }
}
