package com.example.platform.config.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.capability.CapabilityId;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Phase 3 acceptance P3-C … P3-K for the First Render Planning Application Service
 * (input + context construction only). P3-B (real persistence) is covered by the IT.
 */
class RevisionRenderPlanningApplicationServiceTest {

    private static final String TENANT = "tenant-1";
    private static final String PROJECT = "project-1";
    private static final String REVISION = "rev-1";
    private static final String PROFILE = "first-render";
    private static final String JOB_ID = "rj_test0001";
    private static final String SNAPSHOT_ID = "snap-1";

    private static final EffectSemanticSnapshot PINNED_EMPTY = new EffectSemanticSnapshotAuthority(
            new EffectDefinitionVersionRegistry.InMemory(),
            new EffectSemanticSnapshotStore.InMemory()).mintEmpty();

    private final RenderJobService renderJobService = mock(RenderJobService.class);
    private final TimelineRevisionSaveService revisionSaveService = mock(TimelineRevisionSaveService.class);
    private final TimelineRevisionQueryService revisionQueryService = mock(TimelineRevisionQueryService.class);
    private final ArtifactQueryService artifactQueries = mock(ArtifactQueryService.class);
    private final ProviderInteroperabilityAuthority authority = mock(ProviderInteroperabilityAuthority.class);

    private final RevisionRenderPlanningApplicationService service = new RevisionRenderPlanningApplicationService(
            renderJobService, revisionSaveService, revisionQueryService, artifactQueries, authority);

    @Test
    void preparesIndependentIdsBoundedCapabilitiesAndAuthorityDeclarations() {
        stubRevision("art-1");
        when(artifactQueries.getArtifact(TENANT, new ArtifactId("art-1"))).thenReturn(java.util.Optional.of(mock(com.example.platform.artifact.domain.Artifact.class)));
        when(authority.declarations()).thenReturn(List.of());
        when(renderJobService.createForGraphPreparation(any(), any(), any(), any()))
                .thenReturn(new RenderJobResponse(JOB_ID, PROJECT, SNAPSHOT_ID, PROFILE, "QUEUED"));

        RevisionRenderPlanningResult result = service.prepare(request());

        // P3-C: RenderRequest.id is independent from renderJobId.
        assertThat(result.renderRequest().id().value()).isNotEqualTo(result.renderJobId());
        // P3-D: all three ids are mutually distinct.
        assertThat(result.executionPlanId().value()).isNotEqualTo(result.renderJobId());
        assertThat(result.executionPlanId().value())
                .isNotEqualTo(result.renderRequest().id().value());
        assertThat(Set.of(result.renderJobId(), result.renderRequest().id().value(),
                result.executionPlanId().value())).hasSize(3);
        // P3-E: legal extent + at least one RENDER_MASTER output.
        assertThat(result.renderRequest().extent().isValid()).isTrue();
        assertThat(result.renderRequest().outputs())
                .isNotEmpty()
                .allSatisfy(output -> assertThat(output.role()).isEqualTo(RenderOutputRole.RENDER_MASTER));
        // P3-H: capability context covers the minimal scenario, and nothing more is asserted.
        assertThat(result.capabilityContext().supports(CapabilityId.of("video.decode"))).isTrue();
        assertThat(result.capabilityContext().supports(CapabilityId.of("render.output"))).isTrue();
        assertThat(result.capabilityContext().supports(CapabilityId.of("render.composite"))).isTrue();
        assertThat(result.capabilityContext().supports(CapabilityId.of("subtitle.rasterize"))).isTrue();
        assertThat(result.capabilityContext().availableCapabilities()).hasSize(4);
        // P3-I: declarations come from the authority; V0 is empty.
        assertThat(result.declarations()).isEmpty();
        verify(authority).declarations();
    }

    @Test
    void resolvesExistingArtifactAndFailsMissingOne() {
        // P3-F
        stubRevision("art-present", "art-missing");
        when(artifactQueries.getArtifact(TENANT, new ArtifactId("art-present"))).thenReturn(java.util.Optional.of(mock(com.example.platform.artifact.domain.Artifact.class)));
        when(artifactQueries.getArtifact(TENANT, new ArtifactId("art-missing"))).thenReturn(java.util.Optional.empty());
        when(authority.declarations()).thenReturn(List.of());
        when(renderJobService.createForGraphPreparation(any(), any(), any(), any()))
                .thenReturn(new RenderJobResponse(JOB_ID, PROJECT, SNAPSHOT_ID, PROFILE, "QUEUED"));

        RevisionRenderPlanningResult result = service.prepare(request());

        assertThat(result.sourceResolution().states())
                .containsEntry(new ArtifactId("art-present"), RenderSourceResolutionState.RESOLVED)
                .containsEntry(new ArtifactId("art-missing"), RenderSourceResolutionState.FAILED);
    }

    @Test
    void existenceExceptionYieldsFailed() {
        // P3-G: an exception while confirming existence is FAILED (no PENDING/UNAVAILABLE).
        stubRevision("art-error");
        when(artifactQueries.getArtifact(TENANT, new ArtifactId("art-error")))
                .thenThrow(new IllegalStateException("existence check timed out"));
        when(authority.declarations()).thenReturn(List.of());
        when(renderJobService.createForGraphPreparation(any(), any(), any(), any()))
                .thenReturn(new RenderJobResponse(JOB_ID, PROJECT, SNAPSHOT_ID, PROFILE, "QUEUED"));

        RevisionRenderPlanningResult result = service.prepare(request());

        assertThat(result.sourceResolution().states())
                .containsEntry(new ArtifactId("art-error"), RenderSourceResolutionState.FAILED);
    }

    @Test
    void exposesNoPlanningBindingOrExecutionCollaborators() {
        // P3-J / P3-K: structurally, the service cannot call #20/#21/#22 or save/start.
        Set<Class<?>> collaborators = Arrays.stream(
                        RevisionRenderPlanningApplicationService.class.getConstructors()[0].getParameterTypes())
                .collect(Collectors.toSet());

        for (Class<?> forbidden : List.of(
                com.example.platform.render.app.renderplan.RenderPlanningEntryService.class,
                com.example.platform.execution.planning.PhysicalPlanningEntryService.class,
                com.example.platform.execution.binding.ProviderBindingEntryService.class,
                com.example.platform.execution.binding.BoundGraphInputStore.class,
                com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflow.class)) {
            assertThat(collaborators).doesNotContain(forbidden);
        }
    }

    private void stubRevision(String... artifactIds) {
        when(revisionSaveService.findById(TENANT, REVISION)).thenReturn(revision(PROJECT));
        String json = TimelineDocumentJsonSerializer.serialize(document(artifactIds));
        when(revisionQueryService.getRevisionSnapshotPayload(PROJECT, TENANT, REVISION))
                .thenReturn(Optional.of(new RevisionSnapshotPayload(SNAPSHOT_ID, json, "timeline-1.0")));
    }

    private static RevisionRenderPlanningRequest request() {
        return new RevisionRenderPlanningRequest(
                TENANT,
                REVISION,
                new RenderInitiator.System("actor-sys", ActorType.SYSTEM, TENANT),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1), FrameRate.of(30, 1)),
                PROFILE);
    }

    private static TimelineRevision revision(String projectId) {
        TimelineDocument document = document("art-1");
        String timelineDigest = new TimelineContentDigester().digest(document);
        String revisionSemanticDigest = TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, PINNED_EMPTY.reference());
        return new TimelineRevision(
                REVISION, projectId, null, TimelineDocument.CURRENT_SCHEMA_VERSION, document,
                revisionSemanticDigest, Instant.EPOCH, "test-fixture",
                new TimelineRevisionSemanticContext(
                        timelineDigest, PINNED_EMPTY.reference(), revisionSemanticDigest,
                        TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1));
    }

    private static TimelineDocument document(String... artifactIds) {
        List<TimelineClip> clips = new java.util.ArrayList<>();
        int index = 0;
        for (String artifactId : artifactIds) {
            clips.add(new TimelineClip(
                    "clip-" + index, "asset-" + index, "stream-" + index, artifactId, "a".repeat(64),
                    MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                    MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                    "MEDIA_STREAM",
                    ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD)));
            index++;
        }
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "v1", TrackType.VIDEO, clips)),
                TimelineMetadata.empty(),
                AudioMix.EMPTY,
                List.of(),
                List.of());
    }
}
