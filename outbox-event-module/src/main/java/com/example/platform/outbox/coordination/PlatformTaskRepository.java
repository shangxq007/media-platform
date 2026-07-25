package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.PlatformTask.PLATFORM_TASK;


@Repository
public class PlatformTaskRepository {

    private final DSLContext dsl;

    public PlatformTaskRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PlatformTask create(String jobId, String taskType, TaskCapability capability,
                                 String provider, int bitPosition) {
        String id = Ids.newId("ptsk");
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(PLATFORM_TASK)
                .columns(PLATFORM_TASK.ID, PLATFORM_TASK.JOB_ID, PLATFORM_TASK.TASK_TYPE,
                        PLATFORM_TASK.CAPABILITY, PLATFORM_TASK.PROVIDER, PLATFORM_TASK.STATUS,
                        PLATFORM_TASK.ATTEMPT_COUNT, PLATFORM_TASK.MAX_ATTEMPTS,
                        PLATFORM_TASK.BIT_POSITION, PLATFORM_TASK.CREATED_AT, PLATFORM_TASK.UPDATED_AT)
                .values(id, jobId, taskType, capability.name(), provider, "PENDING",
                        0, 3, bitPosition, now, now)
                .execute();
        return new PlatformTask(id, jobId, taskType, capability, provider, TaskStatus.PENDING,
                0, 3, null, null, null, bitPosition, null, null,
                now.toInstant(java.time.ZoneOffset.UTC), now.toInstant(java.time.ZoneOffset.UTC));
    }

    public Optional<PlatformTask> findById(String taskId) {
        Record r = dsl.select().from(PLATFORM_TASK).where(PLATFORM_TASK.ID.eq(taskId)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(mapTask(r));
    }

    public List<PlatformTask> listByJob(String jobId) {
        return dsl.select().from(PLATFORM_TASK)
                .where(PLATFORM_TASK.JOB_ID.eq(jobId))
                .orderBy(PLATFORM_TASK.BIT_POSITION.asc())
                .fetch().map(PlatformTaskRepository::mapTask);
    }

    public boolean lease(String taskId) {
        return dsl.update(PLATFORM_TASK)
                .set(PLATFORM_TASK.STATUS, "LEASED")
                .set(PLATFORM_TASK.STARTED_AT, LocalDateTime.now())
                .set(PLATFORM_TASK.UPDATED_AT, LocalDateTime.now())
                .where(PLATFORM_TASK.ID.eq(taskId).and(PLATFORM_TASK.STATUS.eq("PENDING")))
                .execute() > 0;
    }

    public void complete(String taskId, String resultRef) {
        dsl.update(PLATFORM_TASK)
                .set(PLATFORM_TASK.STATUS, "COMPLETED")
                .set(PLATFORM_TASK.RESULT_REF, resultRef)
                .set(PLATFORM_TASK.COMPLETED_AT, LocalDateTime.now())
                .set(PLATFORM_TASK.UPDATED_AT, LocalDateTime.now())
                .where(PLATFORM_TASK.ID.eq(taskId)).execute();
    }

    public void fail(String taskId, String errorMessage) {
        dsl.update(PLATFORM_TASK)
                .set(PLATFORM_TASK.STATUS, "FAILED")
                .set(PLATFORM_TASK.ERROR_MESSAGE, errorMessage)
                .set(PLATFORM_TASK.UPDATED_AT, LocalDateTime.now())
                .where(PLATFORM_TASK.ID.eq(taskId)).execute();
    }

    public List<PlatformTask> listPendingByCapability(TaskCapability capability, int limit) {
        return dsl.select().from(PLATFORM_TASK)
                .where(PLATFORM_TASK.CAPABILITY.eq(capability.name())
                        .and(PLATFORM_TASK.STATUS.eq("PENDING")))
                .orderBy(PLATFORM_TASK.CREATED_AT.asc())
                .limit(limit)
                .fetch().map(PlatformTaskRepository::mapTask);
    }

    public int resetStaleLeases(int leaseTimeoutMinutes) {
        Condition stale = PLATFORM_TASK.STATUS.eq("LEASED")
                .and(PLATFORM_TASK.STARTED_AT.lt(LocalDateTime.now().minusMinutes(leaseTimeoutMinutes)));
        return dsl.update(PLATFORM_TASK)
                .set(PLATFORM_TASK.STATUS, "PENDING")
                .set(PLATFORM_TASK.STARTED_AT, (LocalDateTime) null)
                .set(PLATFORM_TASK.UPDATED_AT, LocalDateTime.now())
                .where(stale)
                .execute();
    }

    private static PlatformTask mapTask(Record r) {
        return new PlatformTask(
                r.get(PLATFORM_TASK.ID),
                r.get(PLATFORM_TASK.JOB_ID),
                r.get(PLATFORM_TASK.TASK_TYPE),
                tryParse(TaskCapability.class, r.get(PLATFORM_TASK.CAPABILITY)),
                r.get(PLATFORM_TASK.PROVIDER),
                tryParse(TaskStatus.class, r.get(PLATFORM_TASK.STATUS)),
                r.get(PLATFORM_TASK.ATTEMPT_COUNT),
                r.get(PLATFORM_TASK.MAX_ATTEMPTS),
                r.get(PLATFORM_TASK.RESULT_REF),
                r.get(PLATFORM_TASK.RESULT_JSON),
                r.get(PLATFORM_TASK.ERROR_MESSAGE),
                r.get(PLATFORM_TASK.BIT_POSITION),
                toInstant(r.get(PLATFORM_TASK.STARTED_AT)),
                toInstant(r.get(PLATFORM_TASK.COMPLETED_AT)),
                toInstant(r.get(PLATFORM_TASK.CREATED_AT)),
                toInstant(r.get(PLATFORM_TASK.UPDATED_AT)));
    }

    private static Instant toInstant(LocalDateTime ldt) { return ldt != null ? ldt.toInstant(java.time.ZoneOffset.UTC) : null; }
    private static <E extends Enum<E>> E tryParse(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); } catch (Exception e) { return null; }
    }
}
