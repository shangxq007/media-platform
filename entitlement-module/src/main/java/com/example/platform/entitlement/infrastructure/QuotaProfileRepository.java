package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.QuotaProfile;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

@Repository

public class QuotaProfileRepository {

    private final DSLContext dsl;

    public QuotaProfileRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(QuotaProfile profile) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("QUOTA_PROFILE"))
                .columns(field("ID"), field("PROFILE_KEY"), field("NAME"), field("DESCRIPTION"),
                        field("MONTHLY_RENDER_MINUTES"), field("DAILY_RENDER_JOBS"),
                        field("CONCURRENT_RENDER_JOBS"), field("STORAGE_BYTES"),
                        field("GPU_MINUTES"), field("REMOTE_WORKER_JOBS"),
                        field("PROMPT_EXECUTIONS"), field("EXTENSION_EXECUTIONS"),
                        field("API_CALLS_PER_MINUTE"), field("MCP_CALLS_PER_MINUTE"),
                        field("CREATED_AT"), field("UPDATED_AT"))
                .values(profile.id(), profile.profileKey(), profile.name(), profile.description(),
                        profile.monthlyRenderMinutes(), profile.dailyRenderJobs(),
                        profile.concurrentRenderJobs(), profile.storageBytes(),
                        profile.gpuMinutes(), profile.remoteWorkerJobs(),
                        profile.promptExecutions(), profile.extensionExecutions(),
                        profile.apiCallsPerMinute(), profile.mcpCallsPerMinute(),
                        now, now)
                .execute();
    }

    public Optional<QuotaProfile> findByKey(String profileKey) {
        return dsl.select()
                .from(table("QUOTA_PROFILE"))
                .where(field("PROFILE_KEY").eq(profileKey))
                .fetchOptional(this::mapRecord);
    }

    public List<QuotaProfile> findAll() {
        return dsl.select()
                .from(table("QUOTA_PROFILE"))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    public void update(QuotaProfile profile) {
        dsl.update(table("QUOTA_PROFILE"))
                .set(field("NAME"), profile.name())
                .set(field("DESCRIPTION"), profile.description())
                .set(field("MONTHLY_RENDER_MINUTES"), profile.monthlyRenderMinutes())
                .set(field("DAILY_RENDER_JOBS"), profile.dailyRenderJobs())
                .set(field("CONCURRENT_RENDER_JOBS"), profile.concurrentRenderJobs())
                .set(field("STORAGE_BYTES"), profile.storageBytes())
                .set(field("GPU_MINUTES"), profile.gpuMinutes())
                .set(field("REMOTE_WORKER_JOBS"), profile.remoteWorkerJobs())
                .set(field("PROMPT_EXECUTIONS"), profile.promptExecutions())
                .set(field("EXTENSION_EXECUTIONS"), profile.extensionExecutions())
                .set(field("API_CALLS_PER_MINUTE"), profile.apiCallsPerMinute())
                .set(field("MCP_CALLS_PER_MINUTE"), profile.mcpCallsPerMinute())
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("PROFILE_KEY").eq(profile.profileKey()))
                .execute();
    }

    private QuotaProfile mapRecord(Record r) {
        return new QuotaProfile(
                r.get(field("ID"), String.class),
                r.get(field("PROFILE_KEY"), String.class),
                r.get(field("NAME"), String.class),
                r.get(field("DESCRIPTION"), String.class),
                r.get(field("MONTHLY_RENDER_MINUTES"), Long.class),
                r.get(field("DAILY_RENDER_JOBS"), Integer.class),
                r.get(field("CONCURRENT_RENDER_JOBS"), Integer.class),
                r.get(field("STORAGE_BYTES"), Long.class),
                r.get(field("GPU_MINUTES"), Long.class),
                r.get(field("REMOTE_WORKER_JOBS"), Long.class),
                r.get(field("PROMPT_EXECUTIONS"), Long.class),
                r.get(field("EXTENSION_EXECUTIONS"), Long.class),
                r.get(field("API_CALLS_PER_MINUTE"), Integer.class),
                r.get(field("MCP_CALLS_PER_MINUTE"), Integer.class),
                toInstant(r.get(field("CREATED_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("UPDATED_AT"), OffsetDateTime.class))
        );
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
