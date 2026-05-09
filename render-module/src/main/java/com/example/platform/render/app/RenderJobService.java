package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RenderJobService {
    private final DSLContext dsl;
    private final RenderPolicyEngine policyEngine;
    private final NotificationEventPublisher publisher;
    private final RenderJobStateMachine stateMachine;
    private final RenderJobStatusHistoryRepository historyRepository;

    public RenderJobService(DSLContext dsl, RenderPolicyEngine policyEngine,
            NotificationEventPublisher publisher,
            RenderJobStatusHistoryRepository historyRepository) {
        this.dsl = dsl;
        this.policyEngine = policyEngine;
        this.publisher = publisher;
        this.historyRepository = historyRepository;
        this.stateMachine = new RenderJobStateMachine();
    }

    public RenderJobResponse create(CreateRenderJobRequest request) {
        Record projectRecord = dsl.select(field("tenant_id"))
                .from(table("project"))
                .where(field("id").eq(request.projectId()))
                .fetchOne();
        String projectTenantId;
        if (projectRecord == null) {
            // Project not in DB (e.g. stored in-memory by identity module);
            // use the project ID as tenant identifier for the render job
            projectTenantId = request.projectId();
        } else {
            projectTenantId = projectRecord.get(field("tenant_id"), String.class);
        }
        assertTenantAccess(projectTenantId);

        var id = Ids.newId("rj");
        var decision = policyEngine.decide(request.profile());
        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"), field("profile"), field("status"), field("created_at"))
                .values(id, request.projectId(), projectTenantId,
                        request.timelineSnapshotId(), request.profile(), "QUEUED", OffsetDateTime.now())
                .execute();
        historyRepository.record(id, null, "QUEUED", "Job created", null);
        publisher.publish(new RenderJobCreatedEvent(id, request.projectId(), request.timelineSnapshotId(), request.profile(), decision.primaryBackend()));
        return new RenderJobResponse(id, request.projectId(), request.timelineSnapshotId(), request.profile(), "QUEUED");
    }

    public RenderJobResponse createForProject(String tenantId, String projectId, CreateRenderJobRequest request) {
        assertTenantAccess(tenantId);
        Record projectRecord = dsl.select(field("tenant_id"))
                .from(table("project"))
                .where(field("id").eq(projectId))
                .fetchOne();
        if (projectRecord == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        String projectTenantId = projectRecord.get(field("tenant_id"), String.class);
        if (!tenantId.equals(projectTenantId)) {
            throw new IllegalArgumentException("Project not found for tenant");
        }

        var id = Ids.newId("rj");
        var decision = policyEngine.decide(request.profile());
        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"), field("profile"), field("status"), field("created_at"))
                .values(id, projectId, tenantId,
                        request.timelineSnapshotId(), request.profile(), "QUEUED", OffsetDateTime.now())
                .execute();
        historyRepository.record(id, null, "QUEUED", "Job created", null);
        publisher.publish(new RenderJobCreatedEvent(id, projectId, request.timelineSnapshotId(), request.profile(), decision.primaryBackend()));
        return new RenderJobResponse(id, projectId, request.timelineSnapshotId(), request.profile(), "QUEUED");
    }

    public RenderJobResponse getById(String jobId) {
        Record record = dsl.select(field("id", String.class), field("project_id", String.class),
                        field("tenant_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        if (record == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        String jobTenantId = record.get(field("tenant_id", String.class));
        assertTenantAccess(jobTenantId);
        return new RenderJobResponse(
                record.get(field("id", String.class)),
                record.get(field("project_id", String.class)),
                record.get(field("timeline_snapshot_id", String.class)),
                record.get(field("profile", String.class)),
                record.get(field("status", String.class))
        );
    }

    public List<RenderJobResponse> list() {
        String currentTenant = TenantContext.get();
        if (currentTenant != null) {
            return dsl.select(field("id", String.class), field("project_id", String.class),
                            field("timeline_snapshot_id", String.class), field("profile", String.class),
                            field("status", String.class))
                    .from(table("render_job"))
                    .where(field("tenant_id").eq(currentTenant))
                    .fetch(r -> new RenderJobResponse(
                            r.get(field("id", String.class)),
                            r.get(field("project_id", String.class)),
                            r.get(field("timeline_snapshot_id", String.class)),
                            r.get(field("profile", String.class)),
                            r.get(field("status", String.class))
                    ));
        }
        return dsl.select(field("id", String.class), field("project_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .fetch(r -> new RenderJobResponse(
                        r.get(field("id", String.class)),
                        r.get(field("project_id", String.class)),
                        r.get(field("timeline_snapshot_id", String.class)),
                        r.get(field("profile", String.class)),
                        r.get(field("status", String.class))
                ));
    }

    public RenderJobResponse getByIdAndProject(String tenantId, String projectId, String jobId) {
        assertTenantAccess(tenantId);
        Record record = dsl.select(field("id", String.class), field("project_id", String.class),
                        field("tenant_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .and(field("project_id").eq(projectId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        if (record == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        return new RenderJobResponse(
                record.get(field("id", String.class)),
                record.get(field("project_id", String.class)),
                record.get(field("timeline_snapshot_id", String.class)),
                record.get(field("profile", String.class)),
                record.get(field("status", String.class))
        );
    }

    public List<RenderJobResponse> listByProject(String tenantId, String projectId) {
        assertTenantAccess(tenantId);
        return dsl.select(field("id", String.class), field("project_id", String.class),
                        field("timeline_snapshot_id", String.class), field("profile", String.class),
                        field("status", String.class))
                .from(table("render_job"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .fetch(r -> new RenderJobResponse(
                        r.get(field("id", String.class)),
                        r.get(field("project_id", String.class)),
                        r.get(field("timeline_snapshot_id", String.class)),
                        r.get(field("profile", String.class)),
                        r.get(field("status", String.class))
                ));
    }

    @Transactional
    public RenderJobResponse cancel(String jobId, String tenantId) {
        assertTenantAccess(tenantId);
        RenderJobResponse job = getById(jobId);
        RenderJobStatus currentStatus = RenderJobStatus.valueOf(job.status());
        stateMachine.validateTransition(currentStatus, RenderJobStatus.CANCELLED);

        dsl.update(table("render_job"))
                .set(field("status"), RenderJobStatus.CANCELLED.name())
                .where(field("id").eq(jobId))
                .execute();
        historyRepository.record(jobId, job.status(), RenderJobStatus.CANCELLED.name(), "User cancelled", null);
        return getById(jobId);
    }

    @Transactional
    public RenderJobResponse retry(String jobId, String tenantId) {
        assertTenantAccess(tenantId);
        RenderJobResponse job = getById(jobId);
        RenderJobStatus currentStatus = RenderJobStatus.valueOf(job.status());
        stateMachine.validateTransition(currentStatus, RenderJobStatus.QUEUED);

        dsl.update(table("render_job"))
                .set(field("status"), RenderJobStatus.QUEUED.name())
                .set(field("error_message"), (String) null)
                .where(field("id").eq(jobId))
                .execute();
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
