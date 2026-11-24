package com.example.platform.render.domain.renderplan;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.selection.ResolvedScope;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

class VideoTransformOperationPlanningTest {
    private static final String REV = "rev-video-transform";
    private static final TimelineContentDigester DIGESTER = new TimelineContentDigester();

    @Test
    void transformOperationPersistsAndSurvivesMoveTrimAndSplit() {
        TimelineDocument base = timeline();
        VideoTransform transform = new VideoTransform(31_250, 83_333, 500_000, 500_000, 90_000_000,
                200_000, 800_000, 200_000, 800_000, 500_000);
        OperationPlan plan = plan(base, OperationDefinition.V3.SET_VIDEO_TRANSFORM,
                new OperationParameters.VideoTransformParameters(transform), "clip-a");
        TimelineClip transformed = plan.candidateTimeline().getTracks().getFirst().clips().getFirst();
        assertThat(transformed.getTransform()).isEqualTo(transform);

        OperationPlan moved = new OperationPlanner().plan(new OperationInstance(
                OperationDefinition.V1.MOVE.definitionId(), OperationDefinition.V1.MOVE.version(),
                REV, plan.candidateContentHash(),
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV,
                        plan.candidateContentHash(), List.of(TimelineClipId.of("clip-a")),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.MoveParameters(MediaTime.ofRational(1, 2), false),
                "parameter-digest", "invocation"), REV, plan.candidateTimeline());
        assertThat(moved.candidateTimeline().getTracks().getFirst().clips().getFirst().getTransform())
                .isEqualTo(transform);

        OperationPlan trimmed = new OperationPlanner().plan(new OperationInstance(
                OperationDefinition.V1.TRIM.definitionId(), OperationDefinition.V1.TRIM.version(),
                REV, moved.candidateContentHash(),
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV,
                        moved.candidateContentHash(), List.of(TimelineClipId.of("clip-a")),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.TrimParameters(
                        OperationParameters.TrimParameters.TrimEdge.END, MediaTime.ofRational(1, 4)),
                "parameter-digest", "invocation"), REV, moved.candidateTimeline());
        assertThat(trimmed.candidateTimeline().getTracks().getFirst().clips().getFirst().getTransform())
                .isEqualTo(transform);

        OperationPlan split = new OperationPlanner().plan(new OperationInstance(
                OperationDefinition.V3.SPLIT_CLIP.definitionId(), OperationDefinition.V3.SPLIT_CLIP.version(),
                REV, trimmed.candidateContentHash(),
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV,
                        trimmed.candidateContentHash(), List.of(TimelineClipId.of("clip-a")),
                        SelectionSpec.ExpansionPolicy.EXACT)),
                new OperationParameters.SplitParameters(TimelineClipId.of("clip-b"),
                        MediaTime.ofRational(5, 4)),
                "parameter-digest", "invocation"), REV, trimmed.candidateTimeline());
        assertThat(split.candidateTimeline().getTracks().getFirst().clips())
                .allSatisfy(clip -> assertThat(clip.getTransform()).isEqualTo(transform));
    }

    @Test
    void authoredTransformMustReachTheLogicalGraphInASingleTrack() {
        TimelineDocument base = timeline();
        VideoTransform transform = new VideoTransform(31_250, 83_333, 500_000, 500_000, 90_000_000,
                200_000, 800_000, 200_000, 800_000, 500_000);
        var changed = plan(base, OperationDefinition.V3.SET_VIDEO_TRANSFORM,
                new OperationParameters.VideoTransformParameters(transform), "clip-a").candidateTimeline();
        var revision = TestPlans.revisionWithContext(changed, DIGESTER.digest(changed),
                TestPlans.effectSnapshot(List.of(), List.of()));
        var verified = VerifiedTimelineRevisionFactory.verified(revision, DIGESTER);
        var planning = TestPlans.inputWithTimeline(verified);
        var withSource = new RenderPlanningInput(planning.authoredSnapshot(), planning.request(),
                new SourceResolutionInput(java.util.Map.of(
                        new com.example.platform.shared.identity.ArtifactId("artifact-a"), RenderSourceResolutionState.RESOLVED)),
                planning.capabilities());
        var materialization = new DefaultRenderMaterializer().materialize(withSource);
        assertThat(materialization.edges().stream().map(RenderDependencyEdge::dependency))
                .anySatisfy(dependency -> {
                    assertThat(dependency).isInstanceOf(RenderDependency.TimedTrackInput.class);
                    assertThat(((RenderDependency.TimedTrackInput) dependency).transform())
                            .isEqualTo(transform);
                });
    }

    @Test
    void canonicalGeometryAndAspectSurviveSerializationWithTheSameDigest() {
        TimelineDocument base = timeline();
        var transform = new VideoTransform(175_031, -125_123, 500_000, 500_000, 17_250_000,
                125_000, 875_000, 0, 1_000_000, 800_000);
        var changed = plan(base, OperationDefinition.V3.SET_VIDEO_TRANSFORM,
                new OperationParameters.VideoTransformParameters(transform), "clip-a").candidateTimeline();
        var aspect = new OperationPlanner().plan(new OperationInstance(
                OperationDefinition.V3.SET_COMPOSITION_ASPECT.definitionId(),
                OperationDefinition.V3.SET_COMPOSITION_ASPECT.version(), REV, DIGESTER.digest(changed),
                new OperationTarget.TimelineTarget("timeline"),
                new OperationParameters.CompositionAspectParameters(
                        com.example.platform.timeline.canonical.CompositionAspectRatio.PORTRAIT_3_4),
                "parameter-digest", "invocation"), REV, changed).candidateTimeline();
        String serialized = com.example.platform.timeline.app.TimelineDocumentJsonSerializer.serialize(aspect);
        assertThat(serialized).contains("composition-transform-micro-v1", "175031", "PORTRAIT_3_4");
        var reloaded = com.example.platform.timeline.app.TimelineDocumentJsonSerializer.deserialize(serialized);
        assertThat(reloaded.getTracks().getFirst().clips().getFirst().getTransform()).isEqualTo(transform);
        assertThat(DIGESTER.digest(reloaded)).isEqualTo(DIGESTER.digest(aspect));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                com.example.platform.timeline.app.TimelineDocumentJsonSerializer.deserialize(
                        serialized.replace("composition-transform-micro-v1", "output-pixel")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OperationPlan plan(TimelineDocument base, OperationDefinition definition,
            OperationParameters parameters, String clipId) {
        String hash = DIGESTER.digest(base);
        return new OperationPlanner().plan(new OperationInstance(
                definition.definitionId(), definition.version(), REV, hash,
                new OperationTarget.ResolvedClipScopeTarget(new ResolvedScope(REV, hash,
                        List.of(TimelineClipId.of(clipId)), SelectionSpec.ExpansionPolicy.EXACT)),
                parameters, "parameter-digest", "invocation"), REV, base);
    }

    private static TimelineDocument timeline() {
        TimelineClip clip = new TimelineClip("clip-a", "asset-a", "stream-a", "artifact-a",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                MediaTime.ZERO, MediaTime.ofRational(2, 1), MediaTime.ZERO,
                MediaTime.ofRational(2, 1), "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD), List.of());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("v1", "V1", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of());
    }
}
