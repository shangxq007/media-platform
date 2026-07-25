package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.QuotaProfile;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.QuotaProfile.QUOTA_PROFILE;


@Repository

public class QuotaProfileRepository {

    private final DSLContext dsl;

    public QuotaProfileRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(QuotaProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(QUOTA_PROFILE)
                .columns(QUOTA_PROFILE.ID, QUOTA_PROFILE.PROFILE_KEY, QUOTA_PROFILE.NAME, QUOTA_PROFILE.DESCRIPTION,
                        QUOTA_PROFILE.MONTHLY_RENDER_MINUTES, QUOTA_PROFILE.DAILY_RENDER_JOBS,
                        QUOTA_PROFILE.CONCURRENT_RENDER_JOBS, QUOTA_PROFILE.STORAGE_BYTES,
                        QUOTA_PROFILE.GPU_MINUTES, QUOTA_PROFILE.REMOTE_WORKER_JOBS,
                        QUOTA_PROFILE.PROMPT_EXECUTIONS, QUOTA_PROFILE.EXTENSION_EXECUTIONS,
                        QUOTA_PROFILE.API_CALLS_PER_MINUTE, QUOTA_PROFILE.MCP_CALLS_PER_MINUTE,
                        QUOTA_PROFILE.CREATED_AT, QUOTA_PROFILE.UPDATED_AT)
                .values(profile.id(), profile.profileKey(), profile.name(), profile.description(),
                        profile.monthlyRenderMinutes(), profile.dailyRenderJobs(),
                        profile.concurrentRenderJobs(), profile.storageBytes(),
                        profile.gpuMinutes(), Math.toIntExact(profile.remoteWorkerJobs()),
                        profile.promptExecutions(), profile.extensionExecutions(),
                        profile.apiCallsPerMinute(), profile.mcpCallsPerMinute(),
                        now, now)
                .execute();
    }

    public Optional<QuotaProfile> findByKey(String profileKey) {
        return dsl.select()
                .from(QUOTA_PROFILE)
                .where(QUOTA_PROFILE.PROFILE_KEY.eq(profileKey))
                .fetchOptional(this::mapRecord);
    }

    public List<QuotaProfile> findAll() {
        return dsl.select()
                .from(QUOTA_PROFILE)
                .orderBy(QUOTA_PROFILE.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public void update(QuotaProfile profile) {
        dsl.update(QUOTA_PROFILE)
                .set(QUOTA_PROFILE.NAME, profile.name())
                .set(QUOTA_PROFILE.DESCRIPTION, profile.description())
                .set(QUOTA_PROFILE.MONTHLY_RENDER_MINUTES, profile.monthlyRenderMinutes())
                .set(QUOTA_PROFILE.DAILY_RENDER_JOBS, profile.dailyRenderJobs())
                .set(QUOTA_PROFILE.CONCURRENT_RENDER_JOBS, profile.concurrentRenderJobs())
                .set(QUOTA_PROFILE.STORAGE_BYTES, profile.storageBytes())
                .set(QUOTA_PROFILE.GPU_MINUTES, profile.gpuMinutes())
                .set(QUOTA_PROFILE.REMOTE_WORKER_JOBS, Math.toIntExact(profile.remoteWorkerJobs()))
                .set(QUOTA_PROFILE.PROMPT_EXECUTIONS, profile.promptExecutions())
                .set(QUOTA_PROFILE.EXTENSION_EXECUTIONS, profile.extensionExecutions())
                .set(QUOTA_PROFILE.API_CALLS_PER_MINUTE, profile.apiCallsPerMinute())
                .set(QUOTA_PROFILE.MCP_CALLS_PER_MINUTE, profile.mcpCallsPerMinute())
                .set(QUOTA_PROFILE.UPDATED_AT, LocalDateTime.now())
                .where(QUOTA_PROFILE.PROFILE_KEY.eq(profile.profileKey()))
                .execute();
    }

    private QuotaProfile mapRecord(Record r) {
        return new QuotaProfile(
                r.get(QUOTA_PROFILE.ID, String.class),
                r.get(QUOTA_PROFILE.PROFILE_KEY, String.class),
                r.get(QUOTA_PROFILE.NAME, String.class),
                r.get(QUOTA_PROFILE.DESCRIPTION, String.class),
                r.get(QUOTA_PROFILE.MONTHLY_RENDER_MINUTES, Long.class),
                r.get(QUOTA_PROFILE.DAILY_RENDER_JOBS, Integer.class),
                r.get(QUOTA_PROFILE.CONCURRENT_RENDER_JOBS, Integer.class),
                r.get(QUOTA_PROFILE.STORAGE_BYTES, Long.class),
                r.get(QUOTA_PROFILE.GPU_MINUTES, Long.class),
                r.get(QUOTA_PROFILE.REMOTE_WORKER_JOBS, Long.class),
                r.get(QUOTA_PROFILE.PROMPT_EXECUTIONS, Long.class),
                r.get(QUOTA_PROFILE.EXTENSION_EXECUTIONS, Long.class),
                r.get(QUOTA_PROFILE.API_CALLS_PER_MINUTE, Integer.class),
                r.get(QUOTA_PROFILE.MCP_CALLS_PER_MINUTE, Integer.class),
                toInstant(r.get(QUOTA_PROFILE.CREATED_AT, LocalDateTime.class)),
                toInstant(r.get(QUOTA_PROFILE.UPDATED_AT, LocalDateTime.class))
        );
    }

    private Instant toInstant(LocalDateTime odt) {
        return odt != null ? odt.toInstant(java.time.ZoneOffset.UTC) : null;
    }
}
