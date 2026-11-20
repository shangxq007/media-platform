package com.example.platform.render.domain.renderplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.audio.domain.mix.StereoBalance;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudioMixOperationPlanningTest {
    private static final String REV = "rev-audio";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void audioOperationsCreateAndUpdateCanonicalRoute() {
        TimelineDocument base = document(AudioMix.empty(), "a1", "a2");
        OperationPlan gain = plan(base, OperationDefinition.V1.SET_AUDIO_GAIN,
                new OperationParameters.AudioGainParameters(AudioGain.of(0.5)), "a1", "a-clip");
        assertThat(gain.candidateTimeline().getAudioMix().routes()).hasSize(1);
        assertThat(gain.candidateTimeline().getAudioMix().routes().getFirst().gain().linear()).isEqualTo(0.5);

        OperationPlan mute = plan(gain.candidateTimeline(), OperationDefinition.V1.SET_AUDIO_MUTE,
                new OperationParameters.AudioMuteParameters(AudioMute.of(true)), "a1", "a-clip");
        assertThat(mute.candidateTimeline().getAudioMix().routes().getFirst().mute().muted()).isTrue();

        OperationPlan pan = plan(mute.candidateTimeline(), OperationDefinition.V1.SET_STEREO_BALANCE,
                new OperationParameters.StereoBalanceParameters(StereoBalance.of(-0.4)), "a1", "a-clip");
        var route = pan.candidateTimeline().getAudioMix().routes().getFirst();
        assertThat(route.balance().value()).isEqualTo(-0.4);
        assertThat(route.gain().linear()).isEqualTo(0.5);
        assertThat(route.mute().muted()).isTrue();
    }

    @Test
    void crossTrackRelocateRetargetsTheCanonicalAudioRoute() {
        TimelineDocument base = document(AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("a1", "a-clip")))), "a1", "a2");
        OperationPlan moved = clippedPlan(base, OperationDefinition.V3.RELOCATE_CLIP,
                new OperationParameters.RelocateClipParameters(MediaTime.ZERO, true, "a2"), "a-clip");
        var route = moved.candidateTimeline().getAudioMix().routes().getFirst();
        assertThat(route.input().trackId()).isEqualTo("a2");
        assertThat(route.input().clipId()).isEqualTo("a-clip");
    }

    @Test
    void deletingAnAudioClipRemovesItsRoute() {
        TimelineDocument base = document(AudioMix.of(AudioMasterBus.master(),
                List.of(AudioRoute.of(AudioMixInput.of("a1", "a-clip")))), "a1", "a2");
        OperationPlan deleted = clippedPlan(base, OperationDefinition.V1.DELETE,
                new OperationParameters.NoParameters(), "a-clip");
        assertThat(deleted.candidateTimeline().getAudioMix().routes()).isEmpty();
    }

    private static OperationPlan plan(TimelineDocument base, OperationDefinition definition,
            OperationParameters parameters, String trackId, String clipId) {
        String hash = DIGESTER.digest(base);
        return new OperationPlanner().plan(new OperationInstance(definition.definitionId(), definition.version(),
                REV, hash, new OperationTarget.AudioTarget(AudioMixInput.of(trackId, clipId)),
                parameters, "parameter-digest", "invocation"), REV, base);
    }

    private static OperationPlan clippedPlan(TimelineDocument base, OperationDefinition definition,
            OperationParameters parameters, String clipId) {
        String hash = DIGESTER.digest(base);
        return new OperationPlanner().plan(new OperationInstance(definition.definitionId(), definition.version(),
                REV, hash, new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV, hash,
                List.of(TimelineClipId.of(clipId)), SelectionSpec.ExpansionPolicy.EXACT)),
                parameters, "parameter-digest", "invocation"), REV, base);
    }

    private static TimelineDocument document(AudioMix mix, String firstAudioTrack, String secondAudioTrack) {
        TimelineClip audio = new TimelineClip("a-clip", "asset-a", "stream-a", "art-a", "digest-a",
                MediaTime.ZERO, MediaTime.ofRational(4, 1), MediaTime.ZERO, MediaTime.ofRational(4, 1),
                "MEDIA_STREAM", ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("v1", "V1", TrackType.VIDEO, List.of()),
                        new TimelineTrack(firstAudioTrack, "A1", TrackType.AUDIO, List.of(audio)),
                        new TimelineTrack(secondAudioTrack, "A2", TrackType.AUDIO, List.of())),
                TimelineMetadata.empty(), mix, List.of(), List.of());
    }
}
