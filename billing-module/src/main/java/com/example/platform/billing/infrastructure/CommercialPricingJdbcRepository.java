package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.CustomPricingRule;
import com.example.platform.billing.domain.DiscountPolicy;
import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.shared.commercial.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The sole durable writer for commercial pricing rules and exact overrides. */
@Repository
public class CommercialPricingJdbcRepository {

    private final JdbcTemplate jdbc;

    public CommercialPricingJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PricingRule saveRule(PricingRule rule) {
        try {
            jdbc.update("""
                    INSERT INTO pricing_rule
                    (id, tenant_id, rule_key, rule_version, name, description, pricing_model,
                     meter_key, unit_price_minor, currency_code, tier_config, status,
                     effective_from, effective_to, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, rule_key, rule_version) DO NOTHING
                    """, rule.ruleId(), rule.tenantId(), rule.ruleKey(), rule.version(),
                    rule.name(), rule.description(), rule.pricingModel().name(), rule.meterKey(),
                    rule.unitPriceMinor(), rule.currencyCode(), encodeTiers(rule.tiers()), rule.status(),
                    Timestamp.from(rule.effectiveFrom()), timestamp(rule.effectiveTo()),
                    Timestamp.from(rule.createdAt()), Timestamp.from(rule.updatedAt()));
        } catch (DuplicateKeyException duplicateId) {
            throw new IllegalStateException("pricing rule identity conflicts with existing rule", duplicateId);
        }
        PricingRule stored = findRule(rule.tenantId(), rule.ruleKey(), rule.version()).orElseThrow();
        if (!stored.equals(rule)) {
            throw new IllegalStateException("Pricing rule key/version reused with different payload");
        }
        return stored;
    }

    public CustomPricingRule saveOverride(CustomPricingRule rule) {
        jdbc.update("""
                INSERT INTO custom_pricing_rule
                (id, tenant_id, workspace_id, meter_key, rule_version, override_price_minor,
                 currency_code, discount_numerator, discount_denominator, effective_from,
                 effective_to, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, rule.ruleId(), rule.tenantId(), rule.workspaceId(), rule.meterKey(), rule.version(),
                rule.overridePriceMinor(), rule.currencyCode() == null ? "XXX" : rule.currencyCode(),
                rule.discountNumerator(), rule.discountDenominator(), Timestamp.from(rule.effectiveFrom()),
                timestamp(rule.effectiveTo()), rule.status(), Timestamp.from(rule.createdAt()));
        CustomPricingRule stored = findOverrideById(rule.tenantId(), rule.ruleId()).orElseGet(() ->
                findOverride(rule.tenantId(), rule.workspaceId(), rule.meterKey(),
                        rule.effectiveFrom()).orElseThrow());
        if (!stored.equals(rule)) {
            throw new IllegalStateException("Pricing override key/version reused with different payload");
        }
        return stored;
    }

    public Optional<PricingRule> findRule(String tenantId, String ruleKey, long version) {
        return jdbc.query("""
                SELECT * FROM pricing_rule
                WHERE tenant_id = ? AND rule_key = ? AND rule_version = ?
                """, this::mapRule, tenantId, ruleKey, version).stream().findFirst();
    }

    public Optional<PricingRule> findEffectiveRule(String tenantId, String ruleKey,
                                                   long version, Instant at) {
        Optional<PricingRule> tenantRule = findRule(tenantId, ruleKey, version)
                .filter(rule -> rule.effectiveAt(at));
        return tenantRule.isPresent() ? tenantRule
                : findRule("GLOBAL", ruleKey, version).filter(rule -> rule.effectiveAt(at));
    }

    public List<PricingRule> findRulesByTenant(String tenantId) {
        return jdbc.query("""
                SELECT * FROM pricing_rule WHERE tenant_id = ?
                ORDER BY rule_key, rule_version
                """, this::mapRule, tenantId);
    }

    public Optional<CustomPricingRule> findOverride(String tenantId, String workspaceId,
                                                    String meterKey, Instant at) {
        List<CustomPricingRule> candidates = jdbc.query("""
                SELECT * FROM custom_pricing_rule
                WHERE tenant_id = ? AND meter_key = ? AND status = 'ACTIVE'
                  AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
                  AND (workspace_id = ? OR workspace_id IS NULL)
                """, this::mapOverride, tenantId, meterKey, Timestamp.from(at), Timestamp.from(at),
                workspaceId);
        return candidates.stream()
                .sorted(Comparator.comparing((CustomPricingRule value) ->
                        value.workspaceId() != null && value.workspaceId().equals(workspaceId)).reversed()
                        .thenComparing(Comparator.comparingLong(CustomPricingRule::version).reversed()))
                .findFirst();
    }

    public List<CustomPricingRule> findOverridesByTenant(String tenantId) {
        return jdbc.query("""
                SELECT * FROM custom_pricing_rule WHERE tenant_id = ?
                ORDER BY meter_key, rule_version
                """, this::mapOverride, tenantId);
    }

    public Optional<CustomPricingRule> findOverrideById(String tenantId, String id) {
        return jdbc.query("SELECT * FROM custom_pricing_rule WHERE tenant_id = ? AND id = ?",
                this::mapOverride, tenantId, id).stream().findFirst();
    }

    public void updateRuleStatus(String tenantId, String ruleKey, long version,
                                 String expectedStatus, String newStatus, Instant updatedAt) {
        int changed = jdbc.update("""
                UPDATE pricing_rule SET status = ?, updated_at = ?
                WHERE tenant_id = ? AND rule_key = ? AND rule_version = ? AND status = ?
                """, newStatus, Timestamp.from(updatedAt), tenantId, ruleKey, version, expectedStatus);
        if (changed != 1) throw new IllegalStateException("pricing rule status transition failed");
    }

    public DiscountPolicy saveDiscount(DiscountPolicy policy) {
        jdbc.update("""
                INSERT INTO discount_policy
                (id, tenant_id, policy_key, rule_version, meter_key, currency_code,
                 name, description, discount_type, discount_numerator,
                 discount_denominator, flat_amount_minor, conditions, status,
                 effective_from, effective_to, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, policy_key, rule_version) DO NOTHING
                """, policy.policyId(), policy.tenantId(), policy.policyKey(), policy.version(),
                policy.meterKey(), policy.currencyCode(), policy.name(), policy.description(),
                policy.discountType(), policy.discountNumerator(), policy.discountDenominator(),
                policy.flatAmountMinor(), encodeMap(policy.conditions()), policy.status(),
                Timestamp.from(policy.effectiveFrom()), timestamp(policy.effectiveTo()),
                Timestamp.from(policy.createdAt()));
        DiscountPolicy stored = findDiscount(policy.tenantId(), policy.policyKey(), policy.version())
                .orElseThrow();
        if (!stored.equals(policy)) {
            throw new IllegalStateException("Discount key/version reused with different payload");
        }
        return stored;
    }

    public Optional<DiscountPolicy> findDiscount(String tenantId, String key, long version) {
        return jdbc.query("""
                SELECT * FROM discount_policy
                WHERE tenant_id = ? AND policy_key = ? AND rule_version = ?
                """, this::mapDiscount, tenantId, key, version).stream().findFirst();
    }

    public List<DiscountPolicy> findEffectiveDiscounts(String tenantId, String meterKey, Instant at) {
        return jdbc.query("""
                SELECT * FROM discount_policy
                WHERE tenant_id IN (?, 'GLOBAL') AND meter_key = ? AND status = 'ACTIVE'
                AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
                ORDER BY CASE WHEN tenant_id = ? THEN 0 ELSE 1 END, policy_key, rule_version
                """, this::mapDiscount, tenantId, meterKey, Timestamp.from(at),
                Timestamp.from(at), tenantId);
    }

    public List<DiscountPolicy> findDiscountsByTenant(String tenantId, Instant at) {
        return jdbc.query("""
                SELECT * FROM discount_policy WHERE tenant_id = ? AND status = 'ACTIVE'
                AND effective_from <= ? AND (effective_to IS NULL OR effective_to > ?)
                ORDER BY policy_key, rule_version
                """, this::mapDiscount, tenantId, Timestamp.from(at), Timestamp.from(at));
    }

    private PricingRule mapRule(ResultSet rs, int row) throws SQLException {
        return new PricingRule(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("rule_key"), rs.getLong("rule_version"), rs.getString("name"),
                rs.getString("description"), PricingModel.valueOf(rs.getString("pricing_model")),
                rs.getString("meter_key"), new Money(rs.getLong("unit_price_minor"),
                rs.getString("currency_code")), decodeTiers(rs.getString("tier_config")),
                rs.getString("status"), instant(rs.getTimestamp("effective_from")),
                instant(rs.getTimestamp("effective_to")), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private CustomPricingRule mapOverride(ResultSet rs, int row) throws SQLException {
        Long amount = (Long) rs.getObject("override_price_minor");
        return new CustomPricingRule(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("workspace_id"), rs.getString("meter_key"), rs.getLong("rule_version"),
                amount == null ? null : new Money(amount, rs.getString("currency_code")),
                rs.getLong("discount_numerator"), rs.getLong("discount_denominator"),
                instant(rs.getTimestamp("effective_from")), instant(rs.getTimestamp("effective_to")),
                rs.getString("status"), instant(rs.getTimestamp("created_at")));
    }

    private DiscountPolicy mapDiscount(ResultSet rs, int row) throws SQLException {
        return new DiscountPolicy(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("policy_key"), rs.getLong("rule_version"),
                rs.getString("meter_key"), rs.getString("currency_code"),
                rs.getString("name"), rs.getString("description"), rs.getString("discount_type"),
                rs.getLong("discount_numerator"), rs.getLong("discount_denominator"),
                rs.getLong("flat_amount_minor"), decodeMap(rs.getString("conditions")),
                rs.getString("status"), instant(rs.getTimestamp("effective_from")),
                instant(rs.getTimestamp("effective_to")), instant(rs.getTimestamp("created_at")));
    }

    private static String encodeTiers(List<PricingTier> tiers) {
        return tiers.stream().map(tier -> tier.upToQuantity() + "," + tier.unitPriceMinor()
                + "," + tier.flatFeeMinor()).collect(Collectors.joining(";"));
    }

    private static List<PricingTier> decodeTiers(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<PricingTier> result = new ArrayList<>();
        for (String encoded : value.split(";")) {
            String[] parts = encoded.split(",", -1);
            result.add(new PricingTier(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                    Long.parseLong(parts[2])));
        }
        return List.copyOf(result);
    }

    private static String encodeMap(Map<String, String> values) {
        return new TreeMap<>(values).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, String> decodeMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        TreeMap<String, String> result = new TreeMap<>();
        for (String line : value.split("\n")) {
            int separator = line.indexOf('=');
            if (separator > 0) result.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return Map.copyOf(result);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
