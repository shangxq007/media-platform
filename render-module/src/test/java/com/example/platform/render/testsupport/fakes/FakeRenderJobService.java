package com.example.platform.render.testsupport.fakes;

import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.shared.Ids;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake for {@link RenderJobService}.
 *
 * <p>Extends the concrete service, overriding all public methods with
 * in-memory storage. Constructor args are null — safe because all
 * methods are overridden and never delegate to the parent.</p>
 *
 * <p>Used in smoke/integration tests to avoid Mockito stubs for the
 * render job CRUD lifecycle.</p>
 */
public class FakeRenderJobService extends RenderJobService {

    private final Map<String, JobRecord> jobs = new ConcurrentHashMap<>();
    private final List<HistoryRecord> history = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> projectTenantIds = new ConcurrentHashMap<>();
    private final RenderJobStateMachine stateMachine = new RenderJobStateMachine();

    public FakeRenderJobService() {
        super(null, null, null, null);
    }

    // ─── Configuration helpers ───

    /**
     * Register a project → tenant mapping so createForProject can resolve tenant.
     */
    public void registerProject(String projectId, String tenantId) {
        projectTenantIds.put(projectId, tenantId);
    }

    /**
     * Directly seed a job record (for tests that need a pre-existing job).
     */
    public void seedJob(String jobId, String projectId, String tenantId,
                        String snapshotId, String profile, String status) {
        jobs.put(jobId, new JobRecord(jobId, projectId, tenantId, snapshotId, profile, status));
    }

    // ─── Overridden service methods ───

    @Override
    public RenderJobResponse create(CreateRenderJobRequest request) {
        String tenantId = projectTenantIds.getOrDefault(request.projectId(), "default-tenant");
        return doCreate(tenantId, request.projectId(), request.timelineSnapshotId(), request.profile());
    }

    @Override
    public RenderJobResponse createForProject(String tenantId, String projectId,
                                               CreateRenderJobRequest request) {
        String projectTenant = projectTenantIds.get(projectId);
        if (projectTenant == null) {
            throw new IllegalArgumentException("Project not found: " + projectId);
        }
        if (!tenantId.equals(projectTenant)) {
            throw new IllegalArgumentException("Project not found for tenant");
        }
        return doCreate(tenantId, projectId, request.timelineSnapshotId(), request.profile());
    }

    @Override
    public RenderJobResponse getById(String jobId) {
        JobRecord record = jobs.get(jobId);
        if (record == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        return toResponse(record);
    }

    @Override
    public RenderJobResponse getByIdAndProject(String tenantId, String projectId, String jobId) {
        JobRecord record = jobs.get(jobId);
        if (record == null || !record.projectId.equals(projectId) || !record.tenantId.equals(tenantId)) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        return toResponse(record);
    }

    @Override
    public List<RenderJobResponse> list() {
        return jobs.values().stream().map(this::toResponse).toList();
    }

    @Override
    public List<RenderJobResponse> listByProject(String tenantId, String projectId) {
        return jobs.values().stream()
                .filter(j -> j.tenantId.equals(tenantId) && j.projectId.equals(projectId))
                .map(this::toResponse)
                .toList();
    }

    @Override
    public RenderJobResponse cancel(String jobId, String tenantId) {
        JobRecord record = jobs.get(jobId);
        if (record == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        RenderJobStatus current = RenderJobStatus.valueOf(record.status);
        stateMachine.validateTransition(current, RenderJobStatus.CANCELLED);
        record.status = RenderJobStatus.CANCELLED.name();
        history.add(new HistoryRecord(jobId, current.name(), record.status, "User cancelled", null));
        return toResponse(record);
    }

    @Override
    public RenderJobResponse retry(String jobId, String tenantId) {
        JobRecord record = jobs.get(jobId);
        if (record == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        RenderJobStatus current = RenderJobStatus.valueOf(record.status);
        stateMachine.validateTransition(current, RenderJobStatus.QUEUED);
        record.status = RenderJobStatus.QUEUED.name();
        history.add(new HistoryRecord(jobId, current.name(), record.status, "User retry", null));
        return toResponse(record);
    }

    @Override
    public List<StatusHistoryResponse> getStatusHistory(String jobId, String tenantId) {
        return history.stream()
                .filter(h -> h.jobId.equals(jobId))
                .map(h -> new StatusHistoryResponse(
                        Ids.newId("rsh"), h.jobId, h.fromStatus, h.toStatus,
                        h.reason, h.errorCode, OffsetDateTime.now()))
                .toList();
    }

    // ─── Internal ───

    private RenderJobResponse doCreate(String tenantId, String projectId,
                                        String snapshotId, String profile) {
        String id = Ids.newId("rj");
        JobRecord record = new JobRecord(id, projectId, tenantId, snapshotId,
                (profile == null || profile.isBlank()) ? "default_1080p" : profile,
                "QUEUED");
        jobs.put(id, record);
        history.add(new HistoryRecord(id, null, "QUEUED", "Job created", null));
        return toResponse(record);
    }

    private RenderJobResponse toResponse(JobRecord r) {
        return new RenderJobResponse(r.id, r.projectId, r.snapshotId, r.profile, r.status);
    }

    // ─── Internal records ───

    private static class JobRecord {
        final String id;
        final String projectId;
        final String tenantId;
        final String snapshotId;
        final String profile;
        String status;

        JobRecord(String id, String projectId, String tenantId,
                  String snapshotId, String profile, String status) {
            this.id = id;
            this.projectId = projectId;
            this.tenantId = tenantId;
            this.snapshotId = snapshotId;
            this.profile = profile;
            this.status = status;
        }
    }

    private static class HistoryRecord {
        final String jobId;
        final String fromStatus;
        final String toStatus;
        final String reason;
        final String errorCode;

        HistoryRecord(String jobId, String fromStatus, String toStatus,
                      String reason, String errorCode) {
            this.jobId = jobId;
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.reason = reason;
            this.errorCode = errorCode;
        }
    }
}
