package com.example.platform.execution.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityContractReference;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
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
 * Cross-stage integration coverage of the full planning chain: a real immutable
 * revision flows P2-1 (#20 render planning) → P2-2 (#21 execution planning) →
 * P2-3 (#22 provider binding), producing the provider-bound executable task
 * graph with exact binding pins — all without any database, worker, or Temporal
 * involvement.
 *
 * <p>The provider candidate used here is a TEST-ONLY declaration: no production
 * provider declares static compatibility yet (provider coverage is P2-4), so
 * this fixture exists purely to exercise the #22 entry.
 */
class ProviderBindingEntryServiceIntegrationTest {

    private static final String ARTIFACT_ID = "art-p21-p22-p23";
    private static final CapabilityId DECODE = CapabilityId.of("video.decode");
    private static final CapabilityId ENCODE = CapabilityId.of("render.output");
    private static final ContractVersionRange RANGE_1_0 =
            ContractVersionRange.exactly(ContractVersion.of(1, 0));

    private static final EffectSemanticSnapshot PINNED_EMPTY = new EffectSemanticSnapshotAuthority(
            new EffectDefinitionVersionRegistry.InMemory(),
            new EffectSemanticSnapshotStore.InMemory())
            .mintEmpty();

    private final RenderPlanningEntryService renderPlanningEntry = new RenderPlanningEntryService();
    private final PhysicalPlanningEntryService physicalPlanningEntry = new PhysicalPlanningEntryService();
    private final ProviderBindingEntryService providerBindingEntry = new ProviderBindingEntryService();

    // ---------- stage #20 fixtures ----------

    private static TimelineDocument document() {
        TimelineClip clip = new TimelineClip(
                "clip-chain", "asset-1", "stream-1", ARTIFACT_ID, "a".repeat(64),
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

    private static TimelineRevision revision() {
        TimelineDocument document = document();
        String timelineDigest = new TimelineContentDigester().digest(document);
        String revisionSemanticDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, PINNED_EMPTY.reference());
        return new TimelineRevision(
                "rev-chain", "product-chain", null,
                TimelineDocument.CURRENT_SCHEMA_VERSION, document, revisionSemanticDigest,
                Instant.EPOCH, "p23-fixture",
                new TimelineRevisionSemanticContext(
                        timelineDigest, PINNED_EMPTY.reference(), revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private static RenderRequest request() {
        return new RenderRequest(
                new RenderRequestId("req-chain"),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1), FrameRate.of(30, 1)),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));
    }

    private PhysicalExecutionPlan physicalPlan() {
        RenderPlanningResult renderResult = renderPlanningEntry.plan(
                revision(),
                PINNED_EMPTY,
                request(),
                new SourceResolutionInput(Map.of(
                        new ArtifactId(ARTIFACT_ID), RenderSourceResolutionState.RESOLVED)),
                new CapabilityContext(Set.of(DECODE, ENCODE, CapabilityId.of("render.composite"))));
        assertEquals(RenderPlanStatus.PLANNABLE, renderResult.status(),
                "the real revision must be PLANNABLE for the chain to proceed");
        return physicalPlanningEntry.plan(renderResult, new ExecutionPlanId("pep-chain"))
                .physicalExecutionPlan();
    }

    // ---------- stage #22 fixtures ----------

    /** TEST-ONLY candidate declaring exactly the capabilities/artifacts the real plan needs. */
    private static ProviderCandidate chainCandidate(String provider) {
        ProviderId providerId = ProviderId.of(provider);
        ProviderImplementationId implementationId = ProviderImplementationId.of(provider + ".native");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                providerId, implementationId, version, contractVersion, profileReference);
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1),
                contractVersion,
                List.of(
                        new ProviderCapabilityContractReference(DECODE, RANGE_1_0),
                        new ProviderCapabilityContractReference(ENCODE, RANGE_1_0)));
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(
                profileReference,
                List.of(
                        ProviderCapabilitySupport.unpinned(DECODE, RANGE_1_0),
                        ProviderCapabilitySupport.unpinned(ENCODE, RANGE_1_0)));
        ProviderStaticCompatibility staticCompatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(
                        ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(binding, descriptor, contract, profile, staticCompatibility);
    }

    private static ProviderCandidate encodeOnlyCandidate(String provider) {
        ProviderCandidate full = chainCandidate(provider);
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1),
                full.executionContract().contractVersion(),
                List.of(new ProviderCapabilityContractReference(ENCODE, RANGE_1_0)));
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(
                full.capabilityProfile().reference(),
                List.of(ProviderCapabilitySupport.unpinned(ENCODE, RANGE_1_0)));
        return new ProviderCandidate(
                full.bindingPin(), full.descriptor(), contract, profile, full.staticCompatibility());
    }

    // ---------- tests ----------

    @Test
    void realRevisionFlowsAcrossStages20To22AndBindsEveryUnit() {
        PhysicalExecutionPlan plan = physicalPlan();
        ProviderCandidate candidate = chainCandidate("chain-provider");

        ProviderBindingEntryService.ProviderBindingOutcome outcome =
                providerBindingEntry.bind(plan, List.of(candidate), List.of());

        assertEquals(plan.units().size(), outcome.tasks().size(),
                "every physical plan unit is bound to exactly one executable task");
        assertTrue(outcome.tasks().stream()
                        .allMatch(task -> task.providerBindingPin().equals(candidate.bindingPin())),
                "every task carries the exact declared provider binding pin");
        assertEquals(plan, outcome.sourcePhysicalPlan(),
                "the bound graph retains the exact canonical #21 source plan");
        assertFalse(outcome.executableTaskGraph().tasks().isEmpty());
        assertNotNull(outcome.executableTaskGraph().digest());
        assertTrue(outcome.executableTaskGraph().uniqueMembershipPhysicalUnitCount()
                        == plan.units().size(),
                "membership coverage is exactly the physical plan unit count");
        assertEquals(0, outcome.executableTaskGraph().missingMembershipCount());
    }

    @Test
    void realPlanProducesInterTaskArtifactBoundariesForItsDependencies() {
        PhysicalExecutionPlan plan = physicalPlan();
        ProviderCandidate candidate = chainCandidate("chain-provider");

        ProviderBindingEntryService.ProviderBindingOutcome outcome =
                providerBindingEntry.bind(plan, List.of(candidate), List.of());

        long dependencyCount = plan.units().stream()
                .flatMap(unit -> unit.typedDependencies().stream())
                .map(dependency -> dependency.edgeId())
                .distinct()
                .count();
        assertEquals(dependencyCount,
                outcome.executableTaskGraph().executionArtifactBoundaries().size(),
                "each source dependency becomes exactly one inter-task Artifact boundary");
        assertEquals(0, outcome.executableTaskGraph().dependencyLossCount(),
                "no dependency is dropped by the binding");
    }

    @Test
    void chainIsDeterministicAcrossRepeatedRuns() {
        ProviderCandidate candidate = chainCandidate("chain-provider");

        ProviderBindingEntryService.ProviderBindingOutcome first =
                providerBindingEntry.bind(physicalPlan(), List.of(candidate), List.of());
        ProviderBindingEntryService.ProviderBindingOutcome second =
                providerBindingEntry.bind(physicalPlan(), List.of(candidate), List.of());

        assertEquals(first.executableTaskGraph().digest(), second.executableTaskGraph().digest());
    }

    @Test
    void chainFailsClosedWhenTheCandidateCannotServeEveryUnit() {
        PhysicalExecutionPlan plan = physicalPlan();

        ProviderBindingException failure = assertThrows(ProviderBindingException.class,
                () -> providerBindingEntry.bind(
                        plan, List.of(encodeOnlyCandidate("partial-provider")), List.of()));

        assertEquals(ProviderBindingException.Reason.UNIT_UNBINDABLE, failure.reason(),
                "a candidate missing the decode capability leaves the plan un-bindable");
    }
}
