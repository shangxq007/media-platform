package com.example.platform.render.infrastructure.billing;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

/**
 * Repository for render billing records.
 */
@Repository
public class RenderBillingRecordRepository {

    private static final Logger log = LoggerFactory.getLogger(RenderBillingRecordRepository.class);

    private final DSLContext dsl;

    public RenderBillingRecordRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Save a billing record.
     */
    public void save(RenderBillingRecord record) {
        dsl.insertInto(table("render_billing_record"))
                .columns(
                        field("id"),
                        field("job_id"),
                        field("tenant_id"),
                        field("estimated_cost"),
                        field("actual_cost"),
                        field("usage_seconds"),
                        field("provider_id"),
                        field("output_size_bytes"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .values(
                        record.id(),
                        record.jobId(),
                        record.tenantId(),
                        record.estimatedCost(),
                        record.actualCost(),
                        record.usageSeconds(),
                        record.providerId(),
                        record.outputSizeBytes(),
                        record.status().name(),
                        OffsetDateTime.from(record.createdAt()),
                        record.completedAt() != null ? OffsetDateTime.from(record.completedAt()) : null
                )
                .onConflict(field("id"))
                .doUpdate()
                .set(field("actual_cost"), record.actualCost())
                .set(field("usage_seconds"), record.usageSeconds())
                .set(field("provider_id"), record.providerId())
                .set(field("output_size_bytes"), record.outputSizeBytes())
                .set(field("status"), record.status().name())
                .set(field("completed_at"), record.completedAt() != null ? OffsetDateTime.from(record.completedAt()) : null)
                .execute();

        log.debug("Saved billing record: id={} jobId={} status={}", 
                record.id(), record.jobId(), record.status());
    }

    /**
     * Find billing record by job ID.
     */
    public RenderBillingRecord findByJobId(String jobId) {
        Record record = dsl.select(
                        field("id"),
                        field("job_id"),
                        field("tenant_id"),
                        field("estimated_cost"),
                        field("actual_cost"),
                        field("usage_seconds"),
                        field("provider_id"),
                        field("output_size_bytes"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .from(table("render_billing_record"))
                .where(field("job_id").eq(jobId))
                .fetchOne();

        if (record == null) {
            return null;
        }

        return mapToRecord(record);
    }

    /**
     * Find billing record by ID.
     */
    public RenderBillingRecord findById(String id) {
        Record record = dsl.select(
                        field("id"),
                        field("job_id"),
                        field("tenant_id"),
                        field("estimated_cost"),
                        field("actual_cost"),
                        field("usage_seconds"),
                        field("provider_id"),
                        field("output_size_bytes"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .from(table("render_billing_record"))
                .where(field("id").eq(id))
                .fetchOne();

        if (record == null) {
            return null;
        }

        return mapToRecord(record);
    }

    /**
     * List billing records for a tenant.
     */
    public List<RenderBillingRecord> findByTenantId(String tenantId) {
        return dsl.select(
                        field("id"),
                        field("job_id"),
                        field("tenant_id"),
                        field("estimated_cost"),
                        field("actual_cost"),
                        field("usage_seconds"),
                        field("provider_id"),
                        field("output_size_bytes"),
                        field("status"),
                        field("created_at"),
                        field("completed_at")
                )
                .from(table("render_billing_record"))
                .where(field("tenant_id").eq(tenantId))
                .fetch(this::mapToRecord);
    }

    private RenderBillingRecord mapToRecord(Record record) {
        OffsetDateTime completedAt = record.get(field("completed_at"), OffsetDateTime.class);
        return new RenderBillingRecord(
                record.get(field("id", String.class)),
                record.get(field("job_id", String.class)),
                record.get(field("tenant_id", String.class)),
                record.get(field("estimated_cost", Double.class)),
                record.get(field("actual_cost", Double.class)),
                record.get(field("usage_seconds", Long.class)),
                record.get(field("provider_id", String.class)),
                record.get(field("output_size_bytes", Long.class)),
                BillingRecordStatus.valueOf(record.get(field("status", String.class))),
                record.get(field("created_at", OffsetDateTime.class)).toInstant(),
                completedAt != null ? completedAt.toInstant() : null
        );
    }
}
