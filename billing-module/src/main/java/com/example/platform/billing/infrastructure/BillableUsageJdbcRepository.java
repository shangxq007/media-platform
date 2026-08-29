package com.example.platform.billing.infrastructure;

import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.billing.usage.MeteringTransformationKind;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Single durable append/replay store for Billing-owned BillableUsage. */
@Repository
public class BillableUsageJdbcRepository {

    private final JdbcTemplate jdbc;

    public BillableUsageJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BillableUsage append(BillableUsage usage) {
        return appendResult(usage).usage();
    }

    public AppendResult appendResult(BillableUsage usage) {
        Objects.requireNonNull(usage, "usage must not be null");
        int inserted = jdbc.update("""
                INSERT INTO billable_usage (
                    billable_usage_id, tenant_id, principal_type, principal_id,
                    observed_usage_id, observed_dimension, observed_quantity_base_units,
                    observed_quantity_unit, billable_meter, billable_dimension,
                    billable_quantity_base_units, billable_quantity_unit,
                    metering_rule_id, metering_rule_version, transformation_kind,
                    transformation_details, source_observation_timestamp, metered_at,
                    idempotency_key, trace_id, provenance_reference
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                usage.billableUsageId(),
                usage.tenantId(),
                usage.principalRef().actorType(),
                usage.principalRef().actorId(),
                usage.observedUsageId(),
                usage.observedDimension().name(),
                usage.observedQuantity().baseUnits(),
                usage.observedQuantity().unit().name(),
                usage.billableMeter(),
                usage.billableDimension().name(),
                usage.billableQuantity().baseUnits(),
                usage.billableQuantity().unit().name(),
                usage.meteringRuleId(),
                usage.meteringRuleVersion(),
                usage.transformationKind().name(),
                usage.transformationDetails(),
                Timestamp.from(usage.sourceObservationTimestamp()),
                Timestamp.from(usage.meteredAt()),
                usage.idempotencyKey(),
                usage.traceId(),
                usage.provenanceReference());
        if (inserted == 1) {
            return new AppendResult(usage, true);
        }
        BillableUsage existing = findByTenantAndIdempotencyKey(
                usage.tenantId(), usage.idempotencyKey()).orElseThrow(
                        () -> new IllegalStateException("Billable conflict row was not readable"));
        if (!sameSemanticPayload(existing, usage)) {
            throw new IllegalStateException(
                    "Idempotency key reused with different billable usage payload");
        }
        return new AppendResult(existing, false);
    }

    public Optional<BillableUsage> findByTenantAndId(String tenantId, String billableUsageId) {
        List<BillableUsage> rows = jdbc.query("""
                SELECT * FROM billable_usage
                WHERE tenant_id = ? AND billable_usage_id = ?
                """, this::mapRow, tenantId, billableUsageId);
        return rows.stream().findFirst();
    }

    public Optional<BillableUsage> findByTenantAndIdempotencyKey(
            String tenantId, String idempotencyKey) {
        List<BillableUsage> rows = jdbc.query("""
                SELECT * FROM billable_usage
                WHERE tenant_id = ? AND idempotency_key = ?
                """, this::mapRow, tenantId, idempotencyKey);
        return rows.stream().findFirst();
    }

    public List<BillableUsage> findByTenant(String tenantId) {
        return jdbc.query("""
                SELECT * FROM billable_usage
                WHERE tenant_id = ? ORDER BY metered_at, billable_usage_id
                """, this::mapRow, tenantId);
    }

    private BillableUsage mapRow(ResultSet rs, int rowNumber) throws SQLException {
        UsageDimension observedDimension = enumValue(
                UsageDimension.class, rs.getString("observed_dimension"), "observed_dimension");
        UsageUnit observedUnit = enumValue(
                UsageUnit.class, rs.getString("observed_quantity_unit"),
                "observed_quantity_unit");
        UsageDimension billableDimension = enumValue(
                UsageDimension.class, rs.getString("billable_dimension"), "billable_dimension");
        UsageUnit billableUnit = enumValue(
                UsageUnit.class, rs.getString("billable_quantity_unit"),
                "billable_quantity_unit");
        return new BillableUsage(
                rs.getString("billable_usage_id"),
                rs.getString("tenant_id"),
                new CanonicalActorRef(rs.getString("principal_id"), rs.getString("principal_type")),
                rs.getString("observed_usage_id"),
                observedDimension,
                new UsageQuantity(rs.getLong("observed_quantity_base_units"), observedUnit),
                rs.getString("billable_meter"),
                billableDimension,
                new UsageQuantity(rs.getLong("billable_quantity_base_units"), billableUnit),
                rs.getString("metering_rule_id"),
                rs.getString("metering_rule_version"),
                enumValue(MeteringTransformationKind.class,
                        rs.getString("transformation_kind"), "transformation_kind"),
                rs.getString("transformation_details"),
                instant(rs, "source_observation_timestamp"),
                instant(rs, "metered_at"),
                rs.getString("idempotency_key"),
                rs.getString("trace_id"),
                rs.getString("provenance_reference"));
    }

    private static boolean sameSemanticPayload(BillableUsage left, BillableUsage right) {
        return Objects.equals(left.tenantId(), right.tenantId())
                && Objects.equals(left.principalRef(), right.principalRef())
                && Objects.equals(left.observedUsageId(), right.observedUsageId())
                && left.observedDimension() == right.observedDimension()
                && Objects.equals(left.observedQuantity(), right.observedQuantity())
                && Objects.equals(left.billableMeter(), right.billableMeter())
                && left.billableDimension() == right.billableDimension()
                && Objects.equals(left.billableQuantity(), right.billableQuantity())
                && Objects.equals(left.meteringRuleId(), right.meteringRuleId())
                && Objects.equals(left.meteringRuleVersion(), right.meteringRuleVersion())
                && left.transformationKind() == right.transformationKind()
                && Objects.equals(left.transformationDetails(), right.transformationDetails())
                && Objects.equals(left.sourceObservationTimestamp(), right.sourceObservationTimestamp())
                && Objects.equals(left.idempotencyKey(), right.idempotencyKey())
                && Objects.equals(left.traceId(), right.traceId())
                && Objects.equals(left.provenanceReference(), right.provenanceReference());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        if (value == null) {
            throw new IllegalStateException(column + " must not be null");
        }
        return value.toInstant();
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " must not be null/blank");
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Unknown " + field + ": " + value, failure);
        }
    }

    public record AppendResult(BillableUsage usage, boolean inserted) {}
}
