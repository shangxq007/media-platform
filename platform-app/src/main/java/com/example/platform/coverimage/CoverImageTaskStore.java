package com.example.platform.coverimage;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, idempotent cover-image task store. One row per (tenant, project, idempotencyKey); the row
 * is the single durable record of a capability request and carries the committed Artifact identity.
 */
@Repository
public class CoverImageTaskStore {

    private final JdbcTemplate jdbc;

    public CoverImageTaskStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Immutable admitted request re-read from the durable row. */
    public record Task(
            String id,
            String tenantId,
            String projectId,
            String subjectArtifactId,
            double timestampSeconds,
            String imageFormat,
            Integer width,
            Integer quality,
            String idempotencyKey,
            String providerId,
            String providerVersion,
            CoverImageContracts.Status status,
            String artifactId,
            String failureCode) {

        public CoverImageContracts.Request toRequest() {
            return new CoverImageContracts.Request(tenantId, projectId, subjectArtifactId,
                    timestampSeconds, imageFormat, width, quality, idempotencyKey);
        }

        public CoverImageContracts.Result toResult() {
            return new CoverImageContracts.Result(id, status, artifactId, failureCode);
        }
    }

    public record Admission(String taskId, boolean created) {}

    /** Idempotent admission: the unique idempotency key decides whether a new row is created. */
    @Transactional
    public Admission admit(CoverImageContracts.Request request) {
        String id = taskId(request);
        int inserted = jdbc.update("""
                insert into cover_image_task(id,tenant_id,project_id,subject_artifact_id,timestamp_seconds,
                    image_format,width,quality,idempotency_key,provider_id,provider_version,status)
                values (?,?,?,?,?,?,?,?,?,?,?,?)
                on conflict (tenant_id,project_id,idempotency_key) do nothing
                """,
                id, request.tenantId(), request.projectId(), request.subjectArtifactId(),
                request.timestampSeconds(), request.imageFormat(), request.width(), request.quality(),
                request.idempotencyKey(), CoverImageContracts.PROVIDER,
                CoverImageContracts.PROVIDER_VERSION, CoverImageContracts.Status.ADMITTED.name());
        if (inserted == 1) {
            return new Admission(id, true);
        }
        return new Admission(taskIdOf(request), false);
    }

    public Optional<Task> find(String tenant, String project, String id) {
        return jdbc.query("""
                select * from cover_image_task where tenant_id=? and project_id=? and id=?
                """,
                (rs, rowNum) -> map(rs), tenant, project, id).stream().findFirst();
    }

    /** Moves the task to the given status only while it is still active; false means no transition. */
    @Transactional
    public boolean statusIfActive(
            String id, CoverImageContracts.Status status, String artifactId, String failureCode) {
        return jdbc.update("""
                update cover_image_task set status=?, artifact_id=?, failure_code=?, updated_at=now()
                 where id=? and status in ('ADMITTED','RUNNING','COMMITTING')
                """, status.name(), artifactId, failureCode, id) == 1;
    }

    @Transactional
    public boolean lockForCommit(String tenant, String project, String id) {
        return jdbc.queryForObject("""
                select status from cover_image_task where tenant_id=? and project_id=? and id=? for update
                """, String.class, tenant, project, id)
                .equals(CoverImageContracts.Status.COMMITTING.name());
    }

    @Transactional
    public boolean completeLocked(String tenant, String project, String id, String artifactId) {
        return jdbc.update("""
                update cover_image_task set status='COMPLETED', artifact_id=?, failure_code=null, updated_at=now()
                 where tenant_id=? and project_id=? and id=?
                """, artifactId, tenant, project, id) == 1;
    }

    public boolean isCancelled(String tenant, String project, String id) {
        return find(tenant, project, id)
                .map(task -> task.status() == CoverImageContracts.Status.CANCELLED)
                .orElse(false);
    }

    @Transactional
    public boolean cancel(String tenant, String project, String id) {
        return jdbc.update("""
                update cover_image_task set status='CANCELLED', updated_at=now()
                 where tenant_id=? and project_id=? and id=? and status in ('ADMITTED','RUNNING','COMMITTING')
                """, tenant, project, id) == 1;
    }

    /** True when this task is the durable owner of the requested idempotency key. */
    public boolean ownsKey(String tenant, String project, String id) {
        return find(tenant, project, id).isPresent();
    }

    static String taskId(CoverImageContracts.Request request) {
        return "cimg_" + java.util.UUID.nameUUIDFromBytes(
                (request.tenantId() + "\0" + request.projectId() + "\0" + request.idempotencyKey())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    private String taskIdOf(CoverImageContracts.Request request) {
        return jdbc.queryForObject("""
                select id from cover_image_task where tenant_id=? and project_id=? and idempotency_key=?
                """, String.class, request.tenantId(), request.projectId(), request.idempotencyKey());
    }

    private static Task map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Task(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("project_id"),
                rs.getString("subject_artifact_id"),
                rs.getDouble("timestamp_seconds"),
                rs.getString("image_format"),
                rs.getObject("width", Integer.class),
                rs.getObject("quality", Integer.class),
                rs.getString("idempotency_key"),
                rs.getString("provider_id"),
                rs.getString("provider_version"),
                CoverImageContracts.Status.valueOf(rs.getString("status")),
                rs.getString("artifact_id"),
                rs.getString("failure_code"));
    }
}
