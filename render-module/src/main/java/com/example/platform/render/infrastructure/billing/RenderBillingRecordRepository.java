package com.example.platform.render.infrastructure.billing;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import static com.example.platform.typedschema.jooq.generated.tables.RenderBillingRecord.RENDER_BILLING_RECORD;


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
        dsl.insertInto(RENDER_BILLING_RECORD)
                .columns(
                        RENDER_BILLING_RECORD.ID,
                        RENDER_BILLING_RECORD.JOB_ID,
                        RENDER_BILLING_RECORD.TENANT_ID,
                        RENDER_BILLING_RECORD.ESTIMATED_COST,
                        RENDER_BILLING_RECORD.ACTUAL_COST,
                        RENDER_BILLING_RECORD.USAGE_SECONDS,
                        RENDER_BILLING_RECORD.PROVIDER_ID,
                        RENDER_BILLING_RECORD.OUTPUT_SIZE_BYTES,
                        RENDER_BILLING_RECORD.STATUS,
                        RENDER_BILLING_RECORD.CREATED_AT,
                        RENDER_BILLING_RECORD.COMPLETED_AT
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
                        record.createdAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime(),
                        record.completedAt() != null ? record.completedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null
                )
                .onConflict(RENDER_BILLING_RECORD.ID)
                .doUpdate()
                .set(RENDER_BILLING_RECORD.ACTUAL_COST, record.actualCost())
                .set(RENDER_BILLING_RECORD.USAGE_SECONDS, record.usageSeconds())
                .set(RENDER_BILLING_RECORD.PROVIDER_ID, record.providerId())
                .set(RENDER_BILLING_RECORD.OUTPUT_SIZE_BYTES, record.outputSizeBytes())
                .set(RENDER_BILLING_RECORD.STATUS, record.status().name())
                .set(RENDER_BILLING_RECORD.COMPLETED_AT, record.completedAt() != null ? record.completedAt().atZone(java.time.ZoneOffset.UTC).toLocalDateTime() : null)
                .execute();

        log.debug("Saved billing record: id={} jobId={} status={}", 
                record.id(), record.jobId(), record.status());
    }

    /**
     * Find billing record by job ID.
     */
    public RenderBillingRecord findByJobId(String jobId) {
        Record record = dsl.select(
                        RENDER_BILLING_RECORD.ID,
                        RENDER_BILLING_RECORD.JOB_ID,
                        RENDER_BILLING_RECORD.TENANT_ID,
                        RENDER_BILLING_RECORD.ESTIMATED_COST,
                        RENDER_BILLING_RECORD.ACTUAL_COST,
                        RENDER_BILLING_RECORD.USAGE_SECONDS,
                        RENDER_BILLING_RECORD.PROVIDER_ID,
                        RENDER_BILLING_RECORD.OUTPUT_SIZE_BYTES,
                        RENDER_BILLING_RECORD.STATUS,
                        RENDER_BILLING_RECORD.CREATED_AT,
                        RENDER_BILLING_RECORD.COMPLETED_AT
                )
                .from(RENDER_BILLING_RECORD)
                .where(RENDER_BILLING_RECORD.JOB_ID.eq(jobId))
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
                        RENDER_BILLING_RECORD.ID,
                        RENDER_BILLING_RECORD.JOB_ID,
                        RENDER_BILLING_RECORD.TENANT_ID,
                        RENDER_BILLING_RECORD.ESTIMATED_COST,
                        RENDER_BILLING_RECORD.ACTUAL_COST,
                        RENDER_BILLING_RECORD.USAGE_SECONDS,
                        RENDER_BILLING_RECORD.PROVIDER_ID,
                        RENDER_BILLING_RECORD.OUTPUT_SIZE_BYTES,
                        RENDER_BILLING_RECORD.STATUS,
                        RENDER_BILLING_RECORD.CREATED_AT,
                        RENDER_BILLING_RECORD.COMPLETED_AT
                )
                .from(RENDER_BILLING_RECORD)
                .where(RENDER_BILLING_RECORD.ID.eq(id))
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
                        RENDER_BILLING_RECORD.ID,
                        RENDER_BILLING_RECORD.JOB_ID,
                        RENDER_BILLING_RECORD.TENANT_ID,
                        RENDER_BILLING_RECORD.ESTIMATED_COST,
                        RENDER_BILLING_RECORD.ACTUAL_COST,
                        RENDER_BILLING_RECORD.USAGE_SECONDS,
                        RENDER_BILLING_RECORD.PROVIDER_ID,
                        RENDER_BILLING_RECORD.OUTPUT_SIZE_BYTES,
                        RENDER_BILLING_RECORD.STATUS,
                        RENDER_BILLING_RECORD.CREATED_AT,
                        RENDER_BILLING_RECORD.COMPLETED_AT
                )
                .from(RENDER_BILLING_RECORD)
                .where(RENDER_BILLING_RECORD.TENANT_ID.eq(tenantId))
                .fetch(this::mapToRecord);
    }

    private RenderBillingRecord mapToRecord(Record record) {
        OffsetDateTime completedAt = record.get(RENDER_BILLING_RECORD.COMPLETED_AT, OffsetDateTime.class);
        return new RenderBillingRecord(
                record.get(RENDER_BILLING_RECORD.ID),
                record.get(RENDER_BILLING_RECORD.JOB_ID),
                record.get(RENDER_BILLING_RECORD.TENANT_ID),
                record.get(RENDER_BILLING_RECORD.ESTIMATED_COST),
                record.get(RENDER_BILLING_RECORD.ACTUAL_COST),
                record.get(RENDER_BILLING_RECORD.USAGE_SECONDS),
                record.get(RENDER_BILLING_RECORD.PROVIDER_ID),
                record.get(RENDER_BILLING_RECORD.OUTPUT_SIZE_BYTES),
                BillingRecordStatus.valueOf(record.get(RENDER_BILLING_RECORD.STATUS)),
                record.get(RENDER_BILLING_RECORD.CREATED_AT).toInstant(ZoneOffset.UTC),
                completedAt != null ? completedAt.toInstant() : null
        );
    }
}
