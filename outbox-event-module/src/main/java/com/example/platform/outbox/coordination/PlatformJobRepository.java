package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.PlatformJob.PLATFORM_JOB;


@Repository
public class PlatformJobRepository {

    private final DSLContext dsl;

    public PlatformJobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PlatformJob create(JobType jobType, String aggregateType, String aggregateId,
                                String tenantId, String projectId, String payloadJson) {
        String id = Ids.newId("pjob");
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(PLATFORM_JOB)
                .columns(PLATFORM_JOB.ID, PLATFORM_JOB.JOB_TYPE, PLATFORM_JOB.AGGREGATE_TYPE,
                        PLATFORM_JOB.AGGREGATE_ID, PLATFORM_JOB.TENANT_ID, PLATFORM_JOB.PROJECT_ID,
                        PLATFORM_JOB.STATUS, PLATFORM_JOB.REQUIRED_MASK, PLATFORM_JOB.COMPLETED_MASK,
                        PLATFORM_JOB.FAILED_MASK, PLATFORM_JOB.TOTAL_TASK_COUNT,
                        PLATFORM_JOB.COMPLETED_TASK_COUNT, PLATFORM_JOB.FAILED_TASK_COUNT,
                        PLATFORM_JOB.PAYLOAD_JSON, PLATFORM_JOB.CREATED_AT, PLATFORM_JOB.UPDATED_AT)
                .values(id, jobType.name(), aggregateType, aggregateId, tenantId, projectId,
                        "PENDING", 0, 0, 0, 0, 0, 0, payloadJson, now, now)
                .execute();
        return new PlatformJob(id, jobType, aggregateType, aggregateId, tenantId, projectId,
                JobStatus.PENDING, 0, 0, 0, 0, 0, 0, payloadJson, null,
                now.toInstant(java.time.ZoneOffset.UTC), now.toInstant(java.time.ZoneOffset.UTC), null);
    }

    public Optional<PlatformJob> findById(String jobId) {
        Record r = dsl.select().from(PLATFORM_JOB).where(PLATFORM_JOB.ID.eq(jobId)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(mapJob(r));
    }

    public void updateMask(String jobId, int requiredMask, int completedMask, int failedMask) {
        dsl.update(PLATFORM_JOB)
                .set(PLATFORM_JOB.REQUIRED_MASK, Integer.valueOf(requiredMask))
                .set(PLATFORM_JOB.COMPLETED_MASK, Integer.valueOf(completedMask))
                .set(PLATFORM_JOB.FAILED_MASK, Integer.valueOf(failedMask))
                .set(PLATFORM_JOB.UPDATED_AT, LocalDateTime.now())
                .where(PLATFORM_JOB.ID.eq(jobId)).execute();
    }

    private static PlatformJob mapJob(Record r) {
        return new PlatformJob(
                r.get(PLATFORM_JOB.ID),
                tryParseEnum(JobType.class, r.get(PLATFORM_JOB.JOB_TYPE)),
                r.get(PLATFORM_JOB.AGGREGATE_TYPE),
                r.get(PLATFORM_JOB.AGGREGATE_ID),
                r.get(PLATFORM_JOB.TENANT_ID),
                r.get(PLATFORM_JOB.PROJECT_ID),
                tryParseEnum(JobStatus.class, r.get(PLATFORM_JOB.STATUS)),
                r.get(PLATFORM_JOB.REQUIRED_MASK),
                r.get(PLATFORM_JOB.COMPLETED_MASK),
                r.get(PLATFORM_JOB.FAILED_MASK),
                r.get(PLATFORM_JOB.TOTAL_TASK_COUNT),
                r.get(PLATFORM_JOB.COMPLETED_TASK_COUNT),
                r.get(PLATFORM_JOB.FAILED_TASK_COUNT),
                r.get(PLATFORM_JOB.PAYLOAD_JSON),
                r.get(PLATFORM_JOB.METADATA_JSON),
                toInstant(r.get(PLATFORM_JOB.CREATED_AT)),
                toInstant(r.get(PLATFORM_JOB.UPDATED_AT)),
                toInstant(r.get(PLATFORM_JOB.COMPLETED_AT)));
    }

    private static Instant toInstant(LocalDateTime ldt) { return ldt != null ? ldt.toInstant(java.time.ZoneOffset.UTC) : null; }
    private static <E extends Enum<E>> E tryParseEnum(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); } catch (Exception e) { return null; }
    }
}
