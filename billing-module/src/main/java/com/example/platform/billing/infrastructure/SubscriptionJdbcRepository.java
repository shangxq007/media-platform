package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.domain.SubscriptionPlan;
import com.example.platform.shared.Jsons;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class SubscriptionJdbcRepository {

    private static final TypeReference<Map<String, Long>> QUOTA_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> CONTRACT_META = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public SubscriptionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void savePlan(SubscriptionPlan plan) {
        String quotaJson = plan.includedQuota() != null ? Jsons.toJson(plan.includedQuota()) : null;
        int updated = jdbc.update("""
                UPDATE subscription_plan SET
                name = ?, description = ?, billing_interval = ?, base_price_minor = ?,
                currency_code = ?, included_quota = ?, status = ?, updated_at = ?
                WHERE plan_key = ?
                """,
                plan.name(),
                plan.description(),
                plan.billingInterval(),
                plan.basePriceMinor(),
                plan.currencyCode(),
                quotaJson,
                plan.status(),
                Timestamp.from(plan.updatedAt()),
                plan.planKey());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO subscription_plan
                    (id, plan_key, name, description, billing_interval, base_price_minor,
                     currency_code, included_quota, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    plan.planId(),
                    plan.planKey(),
                    plan.name(),
                    plan.description(),
                    plan.billingInterval(),
                    plan.basePriceMinor(),
                    plan.currencyCode(),
                    quotaJson,
                    plan.status(),
                    Timestamp.from(plan.createdAt()),
                    Timestamp.from(plan.updatedAt()));
        }
    }

    public void saveContract(SubscriptionContract contract) {
        String metaJson = Jsons.toJson(Map.of(
                "basePriceMinor", contract.basePriceMinor(),
                "currencyCode", contract.currencyCode(),
                "includedQuota", contract.includedQuota() != null ? contract.includedQuota() : Map.of(),
                "includedQuotaUsed", contract.includedQuotaUsed() != null ? contract.includedQuotaUsed() : Map.of(),
                "tenantId", contract.tenantId(),
                "userId", contract.userId(),
                "contractRole", contract.contractRole() != null ? contract.contractRole().name() : "BASE",
                "productCode", contract.productCode() != null ? contract.productCode() : contract.planKey()
        ));
        int updated = jdbc.update("""
                UPDATE subscription_contract SET
                subject_type = ?, subject_id = ?, canonical_product_code = ?,
                contract_state = ?, period_start_at = ?, period_end_at = ?,
                plan_key = ?, included_quota_used = ?
                WHERE id = ?
                """,
                "USER",
                contract.userId(),
                contract.planKey(),
                contract.lifecycleState(),
                Timestamp.from(contract.periodStartAt()),
                contract.periodEndAt() != null ? Timestamp.from(contract.periodEndAt()) : null,
                contract.planKey(),
                metaJson,
                contract.contractId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO subscription_contract
                    (id, subject_type, subject_id, canonical_product_code, contract_state,
                     period_start_at, period_end_at, created_at, plan_key, included_quota_used)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    contract.contractId(),
                    "USER",
                    contract.userId(),
                    contract.planKey(),
                    contract.lifecycleState(),
                    Timestamp.from(contract.periodStartAt()),
                    contract.periodEndAt() != null ? Timestamp.from(contract.periodEndAt()) : null,
                    Timestamp.from(Instant.now()),
                    contract.planKey(),
                    metaJson);
        }
    }

    public List<SubscriptionPlan> loadAllPlans() {
        return jdbc.query("SELECT * FROM subscription_plan ORDER BY created_at", this::mapPlan);
    }

    public List<SubscriptionContract> loadAllContracts() {
        return jdbc.query("SELECT * FROM subscription_contract ORDER BY created_at", this::mapContract);
    }

    public Optional<SubscriptionPlan> findPlanByKey(String planKey) {
        List<SubscriptionPlan> rows = jdbc.query(
                "SELECT * FROM subscription_plan WHERE plan_key = ?",
                this::mapPlan,
                planKey);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private SubscriptionPlan mapPlan(ResultSet rs, int rowNum) throws SQLException {
        String quotaRaw = rs.getString("included_quota");
        Map<String, Long> quota = quotaRaw != null && !quotaRaw.isBlank()
                ? Jsons.fromJson(quotaRaw, QUOTA_MAP)
                : Map.of();
        return new SubscriptionPlan(
                rs.getString("id"),
                rs.getString("plan_key"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("billing_interval"),
                rs.getLong("base_price_minor"),
                rs.getString("currency_code"),
                quota,
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private SubscriptionContract mapContract(ResultSet rs, int rowNum) throws SQLException {
        String metaRaw = rs.getString("included_quota_used");
        Map<String, Object> meta = metaRaw != null && !metaRaw.isBlank()
                ? Jsons.fromJson(metaRaw, CONTRACT_META)
                : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Long> includedQuota = meta.get("includedQuota") instanceof Map
                ? (Map<String, Long>) meta.get("includedQuota") : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Long> includedQuotaUsed = meta.get("includedQuotaUsed") instanceof Map
                ? (Map<String, Long>) meta.get("includedQuotaUsed") : Map.of();
        String tenantId = meta.get("tenantId") != null ? meta.get("tenantId").toString() : rs.getString("subject_id");
        String userId = meta.get("userId") != null ? meta.get("userId").toString() : rs.getString("subject_id");
        long basePrice = meta.get("basePriceMinor") instanceof Number n ? n.longValue() : 0L;
        String currency = meta.get("currencyCode") != null ? meta.get("currencyCode").toString() : "USD";
        String planKey = rs.getString("plan_key");
        if (planKey == null || planKey.isBlank()) {
            planKey = rs.getString("canonical_product_code");
        }
        Timestamp periodEnd = rs.getTimestamp("period_end_at");
        SubscriptionContractRole role = SubscriptionContractRole.BASE;
        if (meta.get("contractRole") != null) {
            try {
                role = SubscriptionContractRole.valueOf(meta.get("contractRole").toString());
            } catch (IllegalArgumentException ignored) {
                role = SubscriptionContractRole.BASE;
            }
        }
        String productCode = meta.get("productCode") != null
                ? meta.get("productCode").toString()
                : rs.getString("canonical_product_code");
        return new SubscriptionContract(
                rs.getString("id"),
                tenantId,
                userId,
                planKey,
                toInstant(rs.getTimestamp("period_start_at")),
                periodEnd != null ? periodEnd.toInstant() : null,
                rs.getString("contract_state"),
                basePrice,
                currency,
                includedQuota,
                includedQuotaUsed,
                role,
                productCode);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }
}
