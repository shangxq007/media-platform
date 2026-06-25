package com.example.platform.outbox.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.outbox.domain.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformTaskRepository {

    private final DSLContext dsl;

    public PlatformTaskRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public PlatformTask create(String jobId, String taskType, TaskCapability capability,
                                 String provider, int bitPosition) {
        String id = Ids.newId("ptsk");
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("platform_task"))
                .columns(field("id"), field("job_id"), field("task_type"),
                        field("capability"), field("provider"), field("status"),
                        field("attempt_count"), field("max_attempts"),
                        field("bit_position"), field("created_at"), field("updated_at"))
                .values(id, jobId, taskType, capability.name(), provider, "PENDING",
                        0, 3, bitPosition, now, now)
                .execute();
        return new PlatformTask(id, jobId, taskType, capability, provider, TaskStatus.PENDING,
                0, 3, null, null, null, bitPosition, null, null,
                now.toInstant(), now.toInstant());
    }

    public Optional<PlatformTask> findById(String taskId) {
        Record r = dsl.select().from(table("platform_task")).where(field("id").eq(taskId)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(mapTask(r));
    }

    public List<PlatformTask> listByJob(String jobId) {
        return dsl.select().from(table("platform_task"))
                .where(field("job_id").eq(jobId))
                .orderBy(field("bit_position").asc())
                .fetch().map(PlatformTaskRepository::mapTask);
    }

    public boolean lease(String taskId) {
        return dsl.update(table("platform_task"))
                .set(field("status"), "LEASED")
                .set(field("started_at"), OffsetDateTime.now())
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(taskId).and(field("status").eq("PENDING")))
                .execute() > 0;
    }

    public void complete(String taskId, String resultRef) {
        dsl.update(table("platform_task"))
                .set(field("status"), "COMPLETED")
                .set(field("result_ref"), resultRef)
                .set(field("completed_at"), OffsetDateTime.now())
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(taskId)).execute();
    }

    public void fail(String taskId, String errorMessage) {
        dsl.update(table("platform_task"))
                .set(field("status"), "FAILED")
                .set(field("error_message"), errorMessage)
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("id").eq(taskId)).execute();
    }

    public List<PlatformTask> listPendingByCapability(TaskCapability capability, int limit) {
        return dsl.select().from(table("platform_task"))
                .where(field("capability").eq(capability.name())
                        .and(field("status").eq("PENDING")))
                .orderBy(field("created_at").asc())
                .limit(limit)
                .fetch().map(PlatformTaskRepository::mapTask);
    }

    public int resetStaleLeases(int leaseTimeoutMinutes) {
        Condition stale = field("status").eq("LEASED")
                .and(field("started_at").lt(OffsetDateTime.now().minusMinutes(leaseTimeoutMinutes)));
        return dsl.update(table("platform_task"))
                .set(field("status"), "PENDING")
                .set(field("started_at"), (OffsetDateTime) null)
                .set(field("updated_at"), OffsetDateTime.now())
                .where(stale)
                .execute();
    }

    private static PlatformTask mapTask(Record r) {
        return new PlatformTask(
                r.get(field("id", String.class)),
                r.get(field("job_id", String.class)),
                r.get(field("task_type", String.class)),
                tryParse(TaskCapability.class, r.get(field("capability", String.class))),
                r.get(field("provider", String.class)),
                tryParse(TaskStatus.class, r.get(field("status", String.class))),
                r.get(field("attempt_count", Integer.class)),
                r.get(field("max_attempts", Integer.class)),
                r.get(field("result_ref", String.class)),
                r.get(field("result_json", String.class)),
                r.get(field("error_message", String.class)),
                r.get(field("bit_position", Integer.class)),
                toInstant(r.get(field("started_at", OffsetDateTime.class))),
                toInstant(r.get(field("completed_at", OffsetDateTime.class))),
                toInstant(r.get(field("created_at", OffsetDateTime.class))),
                toInstant(r.get(field("updated_at", OffsetDateTime.class))));
    }

    private static Instant toInstant(OffsetDateTime odt) { return odt != null ? odt.toInstant() : null; }
    private static <E extends Enum<E>> E tryParse(Class<E> type, String value) {
        try { return Enum.valueOf(type, value); } catch (Exception e) { return null; }
    }
}
