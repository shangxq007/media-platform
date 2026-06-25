package com.example.platform.outbox.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.outbox.domain.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformJobRepository {

    private final DSLContext dsl;

    public PlatformJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PlatformJob create(JobType jobType, String aggregateType, String aggregateId,
                                String tenantId, String projectId, String payloadJson) {
        String id = Ids.newId("pjob");
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("platform_job"))
                .columns(field("id"), field("job_type"), field("aggregate_type"),
                        field("aggregate_id"), field("tenant_id"), field("project_id"),
                        field("status"), field("required_mask"), field("completed_mask"),
                        field("failed_mask"), field("total_task_count"),
                        field("completed_task_count"), field("failed_task_count"),
                        field("payload_json"), field("created_at"), field("updated_at"))
                .values(id, jobType.name(), aggregateType, aggregateId, tenantId, projectId,
                        "PENDING", 0, 0, 0, 0, 0, 0, payloadJson, now, now)
                .execute();
        return new PlatformJob(id, jobType, aggregateType, aggregateId, tenantId, projectId,
                JobStatus.PENDING, 0, 0, 0, 0, 0, 0, payloadJson, null,
                now.toInstant(), now.toInstant(), null);
    }

    public Optional<PlatformJob> findById(String jobId) {
        Record r = dsl.select().from(table("platform_job")).where(field("id").eq(jobId)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(mapJob(r));
    }

    public void updateStatus(String jobId, String status) {
        dsl.update(table("platform_job"))
                .set(field("status"), status)
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(jobId)).execute();
    }

    public void updateMask(String jobId, int requiredMask, int completedMask, int failedMask) {
        dsl.update(table("platform_job"))
                .set(field("required_mask"), Integer.valueOf(requiredMask))
                .set(field("completed_mask"), Integer.valueOf(completedMask))
                .set(field("failed_mask"), Integer.valueOf(failedMask))
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(jobId)).execute();
    }

    public void markCompleted(String jobId) {
        dsl.update(table("platform_job"))
                .set(field("status"), "COMPLETED")
                .set(field("completed_at"), OffsetDateTime.now())
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(jobId)).execute();
    }

    public void markFailed(String jobId) {
        dsl.update(table("platform_job"))
                .set(field("status"), "FAILED")
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(jobId)).execute();
    }

    private static PlatformJob mapJob(Record r) {
        return new PlatformJob(
                r.get(field("id", String.class)),
                tryParseEnum(JobType.class, r.get(field("job_type", String.class))),
                r.get(field("aggregate_type", String.class)),
                r.get(field("aggregate_id", String.class)),
                r.get(field("tenant_id", String.class)),
                r.get(field("project_id", String.class)),
                tryParseEnum(JobStatus.class, r.get(field("status", String.class))),
                r.get(field("required_mask", Integer.class)),
                r.get(field("completed_mask", Integer.class)),
                r.get(field("failed_mask", Integer.class)),
                r.get(field("total_task_count", Integer.class)),
                r.get(field("completed_task_count", Integer.class)),
                r.get(field("failed_task_count", Integer.class)),
                r.get(field("payload_json", String.class)),
                r.get(field("metadata_json", String.class)),
                toInstant(r.get(field("created_at", OffsetDateTime.class))),
                toInstant(r.get(field("updated_at", OffsetDateTime.class))),
                toInstant(r.get(field("completed_at", OffsetDateTime.class))));
    }

    private static Instant toInstant(OffsetDateTime odt) { return odt != null ? odt.toInstant() : null; }
    private static <E extends Enum<E>> E tryParseEnum(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); } catch (Exception e) { return null; }
    }
}
