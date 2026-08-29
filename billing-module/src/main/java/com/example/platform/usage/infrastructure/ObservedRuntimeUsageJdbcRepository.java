package com.example.platform.usage.infrastructure;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.shared.usage.RuntimeOutcome;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageProvenance;
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

/** Single subordinate durable append store for neutral runtime observations. */
@Repository
public class ObservedRuntimeUsageJdbcRepository {

    private final JdbcTemplate jdbc;

    public ObservedRuntimeUsageJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ObservedRuntimeUsage append(ObservedRuntimeUsage observation) {
        Objects.requireNonNull(observation, "observation must not be null");
        int inserted = jdbc.update("""
                INSERT INTO observed_runtime_usage (
                    observed_usage_id, tenant_id, project_id, principal_type, principal_id,
                    operation_ref, attempt_ref, execution_ref, provider_ref, capability,
                    dimension, quantity_base_units, quantity_unit, operation_outcome,
                    occurred_at, observed_at, recorded_at, provenance, source,
                    source_reference, trace_id, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """,
                observation.observedUsageId(),
                observation.tenantId(),
                observation.projectId(),
                observation.principalRef().actorType(),
                observation.principalRef().actorId(),
                observation.operationRef().operationId(),
                observation.operationRef().attemptId(),
                observation.executionRef(),
                observation.providerRef().providerId(),
                observation.capability(),
                observation.dimension().name(),
                observation.quantity().baseUnits(),
                observation.quantity().unit().name(),
                observation.outcome().name(),
                Timestamp.from(observation.occurredAt()),
                Timestamp.from(observation.observedAt()),
                Timestamp.from(observation.recordedAt()),
                observation.provenance().name(),
                observation.source(),
                observation.sourceReference(),
                observation.traceId(),
                observation.idempotencyKey());
        if (inserted == 1) {
            return observation;
        }
        ObservedRuntimeUsage existing = findByTenantAndIdempotencyKey(
                observation.tenantId(), observation.idempotencyKey()).orElseThrow(
                        () -> new IllegalStateException("Observation conflict row was not readable"));
        if (!sameSemanticPayload(existing, observation)) {
            throw new IllegalStateException(
                    "Idempotency key reused with different observed usage payload");
        }
        return existing;
    }

    public Optional<ObservedRuntimeUsage> findByTenantAndId(String tenantId, String observedUsageId) {
        List<ObservedRuntimeUsage> rows = jdbc.query("""
                SELECT * FROM observed_runtime_usage
                WHERE tenant_id = ? AND observed_usage_id = ?
                """, this::mapRow, tenantId, observedUsageId);
        return rows.stream().findFirst();
    }

    public Optional<ObservedRuntimeUsage> findByTenantAndIdempotencyKey(
            String tenantId, String idempotencyKey) {
        List<ObservedRuntimeUsage> rows = jdbc.query("""
                SELECT * FROM observed_runtime_usage
                WHERE tenant_id = ? AND idempotency_key = ?
                """, this::mapRow, tenantId, idempotencyKey);
        return rows.stream().findFirst();
    }

    public List<ObservedRuntimeUsage> findByTenant(String tenantId) {
        return jdbc.query("""
                SELECT * FROM observed_runtime_usage
                WHERE tenant_id = ? ORDER BY recorded_at, observed_usage_id
                """, this::mapRow, tenantId);
    }

    private ObservedRuntimeUsage mapRow(ResultSet rs, int rowNumber) throws SQLException {
        UsageDimension dimension = enumValue(
                UsageDimension.class, rs.getString("dimension"), "dimension");
        UsageUnit unit = enumValue(UsageUnit.class, rs.getString("quantity_unit"), "quantity_unit");
        return new ObservedRuntimeUsage(
                rs.getString("observed_usage_id"),
                rs.getString("tenant_id"),
                rs.getString("project_id"),
                new CanonicalActorRef(rs.getString("principal_id"), rs.getString("principal_type")),
                OperationRef.of(rs.getString("operation_ref"), rs.getString("attempt_ref")),
                rs.getString("execution_ref"),
                new ProviderRef(rs.getString("provider_ref")),
                rs.getString("capability"),
                dimension,
                new UsageQuantity(rs.getLong("quantity_base_units"), unit),
                enumValue(RuntimeOutcome.class, rs.getString("operation_outcome"), "operation_outcome"),
                instant(rs, "occurred_at"),
                instant(rs, "observed_at"),
                instant(rs, "recorded_at"),
                enumValue(UsageProvenance.class, rs.getString("provenance"), "provenance"),
                rs.getString("source"),
                rs.getString("source_reference"),
                rs.getString("trace_id"),
                rs.getString("idempotency_key"));
    }

    private static boolean sameSemanticPayload(
            ObservedRuntimeUsage left, ObservedRuntimeUsage right) {
        return Objects.equals(left.tenantId(), right.tenantId())
                && Objects.equals(left.projectId(), right.projectId())
                && Objects.equals(left.principalRef(), right.principalRef())
                && Objects.equals(left.operationRef(), right.operationRef())
                && Objects.equals(left.executionRef(), right.executionRef())
                && Objects.equals(left.providerRef(), right.providerRef())
                && Objects.equals(left.capability(), right.capability())
                && left.dimension() == right.dimension()
                && Objects.equals(left.quantity(), right.quantity())
                && left.outcome() == right.outcome()
                && Objects.equals(left.occurredAt(), right.occurredAt())
                && Objects.equals(left.observedAt(), right.observedAt())
                && Objects.equals(left.recordedAt(), right.recordedAt())
                && left.provenance() == right.provenance()
                && Objects.equals(left.source(), right.source())
                && Objects.equals(left.sourceReference(), right.sourceReference())
                && Objects.equals(left.traceId(), right.traceId())
                && Objects.equals(left.idempotencyKey(), right.idempotencyKey());
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
}
