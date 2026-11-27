package com.example.platform.render.domain.renderplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.operation.OperationRequestResolver;
import com.example.platform.operation.operation.OperationResolutionException;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanDigest;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.InternalTimelineValidationService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransitionEffectOperationPlanningTest {
    private static final String REV = "rev-transition-effect";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void effectIsPersistedAndRemovedThroughCanonicalClipOperations() {
        TimelineDocument base = timeline();
        BasicVideoEffect effect = new BasicVideoEffect(0.2, 1.3, 0.6, false, 0.5, 0.5, true);
        OperationPlan set = plan(base, OperationDefinition.V3.SET_VIDEO_EFFECT,
                "clip-a", new OperationParameters.VideoEffectParameters(effect));
        var clip = set.candidateTimeline().getTracks().getFirst().clips().getFirst();
        assertThat(clip.getEffects()).hasSize(1);
        assertThat(clip.getEffects().getFirst().effectKey()).isEqualTo(BasicVideoEffect.EFFECT_KEY);
        assertThat(clip.getEffects().getFirst().parameters())
                .containsEntry("brightness", 0.2)
                .containsEntry("contrast", 1.3)
                .containsEntry("saturation", 0.6)
                .containsEntry("fadeInSeconds", 0.5);

        OperationPlan removed = plan(set.candidateTimeline(), OperationDefinition.V3.REMOVE_VIDEO_EFFECT,
                "clip-a", new OperationParameters.NoParameters());
        assertThat(removed.candidateTimeline().getTracks().getFirst().clips().getFirst().getEffects()).isEmpty();
    }

    @Test
    void crossDissolveRequiresOverlapHigherLayerAndDuration() {
        TimelineDocument base = timeline();
        OperationPlan created = plan(base, OperationDefinition.V3.CREATE_CROSS_DISSOLVE, "clip-a",
                new OperationParameters.TransitionCreateParameters("transition-1", "clip-b",
                        MediaTime.ofRational(1, 1)));
        assertThat(created.candidateTimeline().getTransitions()).hasSize(1);
        var transition = created.candidateTimeline().getTransitions().getFirst();
        assertThat(transition.outgoingClipId()).isEqualTo("clip-a");
        assertThat(transition.incomingClipId()).isEqualTo("clip-b");
        assertThat(transition.duration()).isEqualTo(MediaTime.ofRational(1, 1));

        assertThat(validate(dissolve(base, "too-long", "clip-a", "clip-b", 4)))
                .contains("TIMELINE_TRANSITION_INTERVAL_INVALID");
        assertThat(validate(dissolve(base, "reverse-layer", "clip-b", "clip-a", 1)))
                .contains("TIMELINE_TRANSITION_PROFILE_UNSUPPORTED");

        OperationPlan updated = plan(created.candidateTimeline(), OperationDefinition.V3.UPDATE_CROSS_DISSOLVE,
                "clip-a", new OperationParameters.TransitionUpdateParameters("transition-1",
                        MediaTime.ofRational(1, 2)));
        assertThat(updated.candidateTimeline().getTransitions().getFirst().duration())
                .isEqualTo(MediaTime.ofRational(1, 2));

        OperationPlan deleted = plan(created.candidateTimeline(), OperationDefinition.V3.DELETE_CROSS_DISSOLVE,
                "clip-a", new OperationParameters.TransitionDeleteParameters("transition-1"));
        assertThat(deleted.candidateTimeline().getTransitions()).isEmpty();
    }

    @Test
    void canonicalGateRejectsCompetingIncomingTransitionsAndAcceptsASequentialChain() {
        TimelineDocument chain = withThirdClip();
        var first = dissolve(chain, "first", "clip-a", "clip-b", 1);
        var sequential = dissolve(first, "second", "clip-b", "clip-c", 1);
        assertThat(validate(sequential)).isEmpty();
        var competing = dissolve(sequential, "competing", "clip-a", "clip-c", 1);
        assertThat(validate(competing)).contains("TIMELINE_TRANSITION_CONFLICT");
    }

    @Test
    void canonicalGateRejectsTransitionIntervalAndMappingChanges() {
        var base = dissolve(timeline(), "transition", "clip-a", "clip-b", 1);
        var rate = plan(base, OperationDefinition.V1.SET_TEMPORAL_RATE, "clip-a",
                new OperationParameters.SetTemporalRateParameters(
                        new com.example.platform.timeline.semantics.clip.MediaClip.Rational(2, 1)));
        var direction = plan(base, OperationDefinition.V1.SET_TEMPORAL_DIRECTION, "clip-a",
                new OperationParameters.SetTemporalDirectionParameters(PlaybackDirection.REVERSE));
        var freeze = plan(base, OperationDefinition.V1.FREEZE, "clip-b",
                new OperationParameters.FreezeParameters(MediaTime.ZERO));

        assertThat(validate(rate.candidateTimeline()))
                .contains("TIMELINE_TRANSITION_INTERVAL_INVALID");
        assertThat(validate(direction.candidateTimeline()))
                .contains("TIMELINE_TRANSITION_MAPPING_UNSUPPORTED");
        assertThat(validate(freeze.candidateTimeline()))
                .contains("TIMELINE_TRANSITION_MAPPING_UNSUPPORTED");
    }

    @Test
    void canonicalGateRejectsVideoEffectOnAudioTrack() {
        var audio = clip("audio", MediaTime.ZERO, MediaTime.ofRational(2, 1));
        var base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("a1", "A1", TrackType.AUDIO, List.of(audio))),
                TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of());
        var candidate = plan(base, OperationDefinition.V3.SET_VIDEO_EFFECT, "audio",
                new OperationParameters.VideoEffectParameters(BasicVideoEffect.IDENTITY));
        assertThat(validate(candidate.candidateTimeline()))
                .contains("TIMELINE_EFFECT_TARGET_UNSUPPORTED");
    }

    @Test
    void replacingVoiceoverKeepsTheClipAndItsOutgoingTransition() {
        var a = clip("a", MediaTime.ofRational(2, 1), MediaTime.ofRational(4, 1));
        var b = clip("b", MediaTime.ofRational(4, 1), MediaTime.ofRational(7, 1));
        var base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("sound", "Sound", TrackType.AUDIO, List.of(a, b))), TimelineMetadata.empty());
        var faded = plan(base, OperationDefinition.V3.SET_AUDIO_FADES, "a",
                new OperationParameters.AudioFadeParameters(MediaTime.ofRational(1, 2), null)).candidateTimeline();
        var cross = plan(faded, OperationDefinition.V3.CREATE_AUDIO_CROSSFADE, "a",
                new OperationParameters.TransitionCreateParameters("cut", "b", MediaTime.ofRational(1, 1))).candidateTimeline();
        var input = new OperationParameters.ReplaceVoiceoverParameters(
                new com.example.platform.shared.identity.ArtifactId("asset-replacement"),
                com.example.platform.shared.digest.ContentDigest.sha256("a".repeat(64)),
                com.example.platform.media.domain.stream.MediaStreamId.of("audio-stream"), MediaTime.ofRational(1, 1));
        var replace = plan(cross, OperationDefinition.V3.REPLACE_VOICEOVER, "a", input);
        var doc = replace.candidateTimeline();
        var changed = doc.getTracks().getFirst().clips().getFirst();
        assertThat(replace.plannedChanges()).hasSize(1);
        assertThat(changed.getClipId()).isEqualTo(a.getClipId());
        assertThat(changed.getStartTime()).isEqualTo(a.getStartTime());
        assertThat(changed.getEndTime()).isEqualTo(a.getEndTime());
        assertThat(changed.getTrimStart()).isEqualTo(MediaTime.ofRational(1, 1));
        assertThat(changed.getTrimEnd()).isEqualTo(MediaTime.ofRational(3, 1));
        assertThat(changed.getArtifactId()).isEqualTo("asset-replacement");
        assertThat(changed.getEffects()).isEqualTo(faded.getTracks().getFirst().clips().getFirst().getEffects());
        assertThat(doc.getTransitions()).isEqualTo(cross.getTransitions());
        assertThat(validate(doc)).isEmpty();
        assertThatThrownBy(() -> plan(timeline(), OperationDefinition.V3.REPLACE_VOICEOVER, "clip-a", input))
                .isInstanceOf(PlanException.class).hasMessageContaining("independent audio");
    }

    @Test
    void audioCrossfadeIsAnIndependentTransitionAtTheSameCut() throws Exception {
        var a = clip("a", MediaTime.ZERO, MediaTime.ofRational(4, 1));
        var b = clip("b", MediaTime.ofRational(4, 1), MediaTime.ofRational(7, 1));
        var pictureA = clip("picture-a", MediaTime.ZERO, MediaTime.ofRational(4, 1));
        var pictureB = clip("picture-b", MediaTime.ofRational(4, 1), MediaTime.ofRational(7, 1));
        var base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("picture", "Picture", TrackType.VIDEO, List.of(pictureA, pictureB)),
                        new TimelineTrack("sound", "Sound", TrackType.AUDIO, List.of(a, b))), TimelineMetadata.empty());
        var video = dissolve(base, "video", "picture-a", "picture-b", 1);
        var both = plan(video, OperationDefinition.V3.CREATE_AUDIO_CROSSFADE, "a",
                new OperationParameters.TransitionCreateParameters("audio", "b", MediaTime.ofRational(1, 1))).candidateTimeline();
        assertThat(validate(both)).isEmpty();
        assertThat(both.getTracks()).isEqualTo(video.getTracks());
        assertThat(both.getTransitions()).hasSize(2).contains(video.getTransitions().getFirst());
        var conflict = plan(both, OperationDefinition.V3.SET_AUDIO_FADES, "b",
                new OperationParameters.AudioFadeParameters(MediaTime.ofRational(1, 4), null)).candidateTimeline();
        assertThat(validate(conflict)).contains("TIMELINE_EFFECT_PARAMETERS_INVALID");
        var allowed = plan(both, OperationDefinition.V3.SET_AUDIO_FADES, "b",
                new OperationParameters.AudioFadeParameters(null, MediaTime.ofRational(1, 4))).candidateTimeline();
        assertThat(validate(allowed)).isEmpty();
        assertThatThrownBy(() -> plan(both, OperationDefinition.V3.DELETE_AUDIO_CROSSFADE,
                "a", new OperationParameters.TransitionDeleteParameters("video"))).isInstanceOf(PlanException.class);
        var update = OperationDefinition.V3.UPDATE_AUDIO_CROSSFADE;
        var delete = OperationDefinition.V3.DELETE_AUDIO_CROSSFADE;
        var half = MediaTime.ofRational(1, 2);
        var retimed = resolveTransition(both, update, "audio",
                new OperationParameters.TransitionUpdateParameters("audio", half));
        assertThat(retimed.getTransitions()).contains(video.getTransitions().getFirst())
                .anySatisfy(t -> {
                    assertThat(t.isAudioCrossfade()).isTrue();
                    assertThat(t.duration()).isEqualTo(half);
                });
        assertThatThrownBy(() -> resolveTransition(both, delete, "video",
                new OperationParameters.TransitionDeleteParameters("video")))
                .isInstanceOf(OperationResolutionException.class);
        var cleared = resolveTransition(retimed, delete, "audio",
                new OperationParameters.TransitionDeleteParameters("audio"));
        assertThat(DIGESTER.digest(cleared)).isEqualTo(DIGESTER.digest(video));
    }

    private static TimelineDocument resolveTransition(TimelineDocument base, OperationDefinition definition,
            String id, OperationParameters parameters) throws Exception {
        String hash = DIGESTER.digest(base);
        var request = new OperationRequest(definition.definitionId(),
                definition.version(), new OperationTargetRequest.TransitionTargetRequest(id),
                parameters, REV, hash, null);
        var resolved = OperationRequestResolver.resolve(request,
                new OperationRequestResolver.OperationBaseContext(REV, hash, base));
        return new OperationPlanner().plan(resolved, REV, base).candidateTimeline();
    }

    private static TimelineDocument dissolve(TimelineDocument base, String id, String outgoing,
            String incoming, long duration) {
        return plan(base, OperationDefinition.V3.CREATE_CROSS_DISSOLVE, outgoing,
                new OperationParameters.TransitionCreateParameters(id, incoming,
                        MediaTime.ofRational(duration, 1))).candidateTimeline();
    }

    private static List<String> validate(TimelineDocument document) {
        return new InternalTimelineValidationService().validateDocument("project", document)
                .diagnostics().stream().map(diagnostic -> diagnostic.code().name()).toList();
    }

    private static TimelineDocument withThirdClip() {
        var original = timeline();
        var tracks = new java.util.ArrayList<>(original.getTracks());
        tracks.set(0, new TimelineTrack("v1", "V1", TrackType.VIDEO,
                List.of(clip("clip-a", MediaTime.ZERO, MediaTime.ofRational(3, 1)))));
        tracks.set(2, new TimelineTrack("v3", "V3", TrackType.VIDEO, List.of(
                clip("clip-c", MediaTime.ofRational(2, 1), MediaTime.ofRational(5, 1)))));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, tracks, TimelineMetadata.empty(),
                com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of());
    }

    private static OperationPlan plan(TimelineDocument base, OperationDefinition definition,
            String clipId, OperationParameters parameters) {
        String hash = DIGESTER.digest(base);
        return new OperationPlanner().plan(new OperationInstance(
                definition.definitionId(), definition.version(), REV, hash,
                parameters instanceof OperationParameters.TransitionUpdateParameters update
                        ? new OperationTarget.TransitionTarget(update.transitionId())
                        : parameters instanceof OperationParameters.TransitionDeleteParameters delete
                        ? new OperationTarget.TransitionTarget(delete.transitionId())
                        : new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV, hash,
                        List.of(TimelineClipId.of(clipId)), SelectionSpec.ExpansionPolicy.EXACT)),
                parameters, "parameter-digest", "invocation"), REV, base);
    }

    @Test
    void transitionReferencedClipsRejectMoveTrimSplitDeleteAndRelocate() {
        TimelineDocument withTransition = plan(timeline(), OperationDefinition.V3.CREATE_CROSS_DISSOLVE,
                "clip-a", new OperationParameters.TransitionCreateParameters(
                        "transition-guarded", "clip-b", MediaTime.ofRational(1, 1))).candidateTimeline();

        assertThatThrownBy(() -> plan(withTransition, OperationDefinition.V1.MOVE, "clip-a",
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 2), false)))
                .isInstanceOf(PlanException.class);
        assertThatThrownBy(() -> plan(withTransition, OperationDefinition.V1.TRIM, "clip-a",
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofRational(1, 2))))
                .isInstanceOf(PlanException.class);
        assertThatThrownBy(() -> plan(withTransition, OperationDefinition.V3.SPLIT_CLIP, "clip-a",
                new OperationParameters.SplitParameters(TimelineClipId.of("clip-a-right"),
                        MediaTime.ofRational(1, 1))))
                .isInstanceOf(PlanException.class);
        assertThatThrownBy(() -> plan(withTransition, OperationDefinition.V1.DELETE, "clip-a",
                new OperationParameters.NoParameters()))
                .isInstanceOf(PlanException.class);
        assertThatThrownBy(() -> plan(withTransition, OperationDefinition.V3.RELOCATE_CLIP, "clip-a",
                new OperationParameters.RelocateClipParameters(MediaTime.ZERO, true, "v3")))
                .isInstanceOf(PlanException.class);
        assertThatThrownBy(() -> plan(withTransition, OperationDefinition.V3.RELOCATE_CLIP, "clip-b",
                new OperationParameters.RelocateClipParameters(MediaTime.ZERO, true, "v3")))
                .isInstanceOf(PlanException.class);
    }

    private static TimelineDocument timeline() {
        TimelineClip a = clip("clip-a", MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineClip b = clip("clip-b", MediaTime.ofRational(1, 1), MediaTime.ofRational(4, 1));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("v1", "V1", TrackType.VIDEO, List.of(a)),
                        new TimelineTrack("v2", "V2", TrackType.VIDEO, List.of(b)),
                        new TimelineTrack("v3", "V3", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of());
    }

    private static TimelineClip clip(String id, MediaTime start, MediaTime end) {
        return new TimelineClip(id, "asset-" + id, "stream-" + id, "artifact-" + id,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                start, end, MediaTime.ZERO, end.subtract(start), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
    }
}
