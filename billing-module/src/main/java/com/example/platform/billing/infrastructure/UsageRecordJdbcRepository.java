package com.example.platform.billing.infrastructure;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderRef;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageUnit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JdbcTemplate persistence for the canonical {@link UsageRecord}.
 *
 * <p>Idempotency is enforced at the persistence level via
 * {@code INSERT ... ON CONFLICT (idempotency_key) DO NOTHING} followed by a select-back —
 * application if-exists-skip alone is insufficient. The legacy {@code quantity double}
 * column is written only as a placeholder to satisfy the frozen NOT NULL constraint; the
 * canonical authority is {@code quantity_base_units} + {@code quantity_unit}, which is what
 * is read back.</p>
 */
@Repository
public class UsageRecordJdbcRepository {

    private final JdbcTemplate jdbc;

    public UsageRecordJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Persists a usage record idempotently.
     *
     * <p>If a row with the same {@code idempotency_key} already exists, the insert is a
     * no-op and the existing row is returned. Otherwise the supplied record is inserted and
     * returned.</p>
     *
     * @param record the canonical usage record
     * @return the effective persisted record (inserted or pre-existing)
     */
    public UsageRecord insert(UsageRecord record) {
        int updated = jdbc.update("""
                INSERT INTO usage_record (
                    id, tenant_id, meter_key, quantity, unit, recorded_at, idempotency_key,
                    operation_ref, attempt_ref, dimension, quantity_base_units, quantity_unit,
                    actor_type, actor_ref, provider_ref, capability, provenance, source, observed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                record.recordId(),
                record.tenantId(),
                // Legacy NOT NULL columns: placeholders only. Canonical authority is base_units + unit.
                record.dimension().name(),
                0.0,
                record.quantity().unit().name(),
                Timestamp.from(record.recordedAt()),
                record.idempotencyKey(),
                // Canonical columns.
                record.operationRef().operationId(),
                record.operationRef().attemptId(),
                record.dimension().name(),
                record.quantity().baseUnits(),
                record.quantity().unit().name(),
                record.actorRef() != null ? record.actorRef().actorType() : null,
                record.actorRef() != null ? record.actorRef().actorId() : null,
                record.providerRef() != null ? record.providerRef().providerId() : null,
                record.capability(),
                record.provenance(),
                record.source(),
                record.observedAt() != null ? Timestamp.from(record.observedAt()) : null);

        if (updated == 1) {
            return record;
        }
        // Conflict: a row already exists for this idempotency key — return it.
        return findByIdempotencyKey(record.idempotencyKey()).orElse(record);
    }

    public Optional<UsageRecord> findById(String recordId) {
        List<UsageRecord> rows = jdbc.query(
                "SELECT * FROM usage_record WHERE id = ?", this::mapRow, recordId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<UsageRecord> findByIdempotencyKey(String idempotencyKey) {
        List<UsageRecord> rows = jdbc.query(
                "SELECT * FROM usage_record WHERE idempotency_key = ?", this::mapRow, idempotencyKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Tenant-constrained query — never returns rows from another tenant. */
    public List<UsageRecord> findByTenant(String tenantId) {
        return jdbc.query(
                "SELECT * FROM usage_record WHERE tenant_id = ? ORDER BY recorded_at",
                this::mapRow, tenantId);
    }

    private UsageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        CanonicalActorRef actorRef = null;
        String actorType = rs.getString("actor_type");
        String actorId = rs.getString("actor_ref");
        if (actorId != null) {
            actorRef = new CanonicalActorRef(actorId, actorType);
        }

        OperationRef operationRef = new OperationRef(
                rs.getString("operation_ref"),
                rs.getString("attempt_ref"));

        ProviderRef providerRef = null;
        String providerId = rs.getString("provider_ref");
        if (providerId != null) {
            providerRef = new ProviderRef(providerId);
        }

        UsageDimension dimension = valueOfEnum(UsageDimension.class, rs.getString("dimension"));
        UsageUnit unit = valueOfEnum(UsageUnit.class, rs.getString("quantity_unit"));

        // The frozen schema persists observed_at but not occurredAt; occurredAt is lost on read-back.
        Instant observedAt = toInstant(rs.getTimestamp("observed_at"));

        // The frozen schema persists observed_at but neither project_id nor occurredAt;
        // both are lost on read-back (projectId/occurredAt are optional domain fields).
        return new UsageRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                null,
                actorRef,
                operationRef,
                null,
                providerRef,
                rs.getString("capability"),
                dimension,
                new UsageQuantity(rs.getLong("quantity_base_units"), unit),
                null,
                observedAt,
                toInstant(rs.getTimestamp("recorded_at")),
                rs.getString("idempotency_key"),
                rs.getString("provenance"),
                rs.getString("source"));
    }

    private static <E extends Enum<E>> E valueOfEnum(Class<E> enumClass, String value) {
        if (value == null) {
            throw new IllegalArgumentException(enumClass.getSimpleName() + " value is required but was null");
        }
        return Enum.valueOf(enumClass, value);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
