package com.example.platform.billing.infrastructure;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.CostType;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderCostObservation;
import com.example.platform.billing.usage.ProviderRef;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JdbcTemplate persistence for the canonical {@link ProviderCostObservation}.
 *
 * <p>Idempotency is enforced at the persistence level via
 * {@code INSERT ... ON CONFLICT (idempotency_key) DO NOTHING} followed by a select-back.</p>
 */
@Repository
public class ProviderCostObservationJdbcRepository {

    private final JdbcTemplate jdbc;

    public ProviderCostObservationJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Persists a provider cost observation idempotently.
     *
     * @param observation the canonical provider cost observation
     * @return the effective persisted observation (inserted or pre-existing)
     */
    public ProviderCostObservation insert(ProviderCostObservation observation) {
        int updated = jdbc.update("""
                INSERT INTO provider_cost_observation (
                    id, tenant_id, project_id, actor_type, actor_ref,
                    operation_ref, execution_ref, provider_ref, capability,
                    amount_minor, currency_code, cost_type, source, observed_at,
                    usage_record_id, idempotency_key
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                observation.observationId(),
                observation.tenantId(),
                observation.projectId(),
                observation.actorRef() != null ? observation.actorRef().actorType() : null,
                observation.actorRef() != null ? observation.actorRef().actorId() : null,
                observation.operationRef().operationId(),
                observation.executionRef(),
                observation.providerRef().providerId(),
                observation.capability(),
                observation.amountMinor(),
                observation.currencyCode(),
                observation.costType().name(),
                observation.source(),
                Timestamp.from(observation.observedAt()),
                observation.usageRecordId(),
                observation.idempotencyKey());

        if (updated == 1) {
            return observation;
        }
        return findByIdempotencyKey(observation.idempotencyKey()).orElse(observation);
    }

    public Optional<ProviderCostObservation> findByIdempotencyKey(String idempotencyKey) {
        List<ProviderCostObservation> rows = jdbc.query(
                "SELECT * FROM provider_cost_observation WHERE idempotency_key = ?",
                this::mapRow, idempotencyKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Tenant-constrained query — never returns rows from another tenant. */
    public List<ProviderCostObservation> findByTenant(String tenantId) {
        return jdbc.query(
                "SELECT * FROM provider_cost_observation WHERE tenant_id = ? ORDER BY observed_at",
                this::mapRow, tenantId);
    }

    public List<ProviderCostObservation> findByOperationRef(String operationRef) {
        return jdbc.query(
                "SELECT * FROM provider_cost_observation WHERE operation_ref = ? ORDER BY observed_at",
                this::mapRow, operationRef);
    }

    /** Finds cost observations linked to a specific usage record (cost↔usage correlation). */
    public List<ProviderCostObservation> findByUsageRecordId(String usageRecordId) {
        return jdbc.query(
                "SELECT * FROM provider_cost_observation WHERE usage_record_id = ? ORDER BY observed_at",
                this::mapRow, usageRecordId);
    }

    private ProviderCostObservation mapRow(ResultSet rs, int rowNum) throws SQLException {
        CanonicalActorRef actorRef = null;
        String actorType = rs.getString("actor_type");
        String actorId = rs.getString("actor_ref");
        if (actorId != null) {
            actorRef = new CanonicalActorRef(actorId, actorType);
        }

        OperationRef operationRef = new OperationRef(
                rs.getString("operation_ref"),
                rs.getString("execution_ref"));

        ProviderRef providerRef = new ProviderRef(rs.getString("provider_ref"));

        CostType costType = Enum.valueOf(CostType.class, rs.getString("cost_type"));

        return new ProviderCostObservation(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("project_id"),
                actorRef,
                operationRef,
                rs.getString("execution_ref"),
                providerRef,
                rs.getString("capability"),
                rs.getBigDecimal("amount_minor"),
                rs.getString("currency_code"),
                costType,
                rs.getString("source"),
                toInstant(rs.getTimestamp("observed_at")),
                rs.getString("usage_record_id"),
                rs.getString("idempotency_key"));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
