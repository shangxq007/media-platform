package com.example.platform.config.firstrender;

import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.config.providerinteroperability.ProviderInteroperabilityAuthority;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderCapabilityVocabulary;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.RenderRequestId;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionSnapshotPayload;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * First Render Planning Application Service (Phase 3 skeleton).
 *
 * <p>Constructs the First Render planning inputs and context and creates the render job, and
 * <em>nothing else</em>: it does not invoke logical planning (#20), execution planning (#21),
 * provider binding (#22), bound-graph persistence, or any workflow start. Those belong to later
 * phases.
 *
 * <p>Frozen provenance: the render job id comes from {@code RenderJobService.createForProject}
 * (D1); the request id and execution plan id are independent UUIDs (D2/D3); the source resolution
 * state is projected from real Artifact existence (D5); the capability context is the bounded set
 * the minimal scenario truly requires (D4/E1); declarations come from the Provider Interoperability
 * Authority (OD5/R5).
 *
 * <p>Phase 3 performs only a minimal revision read (clip artifact ids); full hydration is Phase 5.
 */
@Service
public class FirstRenderPlanningApplicationService {

    private final RenderJobService renderJobService;
    private final TimelineRevisionSaveService timelineRevisionSaveService;
    private final TimelineRevisionQueryService timelineRevisionQueryService;
    private final ArtifactRepository artifactRepository;
    private final ProviderInteroperabilityAuthority providerInteroperabilityAuthority;

    public FirstRenderPlanningApplicationService(
            RenderJobService renderJobService,
            TimelineRevisionSaveService timelineRevisionSaveService,
            TimelineRevisionQueryService timelineRevisionQueryService,
            ArtifactRepository artifactRepository,
            ProviderInteroperabilityAuthority providerInteroperabilityAuthority) {
        this.renderJobService = Objects.requireNonNull(renderJobService, "renderJobService");
        this.timelineRevisionSaveService = Objects.requireNonNull(
                timelineRevisionSaveService, "timelineRevisionSaveService");
        this.timelineRevisionQueryService = Objects.requireNonNull(
                timelineRevisionQueryService, "timelineRevisionQueryService");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository");
        this.providerInteroperabilityAuthority = Objects.requireNonNull(
                providerInteroperabilityAuthority, "providerInteroperabilityAuthority");
    }

    /**
     * Prepares the First Render inputs and context for one request.
     *
     * @throws IllegalArgumentException when the revision does not exist for the tenant
     * @throws IllegalStateException    when the revision's canonical snapshot is unavailable
     */
    public FirstRenderPlanningResult prepare(FirstRenderPlanningRequest request) {
        Objects.requireNonNull(request, "request");
        String tenantId = request.tenantId();
        String revisionId = request.revisionId();

        // 1. Minimal revision read: the revision row yields the project scope (Q4: clip artifact ids only).
        TimelineRevision revision = timelineRevisionSaveService.findById(tenantId, revisionId);
        if (revision == null) {
            throw new IllegalArgumentException("Timeline revision not found: " + revisionId);
        }
        String projectId = revision.productId();
        RevisionSnapshotPayload payload = timelineRevisionQueryService
                .getRevisionSnapshotPayload(projectId, tenantId, revisionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Timeline revision snapshot unavailable: " + revisionId));
        TimelineDocument document =
                TimelineDocumentJsonSerializer.deserialize(payload.canonicalTimelineJson());
        List<ArtifactId> sourceArtifacts = FirstRenderSourceArtifacts.artifactIds(document);

        // 2. renderJobId via the existing render-job creation boundary (D1).
        RenderInitiator initiator = request.initiator();
        RenderJobResponse job = renderJobService.createForProject(
                tenantId,
                projectId,
                new CreateRenderJobRequest(projectId, payload.snapshotId(), request.profile()),
                initiator);
        String renderJobId = job.id();

        // 3. RenderRequest with an INDEPENDENT correlation id (D2) and a real RENDER_MASTER output.
        RenderRequest renderRequest = new RenderRequest(
                new RenderRequestId(UUID.randomUUID().toString()),
                request.extent(),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)));

        // 4. Independent execution plan identity (D3).
        ExecutionPlanId executionPlanId = new ExecutionPlanId(UUID.randomUUID().toString());

        // 5. Source resolution projected from real Artifact existence (D5).
        SourceResolutionInput sourceResolution = resolveSources(tenantId, sourceArtifacts);

        // 6. Bounded capability context: only what the minimal scenario truly requires (D4/E1).
        CapabilityContext capabilityContext = new CapabilityContext(Set.of(
                RenderCapabilityVocabulary.videoDecode().capabilityId(),
                RenderCapabilityVocabulary.outputEncode().capabilityId()));

        // 7. Declarations come from the Provider Interoperability Authority (OD5/R5).
        List<ProviderBoundaryCompatibilityDeclaration> declarations =
                providerInteroperabilityAuthority.declarations();

        return new FirstRenderPlanningResult(
                renderJobId, renderRequest, executionPlanId, sourceResolution, capabilityContext,
                declarations);
    }

    private SourceResolutionInput resolveSources(String tenantId, List<ArtifactId> sourceArtifacts) {
        Map<ArtifactId, RenderSourceResolutionState> states = new LinkedHashMap<>();
        for (ArtifactId artifactId : sourceArtifacts) {
            states.put(artifactId, resolveState(tenantId, artifactId));
        }
        return new SourceResolutionInput(states);
    }

    /**
     * D5 mapping: {@code exists == true -> RESOLVED}; {@code exists == false -> FAILED}; an
     * exception (including timeout) while confirming existence is also {@code FAILED} — no
     * PENDING/UNAVAILABLE state is introduced.
     */
    private RenderSourceResolutionState resolveState(String tenantId, ArtifactId artifactId) {
        try {
            return artifactRepository.exists(tenantId, artifactId)
                    ? RenderSourceResolutionState.RESOLVED
                    : RenderSourceResolutionState.FAILED;
        } catch (RuntimeException unresolvable) {
            return RenderSourceResolutionState.FAILED;
        }
    }
}
