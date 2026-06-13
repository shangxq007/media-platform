package com.example.platform.render.infrastructure.queue;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

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
        dsl.insertInto(table("render_usage_record"))
                .columns(
                        field("job_id"),
                        field("tenant_id"),
                        field("duration_seconds"),
                        field("cost"),
                        field("created_at")
                )
                .values(
                        jobId,
                        tenantId,
                        durationSeconds,
                        cost,
                        OffsetDateTime.now()
                )
                .execute();

        log.info("Recorded usage for job {}: {} seconds, ${}", jobId, durationSeconds, 
                String.format("%.4f", cost));
    }

    /**
     * Get total usage for a tenant.
     */
    public TenantUsage getTenantUsage(String tenantId) {
        var record = dsl.select(
                        field("count(*)").as("job_count"),
                        field("sum(duration_seconds)").as("total_seconds"),
                        field("sum(cost)").as("total_cost")
                )
                .from(table("render_usage_record"))
                .where(field("tenant_id").eq(tenantId))
                .fetchOne();

        return new TenantUsage(
                tenantId,
                record.get(field("job_count", Integer.class)),
                record.get(field("total_seconds", Long.class)),
                record.get(field("total_cost", Double.class))
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
