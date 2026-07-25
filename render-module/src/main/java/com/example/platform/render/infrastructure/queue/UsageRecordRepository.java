package com.example.platform.render.infrastructure.queue;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import static com.example.platform.typedschema.jooq.generated.tables.RenderUsageRecord.RENDER_USAGE_RECORD;
import org.jooq.impl.DSL;


/**
 * Minimal usage record for billing.
 * 
 * <p>Records job completion with duration and cost.
 * No policy engine, no credit system, no pricing engine.
 */
@Repository
public class UsageRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(UsageRecordRepository.class);

    private final DSLContext dsl;

    public UsageRecordRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Record usage for a completed job.
     */
    public void recordUsage(String jobId, String tenantId, long durationSeconds, double cost) {
        dsl.insertInto(RENDER_USAGE_RECORD)
                .columns(
                        RENDER_USAGE_RECORD.JOB_ID,
                        RENDER_USAGE_RECORD.TENANT_ID,
                        RENDER_USAGE_RECORD.DURATION_SECONDS,
                        RENDER_USAGE_RECORD.COST,
                        RENDER_USAGE_RECORD.CREATED_AT
                )
                .values(
                        jobId,
                        tenantId,
                        durationSeconds,
                        cost,
                        LocalDateTime.now()
                )
                .execute();

        log.info("Recorded usage for job {}: {} seconds, ${}", jobId, durationSeconds, 
                String.format("%.4f", cost));
    }

    /**
     * Get total usage for a tenant.
     */
    public TenantUsage getTenantUsage(String tenantId) {
        var jobCountField = DSL.field(DSL.raw("count(*)"), Long.class).as("job_count");
        var totalSecondsField = DSL.field(DSL.raw("sum(duration_seconds)"), Long.class).as("total_seconds");
        var totalCostField = DSL.field(DSL.raw("sum(cost)"), Double.class).as("total_cost");
        var record = dsl.select(
                        jobCountField,
                        totalSecondsField,
                        totalCostField
                )
                .from(RENDER_USAGE_RECORD)
                .where(RENDER_USAGE_RECORD.TENANT_ID.eq(tenantId))
                .fetchOne();

        return new TenantUsage(
                tenantId,
                record.get(jobCountField).intValue(),
                record.get(totalSecondsField),
                record.get(totalCostField)
        );
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record TenantUsage(
            String tenantId,
            int jobCount,
            long totalSeconds,
            double totalCost
    ) {}
}
