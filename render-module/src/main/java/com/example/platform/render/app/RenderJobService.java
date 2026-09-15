package com.example.platform.render.app;

import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.api.port.RenderJobCancellationContinuation;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.api.event.RenderJobCreatedEvent;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.shared.authorization.*;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenderJobService {
    private final ProjectReadQuery projects;
    private final RenderAcceptanceContextService acceptance;
    private final CanonicalActorResolver actors;
    private final AuthorizationDecisionPort authorization;
    private static final AuthorizationAction READ_JOB = new AuthorizationAction("READ", AuthorizationResourceType.RENDER_JOB, "Read Render job");
    private final RenderJobRepository renderJobRepository;
    private final RenderPolicyEngine policyEngine;
    private final RenderLifecyclePublisher publisher;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;
    private final RenderJobCancellationContinuation cancellationContinuation;

    @org.springframework.beans.factory.annotation.Autowired
    public RenderJobService(RenderJobRepository renderJobRepository, RenderPolicyEngine policyEngine,
            RenderLifecyclePublisher publisher,
            RenderJobStatusHistoryRepository historyRepository,
            @Autowired(required = false) RenderJobCancellationContinuation cancellationContinuation, ProjectReadQuery projects, CanonicalActorResolver actors, AuthorizationDecisionPort authorization, RenderAcceptanceContextService acceptance) {
        this.renderJobRepository = renderJobRepository;
        this.acceptance = acceptance;
        this.policyEngine = policyEngine;
        this.publisher = publisher;
        this.historyRepository = historyRepository;
        this.stateMachine = new RenderJobStateMachine();
        this.cancellationContinuation = cancellationContinuation;
        this.projects = java.util.Objects.requireNonNull(projects);
        this.actors = java.util.Objects.requireNonNull(actors);
        this.authorization = java.util.Objects.requireNonNull(authorization);
    }

    @Transactional
    public RenderJobResponse create(CreateRenderJobRequest request, RenderInitiator initiator) {
        return createForProject(initiator.tenantId(),request.projectId(),request,initiator);
    }

    @Transactional
    public RenderJobResponse createForProject(String tenantId, String projectId,
            CreateRenderJobRequest request, RenderInitiator initiator) {
        assertInitiatorScope(tenantId, initiator);
        assertTenantAccess(tenantId);
        if(!projectId.equals(request.projectId()))throw new PlatformException(CommonErrorCode.INVALID_REQUEST,"Project hint mismatch");
        var prepared=acceptance.prepare(tenantId,projectId,initiator,request.workspaceId(),request.allocationMode());

        var id = ("rj_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        renderJobRepository.create(id, projectId, tenantId,
                request.timelineSnapshotId(), request.profile(), "QUEUED", initiator, OffsetDateTime.now());
        acceptance.persist(id, request.timelineSnapshotId(), prepared);
        historyRepository.record(id, null, "QUEUED", "Job created", null);
        publisher.publishEvent(new RenderJobCreatedEvent(id, projectId, request.timelineSnapshotId(), request.profile(), initiator, java.time.Instant.now()));
        return new RenderJobResponse(id, projectId, request.timelineSnapshotId(), request.profile(), "QUEUED");
    }

    public RenderJobResponse getById(String jobId) {
        String tenantId = TenantContext.get();
        CanonicalActor actor = requireReadActor(tenantId);
        for (var project : projects.listProjects(tenantId)) {
            var job = renderJobRepository.findByIdAndProjectAndTenant(jobId, project.id(), tenantId);
            if (job.isPresent()) {
                authorization.requireAuthorized(jobRead(actor, tenantId, project.id(), jobId));
                return job.get();
            }
        }
        throw new PlatformException(CommonErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
    }

    public List<RenderJobResponse> list() {
        String tenantId = TenantContext.get();
        CanonicalActor actor = requireReadActor(tenantId);
        return projects.listProjects(tenantId).stream()
                .flatMap(project -> renderJobRepository.listByProjectAndTenant(project.id(), tenantId).stream())
                .filter(job -> authorization.decide(jobRead(actor, tenantId, job.projectId(), job.id())).allowed()).toList();
    }

    public RenderJobResponse getByIdAndProject(String tenantId, String projectId, String jobId) {
        CanonicalActor actor = requireReadActor(tenantId);
        requireProject(tenantId, projectId);
        authorization.requireAuthorized(jobRead(actor, tenantId, projectId, jobId));
        return renderJobRepository.findByIdAndProjectAndTenant(jobId, projectId, tenantId)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.RESOURCE_NOT_FOUND, "Resource not found"));
    }

    public List<RenderJobResponse> listByProject(String tenantId, String projectId) {
        CanonicalActor actor = requireReadActor(tenantId);
        requireProject(tenantId, projectId);
        return renderJobRepository.listByProjectAndTenant(projectId, tenantId).stream()
                .filter(job -> authorization.decide(jobRead(actor, tenantId, projectId, job.id())).allowed()).toList();
    }

    private void requireProject(String tenantId, String projectId) {
        var project = projects.getProject(tenantId, projectId);
        if (project == null || !tenantId.equals(project.tenantId()) || !projectId.equals(project.id()))
            throw new PlatformException(CommonErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
    }

    private CanonicalActor requireReadActor(String tenantId) {
        CanonicalActor actor = actors.resolveCurrentActor().orElseThrow(() -> new PlatformException(CommonErrorCode.AUTHENTICATION_REQUIRED, "Authentication required"));
        if (tenantId == null || tenantId.isBlank() || !tenantId.equals(TenantContext.get()) || !tenantId.equals(actor.tenantId()))
            throw new AuthorizationDeniedException(AuthorizationDecision.deny("TENANT_BOUNDARY", "IDENTITY", "Resource unavailable"));
        return actor;
    }

    private AuthorizationRequest jobRead(CanonicalActor actor, String tenantId, String projectId, String jobId) {
        return new AuthorizationRequest(actor, READ_JOB,
                new AuthorizableResourceRef(AuthorizationResourceType.RENDER_JOB, jobId, tenantId, projectId, null),
                new AuthorizationContext("render-job-read", null, Map.of()));
    }

    // Existing mutation hydration stays internal; it is not a public read/discovery entry point.
    private RenderJobResponse mutationJob(String jobId) {
        RenderJobResponse job = renderJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Render job not found: " + jobId));
        // Resolve tenant from the job record itself for tenant access check
        String jobTenantId = renderJobRepository.findTenantIdById(jobId).orElse(null);
        assertTenantAccess(jobTenantId);
        return job;
    }

    @Transactional
    public RenderJobResponse cancel(String jobId, String tenantId) {
        assertTenantAccess(tenantId);
        RenderJobResponse job = mutationJob(jobId);
        RenderJobStatus currentStatus = RenderJobStatus.valueOf(job.status());
        stateMachine.validateTransition(currentStatus, RenderJobStatus.CANCELLED);

        renderJobRepository.updateStatus(jobId, RenderJobStatus.CANCELLED.name());
        historyRepository.record(jobId, job.status(), RenderJobStatus.CANCELLED.name(), "User cancelled", null);
        // W1-GAP-006 (frozen contract TEPHV1 CONTRACT_V1): propagate the
        // application cancellation to the durable execution mechanism
        // (Temporal workflow cancel in temporal mode; no-op in local mode).
        if (cancellationContinuation != null) {
            cancellationContinuation.cancelAfterJobCancelled(tenantId, jobId);
        }
        return mutationJob(jobId);
    }

    @Transactional
    public RenderJobResponse retry(String jobId, String tenantId) {
        assertTenantAccess(tenantId);
        RenderJobResponse job = mutationJob(jobId);
        RenderJobStatus currentStatus = RenderJobStatus.valueOf(job.status());

        // Retry creates a new RenderJob — old job remains in its terminal state
        if (!currentStatus.isTerminal()) {
            throw new PlatformException(CommonErrorCode.CONFLICT,
                    "Cannot retry non-terminal job: " + currentStatus);
        }

        var newId = ("rj_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        renderJobRepository.createRetryJob(newId, jobId);
        historyRepository.record(newId, null, "QUEUED",
                "Retry of failed job " + jobId, null);
        return mutationJob(newId);
    }

    public List<StatusHistoryResponse> getStatusHistory(String jobId, String tenantId) {
        assertTenantAccess(tenantId);
        getById(jobId);
        return historyRepository.findByJobId(jobId);
    }

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }

    private void assertInitiatorScope(String tenantId, RenderInitiator initiator) {
        if (initiator == null) {
            throw new NullPointerException("initiator must not be null");
        }
        if (!tenantId.equals(initiator.tenantId())) {
            throw new IllegalArgumentException("Render initiator tenant does not match request tenant");
        }
    }
}
