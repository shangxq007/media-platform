package com.example.platform.render.domain.renderplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationTarget;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanDigest;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.shared.time.MediaTime;
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

        assertThatThrownBy(() -> plan(base, OperationDefinition.V3.CREATE_CROSS_DISSOLVE, "clip-a",
                new OperationParameters.TransitionCreateParameters("transition-2", "clip-b",
                        MediaTime.ofRational(4, 1)))).isInstanceOf(PlanException.class);

        assertThatThrownBy(() -> plan(base, OperationDefinition.V3.CREATE_CROSS_DISSOLVE, "clip-b",
                new OperationParameters.TransitionCreateParameters("transition-3", "clip-a",
                        MediaTime.ofRational(1, 1)))).isInstanceOf(PlanException.class);

        OperationPlan updated = plan(created.candidateTimeline(), OperationDefinition.V3.UPDATE_CROSS_DISSOLVE,
                "clip-a", new OperationParameters.TransitionUpdateParameters("transition-1",
                        MediaTime.ofRational(1, 2)));
        assertThat(updated.candidateTimeline().getTransitions().getFirst().duration())
                .isEqualTo(MediaTime.ofRational(1, 2));

        OperationPlan deleted = plan(created.candidateTimeline(), OperationDefinition.V3.DELETE_CROSS_DISSOLVE,
                "clip-a", new OperationParameters.TransitionDeleteParameters("transition-1"));
        assertThat(deleted.candidateTimeline().getTransitions()).isEmpty();
    }

    private static OperationPlan plan(TimelineDocument base, OperationDefinition definition,
            String clipId, OperationParameters parameters) {
        String hash = DIGESTER.digest(base);
        return new OperationPlanner().plan(new OperationInstance(
                definition.definitionId(), definition.version(), REV, hash,
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV, hash,
                        List.of(TimelineClipId.of(clipId)), SelectionSpec.ExpansionPolicy.EXACT)),
                parameters, "parameter-digest", "invocation"), REV, base);
    }

    private static TimelineDocument timeline() {
        TimelineClip a = clip("clip-a", MediaTime.ZERO, MediaTime.ofRational(2, 1));
        TimelineClip b = clip("clip-b", MediaTime.ofRational(1, 1), MediaTime.ofRational(4, 1));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("v1", "V1", TrackType.VIDEO, List.of(a)),
                        new TimelineTrack("v2", "V2", TrackType.VIDEO, List.of(b))),
                TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of());
    }

    private static TimelineClip clip(String id, MediaTime start, MediaTime end) {
        return new TimelineClip(id, "asset-" + id, "stream-" + id, "artifact-" + id,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                start, end, MediaTime.ZERO, end.subtract(start), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
    }
}
