package com.example.platform.config.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint;
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
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.providerplugin.ProviderCandidateProjection;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionSnapshotPayload;
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
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflow;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Phase 5 acceptance P5-B … P5-L for the planning-and-binding coordinator. */
class RevisionRenderPlanningAndBindingServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String PROJECT = "project-1";
    private static final String REVISION = "rev-1";
    private static final String PROFILE = "first-render";
    private static final String ARTIFACT_ID = "art-1";
    private static final String JOB_ID = "rj_p5";
    private static final String SNAPSHOT_ID = "snap-1";

    private static final EffectSemanticSnapshot PINNED_EMPTY = new EffectSemanticSnapshotAuthority(
            new EffectDefinitionVersionRegistry.InMemory(),
            new EffectSemanticSnapshotStore.InMemory()).mintEmpty();

    @Test
    void drivesTheRealTypedChainAndBuildsCompleteBoundGraphInputs() {
        // --- collaborators -------------------------------------------------
        RenderJobService renderJobService = mock(RenderJobService.class);
        TimelineRevisionSaveService revisionSaveService = mock(TimelineRevisionSaveService.class);
        TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
        ArtifactQueryService artifactQueries = mock(ArtifactQueryService.class);
        ProviderInteroperabilityAuthority authority = mock(ProviderInteroperabilityAuthority.class);
        EffectSemanticSnapshotStore effectStore = mock(EffectSemanticSnapshotStore.class);
        ProviderPluginCatalog catalog = FirstRenderPlanningFixture.catalog();

        RevisionRenderPlanningApplicationService phase3 =
                spy(new RevisionRenderPlanningApplicationService(
                        renderJobService, revisionSaveService, revisionQueryService, artifactQueries, authority));
        AtomicReference<RevisionRenderPlanningResult> phase3Result = new AtomicReference<>();
        doAnswer(invocation -> {
            RevisionRenderPlanningResult value = (RevisionRenderPlanningResult) invocation.callRealMethod();
            phase3Result.set(value);
            return value;
        }).when(phase3).prepare(any());

        RenderPlanningEntryService render20 = spy(new RenderPlanningEntryService());
        PhysicalPlanningEntryService plan21 = spy(new PhysicalPlanningEntryService());
        ProviderBindingEntryService bind22 = spy(new ProviderBindingEntryService());

        RevisionRenderPlanningAndBindingService service = new RevisionRenderPlanningAndBindingService(
                phase3, revisionSaveService, revisionQueryService, effectStore, catalog,
                render20, plan21, bind22, mock(com.example.platform.render.api.GraphRenderLifecycle.class),
                    (tenant, project, id, digest) -> java.util.Optional.of(
                        new com.example.platform.artifact.contract.ArtifactSourceFacts(MediaTime.ofMillis(2000),
                                List.of(new com.example.platform.artifact.contract.ArtifactSourceFacts.Stream(
                                        "stream-0", com.example.platform.artifact.contract.ArtifactSourceFacts.Kind.VIDEO,
                                        MediaTime.ofMillis(2000))), new com.example.platform.artifact.contract.ArtifactSourceFacts.StreamCounts(1, 0))));

        // --- stubs ---------------------------------------------------------
        String json = TimelineDocumentJsonSerializer.serialize(document());
        when(revisionSaveService.findById(TENANT, REVISION)).thenReturn(revision(json));
        when(revisionQueryService.getRevisionSnapshotPayload(PROJECT, TENANT, REVISION))
                .thenReturn(Optional.of(new RevisionSnapshotPayload(SNAPSHOT_ID, json, "timeline-1.0")));
        when(artifactQueries.getArtifact(TENANT, new ArtifactId(ARTIFACT_ID))).thenReturn(java.util.Optional.of(mock(com.example.platform.artifact.domain.Artifact.class)));
        when(authority.declarations()).thenReturn(List.of());
        when(effectStore.findById(PROJECT, TENANT, PINNED_EMPTY.reference().snapshotId()))
                .thenReturn(Optional.of(PINNED_EMPTY));
        when(renderJobService.createForGraphPreparation(any(), any(), any(), any()))
                .thenReturn(new RenderJobResponse(JOB_ID, PROJECT, SNAPSHOT_ID, PROFILE, "QUEUED"));

        // --- act -----------------------------------------------------------
        RevisionRenderPlanningAndBindingResult result = service.planAndBind(request());

        // P5-D: #20 really called, with the Phase 3 inputs.
        ArgumentCaptor<TimelineRevision> revCaptor = ArgumentCaptor.forClass(TimelineRevision.class);
        ArgumentCaptor<EffectSemanticSnapshot> snapCaptor = ArgumentCaptor.forClass(EffectSemanticSnapshot.class);
        ArgumentCaptor<RenderRequest> requestCaptor = ArgumentCaptor.forClass(RenderRequest.class);
        ArgumentCaptor<SourceResolutionInput> resolutionCaptor = ArgumentCaptor.forClass(SourceResolutionInput.class);
        ArgumentCaptor<CapabilityContext> capabilityCaptor = ArgumentCaptor.forClass(CapabilityContext.class);
        verify(render20, times(1)).plan(revCaptor.capture(), snapCaptor.capture(), requestCaptor.capture(),
                resolutionCaptor.capture(), capabilityCaptor.capture());

        // P5-B: full hydration produced a canonical document.
        assertThat(revCaptor.getValue().canonicalTimeline()).isNotNull();
        // P5-C: the snapshot satisfies the revision's pinned semantics.
        assertThat(snapCaptor.getValue().reference())
                .isEqualTo(revCaptor.getValue().effectSemanticSnapshotReference());
        // P5-D: inputs are exactly the Phase 3 constructed values (not rebuilt).
        assertThat(requestCaptor.getValue()).isSameAs(phase3Result.get().renderRequest());
        assertThat(resolutionCaptor.getValue()).isSameAs(phase3Result.get().sourceResolution());
        assertThat(capabilityCaptor.getValue()).isSameAs(phase3Result.get().capabilityContext());
        assertThat(result.renderJobId()).isEqualTo(phase3Result.get().renderJobId());
        assertThat(result.tenantId()).isEqualTo(TENANT);
        // Phase 5 Amendment: the request id is passed through losslessly from the planning result.
        assertThat(result.renderRequestId()).isEqualTo(phase3Result.get().renderRequest().id());
        // ID independence: renderJobId / renderRequestId / executionPlanId remain distinct.
        assertThat(result.renderRequestId().value()).isNotEqualTo(result.renderJobId());
        assertThat(result.renderRequestId().value())
                .isNotEqualTo(phase3Result.get().executionPlanId().value());

        // P5-E: #21 called with the #20 result + the Phase 3 execution plan id.
        ArgumentCaptor<RenderPlanningResult> r20Captor = ArgumentCaptor.forClass(RenderPlanningResult.class);
        ArgumentCaptor<ExecutionPlanId> planIdCaptor = ArgumentCaptor.forClass(ExecutionPlanId.class);
        verify(plan21, times(1)).plan(r20Captor.capture(), planIdCaptor.capture());
        assertThat(r20Captor.getValue()).isNotNull();
        assertThat(planIdCaptor.getValue()).isEqualTo(phase3Result.get().executionPlanId());
        // executionPlanId is recoverable losslessly from the resulting physical plan.
        assertThat(result.boundGraphInputs().physicalPlan().planId())
                .isEqualTo(phase3Result.get().executionPlanId());

        BoundGraphInputs inputs = result.boundGraphInputs();
        PhysicalExecutionPlan physicalPlan = inputs.physicalPlan();

        // P5-F: candidates come from the catalog projection.
        List<ProviderCandidate> projected = ProviderCandidateProjection.project(catalog);
        assertThat(projected).isNotEmpty();
        assertThat(inputs.candidates()).isEqualTo(projected);

        // P5-G: #22 called with plan + candidates + declarations.
        ArgumentCaptor<PhysicalExecutionPlan> planCaptor = ArgumentCaptor.forClass(PhysicalExecutionPlan.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProviderCandidate>> candidatesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration>>
                declarationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bind22, times(1)).bind(
                planCaptor.capture(), candidatesCaptor.capture(), declarationsCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(com.example.platform.execution.binding.ProviderSelectionPolicy.PREFERRED));
        assertThat(planCaptor.getValue()).isSameAs(physicalPlan);
        assertThat(candidatesCaptor.getValue()).isEqualTo(projected);
        assertThat(declarationsCaptor.getValue()).isEqualTo(phase3Result.get().declarations());

        // P5-H: the digest is the real #22 result (deterministic re-derivation proves provenance).
        String recomputed = new ProviderBindingEntryService()
                .bind(physicalPlan, projected, phase3Result.get().declarations())
                .executableTaskGraph().digest().sha256Hex();
        assertThat(inputs.expectedExecutableTaskGraphDigest().sha256Hex()).isEqualTo(recomputed);
        assertThat(inputs.expectedExecutableTaskGraphDigest().sha256Hex()).matches("[0-9a-f]{64}");
    }

    @Test
    void hasNoPersistenceStartOrReferenceCollaborators() {
        // P5-J / P5-K / P5-L: structurally cannot save, start, or build a BoundGraphReference.
        Set<Class<?>> collaborators = Arrays.stream(
                        RevisionRenderPlanningAndBindingService.class.getConstructors()[0].getParameterTypes())
                .collect(Collectors.toSet());
        for (Class<?> forbidden : List.of(
                BoundGraphInputStore.class, MediaTaskWorkflow.class, BoundGraphReference.class)) {
            assertThat(collaborators).doesNotContain(forbidden);
        }
    }

    private static RevisionRenderPlanningRequest request() {
        return new RevisionRenderPlanningRequest(
                TENANT, REVISION,
                new RenderInitiator.System("actor-sys", ActorType.SYSTEM, TENANT),
                new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(2000), FrameRate.of(30, 1)),
                PROFILE);
    }

    private static TimelineRevision revision(String canonicalJson) {
        TimelineDocument roundTripped = TimelineDocumentJsonSerializer.deserialize(canonicalJson);
        String timelineDigest = new TimelineContentDigester().digest(roundTripped);
        String revisionSemanticDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, PINNED_EMPTY.reference());
        return new TimelineRevision(
                REVISION, PROJECT, null, TimelineDocument.CURRENT_SCHEMA_VERSION, null,
                revisionSemanticDigest, Instant.EPOCH, "test-fixture",
                new TimelineRevisionSemanticContext(
                        timelineDigest, PINNED_EMPTY.reference(), revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private static TimelineDocument document() {
        TimelineClip clip = new TimelineClip(
                "clip-0", "asset-0", "stream-0", ARTIFACT_ID, "a".repeat(64),
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
