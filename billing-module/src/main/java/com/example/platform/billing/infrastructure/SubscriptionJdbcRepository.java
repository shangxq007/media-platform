package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.SubscriptionCommand;
import com.example.platform.billing.domain.SubscriptionCommandResult;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.domain.SubscriptionPlan;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.commercial.PrincipalRef;
import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sole physical writer for {@code subscription_contract}. */
@Repository
public class SubscriptionJdbcRepository {

    private static final TypeReference<Map<String, Long>> QUOTA_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> CONTRACT_META = new TypeReference<>() {};
    private final JdbcTemplate jdbc;

    public SubscriptionJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void savePlan(SubscriptionPlan plan) {
        String quotaJson = plan.includedQuota() == null ? null : Jsons.toJson(plan.includedQuota());
        int updated = jdbc.update("""
                UPDATE subscription_plan SET name = ?, description = ?, billing_interval = ?,
                    base_price_minor = ?, currency_code = ?, included_quota = ?, status = ?, updated_at = ?
                WHERE plan_key = ?
                """, plan.name(), plan.description(), plan.billingInterval(), plan.basePriceMinor(),
                plan.currencyCode(), quotaJson, plan.status(), Timestamp.from(plan.updatedAt()), plan.planKey());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO subscription_plan
                        (id, plan_key, name, description, billing_interval, base_price_minor,
                         currency_code, included_quota, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, plan.planId(), plan.planKey(), plan.name(), plan.description(),
                    plan.billingInterval(), plan.basePriceMinor(), plan.currencyCode(), quotaJson,
                    plan.status(), Timestamp.from(plan.createdAt()), Timestamp.from(plan.updatedAt()));
        }
    }

    public boolean claim(String commandId, SubscriptionCommand command, Instant now) {
        return jdbc.update("""
                INSERT INTO subscription_command
                    (id, tenant_id, principal_type, principal_id, idempotency_key,
                     command_type, payload_fingerprint, actor, reason, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
                """, commandId, command.principal().tenantId(), command.principal().principalType().name(),
                command.principal().principalId(), command.idempotencyKey(), command.commandType().name(),
                command.fingerprint(), command.actor(), command.reason(), command.traceId(),
                Timestamp.from(now)) == 1;
    }

    public SubscriptionCommandResult replay(SubscriptionCommand command) {
        List<SubscriptionCommandResult> rows = jdbc.query("""
                SELECT id, payload_fingerprint, result_snapshot
                FROM subscription_command WHERE tenant_id = ? AND idempotency_key = ?
                """, (rs, row) -> {
                    if (!command.fingerprint().equals(rs.getString("payload_fingerprint"))) {
                        throw new IllegalStateException(
                                "Idempotency key reused with different subscription command payload");
                    }
                    String snapshot = rs.getString("result_snapshot");
                    if (snapshot == null) {
                        throw new IllegalStateException("Subscription command has no committed result");
                    }
                    return new SubscriptionCommandResult(rs.getString("id"),
                            Jsons.fromJson(snapshot, SubscriptionContract.class));
                }, command.principal().tenantId(), command.idempotencyKey());
        if (rows.size() != 1) throw new IllegalStateException("Subscription command claim not found");
        return rows.get(0);
    }

    public void complete(String commandId, SubscriptionContract contract, Instant completedAt) {
        int updated = jdbc.update("""
                UPDATE subscription_command SET result_snapshot = ?, completed_at = ?
                WHERE id = ? AND result_snapshot IS NULL
                """, Jsons.toJson(contract), Timestamp.from(completedAt), commandId);
        if (updated != 1) throw new IllegalStateException("Subscription command audit completion failed");
    }

    public void cancelActiveBase(PrincipalRef principal, Instant effectiveAt) {
        jdbc.update("""
                UPDATE subscription_contract
                SET contract_state = 'CANCELLED', version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND subject_type = ? AND subject_id = ?
                  AND contract_role = 'BASE' AND contract_state = 'ACTIVE'
                """, Timestamp.from(effectiveAt), principal.tenantId(),
                principal.principalType().name(), principal.principalId());
    }

    public void insertContract(SubscriptionContract contract, PrincipalRef principal, Instant createdAt) {
        int inserted = jdbc.update("""
                INSERT INTO subscription_contract
                    (id, tenant_id, subject_type, subject_id, canonical_product_code,
                     contract_role, contract_state, period_start_at, period_end_at,
                     created_at, updated_at, plan_key, included_quota_used, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, contract.contractId(), contract.tenantId(), principal.principalType().name(), contract.userId(),
                contract.productCode(), contract.contractRole().name(), contract.lifecycleState(),
                Timestamp.from(contract.periodStartAt()),
                contract.periodEndAt() == null ? null : Timestamp.from(contract.periodEndAt()),
                Timestamp.from(createdAt), Timestamp.from(createdAt), contract.planKey(),
                metadata(contract), contract.version());
        if (inserted != 1) throw new IllegalStateException("Subscription insert did not write one row");
    }

    public SubscriptionContract transition(
            PrincipalRef principal, String contractId, long expectedVersion,
            SubscriptionContract replacement, String requiredState) {
        int updated = jdbc.update("""
                UPDATE subscription_contract
                SET canonical_product_code = ?, contract_state = ?, period_start_at = ?,
                    period_end_at = ?, updated_at = ?, plan_key = ?, included_quota_used = ?,
                    version = version + 1
                WHERE id = ? AND tenant_id = ? AND subject_type = ? AND subject_id = ?
                  AND contract_state = ? AND version = ?
                """, replacement.productCode(), replacement.lifecycleState(),
                Timestamp.from(replacement.periodStartAt()),
                replacement.periodEndAt() == null ? null : Timestamp.from(replacement.periodEndAt()),
                Timestamp.from(Instant.now()), replacement.planKey(), metadata(replacement),
                contractId, principal.tenantId(), principal.principalType().name(),
                principal.principalId(), requiredState, expectedVersion);
        if (updated != 1) {
            if (findByPrincipalAndId(principal, contractId).isEmpty()) {
                throw new IllegalArgumentException("Subscription not found for principal: " + contractId);
            }
            throw new IllegalStateException("Stale or illegal subscription transition: " + contractId);
        }
        return findByPrincipalAndId(principal, contractId)
                .orElseThrow(() -> new IllegalStateException("Updated subscription not found"));
    }

    public Optional<SubscriptionContract> findByPrincipalAndId(PrincipalRef principal, String contractId) {
        List<SubscriptionContract> rows = jdbc.query("""
                SELECT * FROM subscription_contract
                WHERE id = ? AND tenant_id = ? AND subject_type = ? AND subject_id = ?
                """, this::mapContract, contractId, principal.tenantId(),
                principal.principalType().name(), principal.principalId());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<SubscriptionContract> findActive(PrincipalRef principal, Instant now) {
        return jdbc.query("""
                SELECT * FROM subscription_contract
                WHERE tenant_id = ? AND subject_type = ? AND subject_id = ?
                  AND contract_state = 'ACTIVE' AND (period_end_at IS NULL OR period_end_at > ?)
                ORDER BY created_at DESC
                """, this::mapContract, principal.tenantId(), principal.principalType().name(),
                principal.principalId(), Timestamp.from(now));
    }

    public List<SubscriptionPlan> loadAllPlans() {
        return jdbc.query("SELECT * FROM subscription_plan ORDER BY created_at", this::mapPlan);
    }

    public List<SubscriptionContract> loadAllContracts() {
        return jdbc.query("SELECT * FROM subscription_contract ORDER BY created_at", this::mapContract);
    }

    public Optional<SubscriptionPlan> findPlanByKey(String planKey) {
        List<SubscriptionPlan> rows = jdbc.query(
                "SELECT * FROM subscription_plan WHERE plan_key = ?", this::mapPlan, planKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private SubscriptionPlan mapPlan(ResultSet rs, int rowNum) throws SQLException {
        String quotaRaw = rs.getString("included_quota");
        Map<String, Long> quota = quotaRaw == null || quotaRaw.isBlank()
                ? Map.of() : Jsons.fromJson(quotaRaw, QUOTA_MAP);
        return new SubscriptionPlan(rs.getString("id"), rs.getString("plan_key"),
                rs.getString("name"), rs.getString("description"), rs.getString("billing_interval"),
                rs.getLong("base_price_minor"), rs.getString("currency_code"), quota,
                rs.getString("status"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private SubscriptionContract mapContract(ResultSet rs, int rowNum) throws SQLException {
        String raw = rs.getString("included_quota_used");
        Map<String, Object> meta = raw == null || raw.isBlank()
                ? Map.of() : Jsons.fromJson(raw, CONTRACT_META);
        Timestamp end = rs.getTimestamp("period_end_at");
        return new SubscriptionContract(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("subject_id"), rs.getString("plan_key"),
                instant(rs.getTimestamp("period_start_at")), end == null ? null : end.toInstant(),
                rs.getString("contract_state"), number(meta.get("basePriceMinor")),
                string(meta.get("currencyCode"), "USD"), longMap(meta.get("includedQuota")),
                longMap(meta.get("includedQuotaUsed")),
                SubscriptionContractRole.valueOf(rs.getString("contract_role")),
                rs.getString("canonical_product_code"), rs.getLong("version"));
    }

    private static String metadata(SubscriptionContract contract) {
        return Jsons.toJson(Map.of(
                "basePriceMinor", contract.basePriceMinor(),
                "currencyCode", contract.currencyCode(),
                "includedQuota", contract.includedQuota() == null ? Map.of() : contract.includedQuota(),
                "includedQuotaUsed", contract.includedQuotaUsed() == null ? Map.of() : contract.includedQuotaUsed()));
    }

    private static long number(Object value) { return value instanceof Number number ? number.longValue() : 0L; }
    private static String string(Object value, String fallback) { return value == null ? fallback : value.toString(); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Map<String, Long> longMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        java.util.HashMap<String, Long> converted = new java.util.HashMap<>();
        map.forEach((key, item) -> {
            if (key != null && item instanceof Number number) converted.put(key.toString(), number.longValue());
        });
        return Map.copyOf(converted);
    }
}
