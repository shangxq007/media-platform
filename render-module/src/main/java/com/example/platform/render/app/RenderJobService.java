package com.example.platform.render.app;

import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenderJobService {
    private final RenderJobRepository renderJobRepository;
    private final RenderPolicyEngine policyEngine;
    private final NotificationEventPublisher publisher;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;

    public RenderJobService(RenderJobRepository renderJobRepository, RenderPolicyEngine policyEngine,
            NotificationEventPublisher publisher,
            RenderJobStatusHistoryRepository historyRepository) {
        this.renderJobRepository = renderJobRepository;
        this.policyEngine = policyEngine;
        this.publisher = publisher;
        this.historyRepository = historyRepository;
        this.stateMachine = new RenderJobStateMachine();
    }

    public RenderJobResponse create(CreateRenderJobRequest request) {
        String projectTenantId = renderJobRepository.findProjectTenantId(request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + request.projectId()));
        assertTenantAccess(projectTenantId);

        var id = Ids.newId("rj");
        var decision = policyEngine.decide(request.profile());
        renderJobRepository.create(id, request.projectId(), projectTenantId,
                request.timelineSnapshotId(), request.profile(), "QUEUED", OffsetDateTime.now());
        historyRepository.record(id, null, "QUEUED", "Job created", null);
        publisher.publish(new RenderJobCreatedEvent(id, request.projectId(), request.timelineSnapshotId(), request.profile(), decision.primaryBackend()));
        return new RenderJobResponse(id, request.projectId(), request.timelineSnapshotId(), request.profile(), "QUEUED");
    }

    public RenderJobResponse createForProject(String tenantId, String projectId, CreateRenderJobRequest request) {
        assertTenantAccess(tenantId);
        String projectTenantId = renderJobRepository.findProjectTenantId(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        if (!tenantId.equals(projectTenantId)) {
            throw new IllegalArgumentException("Project not found for tenant");
        }

        var id = Ids.newId("rj");
        var decision = policyEngine.decide(request.profile());
        renderJobRepository.create(id, projectId, tenantId,
                request.timelineSnapshotId(), request.profile(), "QUEUED", OffsetDateTime.now());
        historyRepository.record(id, null, "QUEUED", "Job created", null);
        publisher.publish(new RenderJobCreatedEvent(id, projectId, request.timelineSnapshotId(), request.profile(), decision.primaryBackend()));
        return new RenderJobResponse(id, projectId, request.timelineSnapshotId(), request.profile(), "QUEUED");
    }

    public RenderJobResponse getById(String jobId) {
        RenderJobResponse job = renderJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Render job not found: " + jobId));
        // Resolve tenant from the job record itself for tenant access check
        String jobTenantId = renderJobRepository.findTenantIdById(jobId).orElse(null);
        assertTenantAccess(jobTenantId);
        return job;
    }

    public List<RenderJobResponse> list() {
        String currentTenant = TenantContext.get();
        if (currentTenant != null) {
            return renderJobRepository.listByTenant(currentTenant);
        }
        return renderJobRepository.listAll();
    }

    public RenderJobResponse getByIdAndProject(String tenantId, String projectId, String jobId) {
        assertTenantAccess(tenantId);
        return renderJobRepository.findByIdAndProjectAndTenant(jobId, projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Render job not found: " + jobId));
    }

    public List<RenderJobResponse> listByProject(String tenantId, String projectId) {
        assertTenantAccess(tenantId);
        return renderJobRepository.listByProjectAndTenant(projectId, tenantId);
    }

    @Transactional
    public RenderJobResponse cancel(String jobId, String tenantId) {
        assertTenantAccess(tenantId);
        RenderJobResponse job = getById(jobId);
        RenderJobStatus currentStatus = RenderJobStatus.valueOf(job.status());
        stateMachine.validateTransition(currentStatus, RenderJobStatus.CANCELLED);

        renderJobRepository.updateStatus(jobId, RenderJobStatus.CANCELLED.name());
        historyRepository.record(jobId, job.status(), RenderJobStatus.CANCELLED.name(), "User cancelled", null);
        return getById(jobId);
    }

    @Transactional
    public RenderJobResponse retry(String jobId, String tenantId) {
        assertTenantAccess(tenantId);
        RenderJobResponse job = getById(jobId);
        RenderJobStatus currentStatus = RenderJobStatus.valueOf(job.status());
        stateMachine.validateTransition(currentStatus, RenderJobStatus.QUEUED);

        renderJobRepository.updateStatusAndClearError(jobId, RenderJobStatus.QUEUED.name());
        historyRepository.record(jobId, job.status(), RenderJobStatus.QUEUED.name(), "User retry", null);
        return getById(jobId);
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
}
