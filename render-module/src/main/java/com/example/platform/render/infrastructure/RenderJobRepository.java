package com.example.platform.render.infrastructure;
import org.jooq.impl.DSL;

import com.example.platform.render.app.dto.RenderJobResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.render.api.request.RenderInitiator;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.render.tables.RenderJob.RENDER_JOB;

@Repository
public class RenderJobRepository {

    private final DSLContext dsl;

    public RenderJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void create(String id, String projectId, String tenantId,
            String timelineSnapshotId, String profile, String status,
            RenderInitiator initiator, OffsetDateTime createdAt) {
        requireInitiatorTenant(tenantId, initiator);
        dsl.insertInto(RENDER_JOB)
                .columns(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID, RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS, RENDER_JOB.INITIATOR_TYPE, RENDER_JOB.INITIATOR_ID,
                        RENDER_JOB.INITIATOR_TENANT_ID, RENDER_JOB.CREATED_AT)
                .values(id, projectId, tenantId,
                        timelineSnapshotId, profile, status, initiator.actorType().name(),
                        initiator.actorId(), initiator.tenantId(), createdAt.toLocalDateTime())
                .execute();
    }

    public Optional<RenderJobResponse> findById(String jobId) {
        Record record = dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(mapToResponse(record));
    }

    public Optional<RenderJobResponse> findByIdAndProjectAndTenant(String jobId, String projectId, String tenantId) {
        Record record = dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TENANT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .and(RENDER_JOB.PROJECT_ID.eq(projectId))
                .and(RENDER_JOB.TENANT_ID.eq(tenantId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.of(mapToResponse(record));
    }

    public List<RenderJobResponse> listByProjectAndTenant(String projectId, String tenantId) {
        return dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID,
                        RENDER_JOB.TIMELINE_SNAPSHOT_ID, RENDER_JOB.PROFILE,
                        RENDER_JOB.STATUS)
                .from(RENDER_JOB)
                .where(RENDER_JOB.TENANT_ID.eq(tenantId))
                .and(RENDER_JOB.PROJECT_ID.eq(projectId))
                .fetch(r -> mapToResponse(r));
    }

    public Record requireScopedJobForUpdate(String tenantId,String jobId) {
        return dsl.fetchOptional("select * from render.render_job where id=? and tenant_id=? for update", jobId, tenantId)
                .orElseThrow(()->new IllegalArgumentException("Render job not found for tenant"));
    }

    public void markGraphPreparation(String tenantId, String jobId) {
        if (dsl.execute("""
                update render.render_job set graph_preparation_stage='INPUTS',
                    graph_preparation_deadline=clock_timestamp()+interval '10 minutes'
                 where tenant_id=? and id=? and status='QUEUED' and graph_preparation_stage is null
                """, tenantId, jobId) != 1) throw new IllegalStateException("graph preparation could not be recorded");
    }
    public void graphPreparationStage(String tenantId, String jobId, String stage) {
        if (dsl.execute("update render.render_job set graph_preparation_stage=? where tenant_id=? and id=? and status='QUEUED' and graph_preparation_deadline is not null",
                stage, tenantId, jobId) != 1) throw new IllegalStateException("graph preparation no longer current");
    }
    public void closeGraphPreparation(String tenantId, String jobId, String failureCode) {
        dsl.execute("update render.render_job set graph_preparation_stage=null, graph_preparation_deadline=null, error_message=? where tenant_id=? and id=?",
                failureCode, tenantId, jobId);
    }

    public int compareAndSetStatus(String jobId,String projectId,String tenantId,String from,String to) {
        return dsl.update(RENDER_JOB).set(RENDER_JOB.STATUS,to).set(RENDER_JOB.UPDATED_AT,java.time.Instant.now())
            .where(RENDER_JOB.ID.eq(jobId)).and(RENDER_JOB.PROJECT_ID.eq(projectId)).and(RENDER_JOB.TENANT_ID.eq(tenantId))
            .and(RENDER_JOB.STATUS.eq(from)).execute();
    }

    public Optional<String> findTenantIdById(String jobId) {
        Record record = dsl.select(RENDER_JOB.TENANT_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        if (record == null) return Optional.empty();
        return Optional.ofNullable(record.get(RENDER_JOB.TENANT_ID));
    }

    public Record requireJobRecord(String jobId) {
        Record job = dsl.select(RENDER_JOB.ID, RENDER_JOB.PROJECT_ID, RENDER_JOB.TENANT_ID,
                        RENDER_JOB.PROFILE, RENDER_JOB.TIMELINE_SNAPSHOT_ID,
                        RENDER_JOB.STATUS, RENDER_JOB.ERROR_MESSAGE,
                        RENDER_JOB.INITIATOR_TYPE, RENDER_JOB.INITIATOR_ID, RENDER_JOB.INITIATOR_TENANT_ID)
                .from(RENDER_JOB)
                .where(RENDER_JOB.ID.eq(jobId))
                .fetchOne();
        if (job == null) {
            throw new IllegalArgumentException("Render job not found: " + jobId);
        }
        return job;
    }

    public void pinIncrementalReference(String tenant, String project, String job, String previous) {
        int n = dsl.execute("update render.render_job set incremental_reference_job_id=? where id=? and tenant_id=? and project_id=? "
                + "and status='QUEUED' and graph_preparation_stage='DISPATCH' and incremental_reference_job_id is null", previous, job, tenant, project);
        if (n != 1) throw new IllegalStateException("Incremental reference already frozen or Job no longer preparing");
    }

    public void pinRevision(String tenantId, String projectId, String jobId, String revisionId) {
        if (dsl.update(RENDER_JOB)
                .set(RENDER_JOB.TIMELINE_REVISION_ID, revisionId)
                .where(RENDER_JOB.ID.eq(jobId))
                .and(RENDER_JOB.PROJECT_ID.eq(projectId))
                .and(RENDER_JOB.TENANT_ID.eq(tenantId))
                .and(RENDER_JOB.TIMELINE_REVISION_ID.isNull())
                .execute() != 1) {
            throw new IllegalStateException("render job revision could not be pinned");
        }
    }

    private RenderJobResponse mapToResponse(Record record) {
        return new RenderJobResponse(
                record.get(RENDER_JOB.ID),
                record.get(RENDER_JOB.PROJECT_ID),
                record.get(RENDER_JOB.TIMELINE_SNAPSHOT_ID),
                record.get(RENDER_JOB.PROFILE),
                record.get(RENDER_JOB.STATUS)
        );
    }

    public static RenderInitiator initiatorFrom(Record record) {
        return RenderInitiator.restore(
                ActorType.valueOf(record.get(RENDER_JOB.INITIATOR_TYPE)),
                record.get(RENDER_JOB.INITIATOR_ID),
                record.get(RENDER_JOB.INITIATOR_TENANT_ID));
    }

    private static void requireInitiatorTenant(String tenantId, RenderInitiator initiator) {
        if (initiator == null) {
            throw new NullPointerException("initiator must not be null");
        }
        if (!tenantId.equals(initiator.tenantId())) {
            throw new IllegalArgumentException("Render initiator tenant does not match request tenant");
        }
    }

    // === Metrics Queries ===

}
