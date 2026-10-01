package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphRederivation;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.providerplugin.ProviderCandidateProjection;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proof of the P2-4b acceptance boundary: the real FFmpeg provider
 * contribution is projected into a Stage-1 candidate and the full planning chain
 * (#20 → #21 → #22 binding) now BINDS the minimal render loop.
 *
 * <p>P2-1 supplies the render planning result from a real immutable revision, P2-2
 * the canonical physical plan, P2-3 the provider binding entry, and P2-4a/P2-4b the
 * catalog projection plus the FFmpeg static-compatibility declaration that makes the
 * candidate statically feasible. Before P2-4b the same chain failed closed with
 * {@code UNIT_UNBINDABLE}.</p>
 */
class FfmpegTypedChainBindingIntegrationTest {

    private static final String ARTIFACT_ID = "art-p24b";
    private static final EffectSemanticSnapshot PINNED_EMPTY = new EffectSemanticSnapshotAuthority(
            new EffectDefinitionVersionRegistry.InMemory(),
            new EffectSemanticSnapshotStore.InMemory())
            .mintEmpty();

    private final RenderPlanningEntryService renderPlanningEntry = new RenderPlanningEntryService();
    private final PhysicalPlanningEntryService physicalPlanningEntry = new PhysicalPlanningEntryService();
    private final ProviderBindingEntryService providerBindingEntry = new ProviderBindingEntryService();

    @Test
    void realContributionBindsTheMinimalRenderLoop() {
        PhysicalExecutionPlan plan = physicalPlan();
        List<ProviderCandidate> candidates = ProviderCandidateProjection.project(
                List.of(new FfmpegProviderPluginContribution()));

        ProviderBindingEntryService.ProviderBindingOutcome outcome =
                providerBindingEntry.bind(plan, candidates, List.of());

        assertThat(candidates).hasSize(1);
        assertThat(outcome.tasks()).hasSize(plan.units().size());
        assertThat(outcome.tasks())
                .allSatisfy(task -> assertThat(task.providerBindingPin())
                        .isEqualTo(FfmpegCpuProvider.BINDING));
        assertThat(outcome.executableTaskGraph().uniqueMembershipPhysicalUnitCount())
                .isEqualTo(plan.units().size());
        assertThat(outcome.executableTaskGraph().missingMembershipCount()).isZero();
        assertThat(outcome.executableTaskGraph().dependencyLossCount()).isZero();
        assertThat(outcome.executableTaskGraph().digest()).isNotNull();
    }

    @Test
    void theAuthoredPlanExercisesExactlyTheDeclaredCapabilities() {
        PhysicalExecutionPlan plan = physicalPlan();
        ProviderBindingEntryService.ProviderBindingOutcome outcome = providerBindingEntry.bind(
                plan,
                ProviderCandidateProjection.project(List.of(new FfmpegProviderPluginContribution())),
                List.of());

        List<String> declared = FfmpegCpuProvider.CAPABILITY_PROFILE.supportDeclarations().stream()
                .map(support -> support.capabilityId().value())
                .toList();
        List<String> planCapabilities = plan.units().stream()
                .flatMap(unit -> unit.capabilityRequirementRefs().stream())
                .map(reference -> reference.declaration().capabilityId().value())
                .distinct()
                .toList();

        assertThat(planCapabilities).isNotEmpty().isSubsetOf(declared);
        assertThat(planCapabilities).contains("video.decode", "render.output");
        assertThat(outcome.tasks()).isNotEmpty();
    }

    @Test
    void withoutTheStaticCompatibilityDeclarationTheSameChainIsUnbindable() {
        PhysicalExecutionPlan plan = physicalPlan();
        ProviderCandidate undeclared = new ProviderCandidate(
                FfmpegCpuProvider.BINDING,
                FfmpegCpuProvider.DESCRIPTOR,
                FfmpegCpuProvider.EXECUTION_CONTRACT,
                FfmpegCpuProvider.CAPABILITY_PROFILE,
                ProviderStaticCompatibility.unknown());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> providerBindingEntry.bind(plan, List.of(undeclared), List.of()))
                .isInstanceOfSatisfying(
                        com.example.platform.execution.binding.ProviderBindingException.class,
                        failure -> assertThat(failure.reason())
                                .isEqualTo(com.example.platform.execution.binding.ProviderBindingException.Reason
                                        .UNIT_UNBINDABLE));
    }

    @Test
    void durableInputsReDeriveTheSameBoundGraphForTheRealProvider() {
        PhysicalExecutionPlan plan = physicalPlan();
        List<ProviderCandidate> candidates = ProviderCandidateProjection.project(
                List.of(new FfmpegProviderPluginContribution()));
        ProviderBoundExecutableTaskGraph bound = providerBindingEntry
                .bind(plan, candidates, List.of())
                .executableTaskGraph();

        ProviderBoundExecutableTaskGraph rederived = BoundGraphRederivation.rederive(
                new BoundGraphInputs(plan, candidates, List.of(), bound.digest()));

        assertThat(rederived.digest()).isEqualTo(bound.digest());
        assertThat(rederived.tasks()).hasSameSizeAs(bound.tasks());
        assertThat(rederived.tasks())
                .allSatisfy(task -> assertThat(task.providerBindingPin())
                        .isEqualTo(FfmpegCpuProvider.BINDING));
    }

    // ---------- fixture ----------

    private PhysicalExecutionPlan physicalPlan() {
        RenderPlanningResult renderResult = renderPlanningEntry.plan(
                revision(),
                PINNED_EMPTY,
                new RenderRequest(
                        new RenderRequestId("req-p24b"),
                        new RenderExtent(
                                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                                FrameRate.of(30, 1)),
                        List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER))),
                new SourceResolutionInput(Map.of(
                        new ArtifactId(ARTIFACT_ID), RenderSourceResolutionState.RESOLVED)),
                new CapabilityContext(Set.of(
                        CapabilityId.of("video.decode"),
                        CapabilityId.of("render.composite"),
                        CapabilityId.of("render.output"))));
        assertThat(renderResult.status()).isEqualTo(RenderPlanStatus.PLANNABLE);
        return physicalPlanningEntry.plan(renderResult, new ExecutionPlanId("pep-p24b"))
                .physicalExecutionPlan();
    }

    private static TimelineRevision revision() {
        TimelineDocument document = document();
        String timelineDigest = new TimelineContentDigester().digest(document);
        String revisionSemanticDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, PINNED_EMPTY.reference());
        return new TimelineRevision(
                "rev-p24b", "product-p24b", null,
                TimelineDocument.CURRENT_SCHEMA_VERSION, document, revisionSemanticDigest,
                Instant.EPOCH, "p24b-fixture",
                new TimelineRevisionSemanticContext(
                        timelineDigest, PINNED_EMPTY.reference(), revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private static TimelineDocument document() {
        TimelineClip clip = new TimelineClip(
                "clip-p24b", "asset-1", "stream-1", ARTIFACT_ID, "a".repeat(64),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "v1", TrackType.VIDEO, List.of(clip))),
                TimelineMetadata.empty(),
                AudioMix.EMPTY,
                List.of(),
                List.of());
    }
}
