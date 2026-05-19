package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PricingRuleService {

    private static final Logger log = LoggerFactory.getLogger(PricingRuleService.class);

    private final ConcurrentHashMap<String, PricingRule> pricingRules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CustomPricingRule> customPricingRules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DiscountPolicy> discountPolicies = new ConcurrentHashMap<>();

    public PricingRule createPricingRule(String ruleKey, String name, String description,
                                          PricingModel pricingModel, String meterKey,
                                          long unitPriceMinor, String currencyCode,
                                          List<PricingTier> tiers,
                                          Instant effectiveFrom, Instant effectiveTo) {
        String ruleId = Ids.newId("prr");
        Instant now = Instant.now();
        PricingRule rule = new PricingRule(
                ruleId, ruleKey, name, description, pricingModel, meterKey,
                unitPriceMinor, currencyCode, tiers, "ACTIVE",
                effectiveFrom, effectiveTo, now, now);
        pricingRules.put(ruleKey, rule);
        log.info("PricingRuleService: created pricing rule {} key={}", ruleId, ruleKey);
        return rule;
    }

    public PricingRule getPricingRule(String ruleKey) {
        return pricingRules.get(ruleKey);
    }

    public List<PricingRule> listPricingRules() {
        return List.copyOf(pricingRules.values());
    }

    public PricingRule archivePricingRule(String ruleKey) {
        PricingRule existing = pricingRules.get(ruleKey);
        if (existing == null) {
            throw new IllegalArgumentException("Pricing rule not found: " + ruleKey);
        }
        PricingRule archived = new PricingRule(
                existing.ruleId(), existing.ruleKey(), existing.name(),
                existing.description(), existing.pricingModel(), existing.meterKey(),
                existing.unitPriceMinor(), existing.currencyCode(), existing.tiers(),
                "ARCHIVED", existing.effectiveFrom(), existing.effectiveTo(),
                existing.createdAt(), Instant.now());
        pricingRules.put(ruleKey, archived);
        log.info("PricingRuleService: archived pricing rule {}", ruleKey);
        return archived;
    }

    public CustomPricingRule createCustomPricing(String tenantId, String workspaceId,
                                                    String meterKey, Long overridePriceMinor,
                                                    Double discountPercent,
                                                    Instant effectiveFrom, Instant effectiveTo) {
        String ruleId = Ids.newId("cpr");
        CustomPricingRule rule = new CustomPricingRule(
                ruleId, tenantId, workspaceId, meterKey,
                overridePriceMinor, discountPercent,
                effectiveFrom, effectiveTo, "ACTIVE", Instant.now());
        customPricingRules.put(ruleId, rule);
        log.info("PricingRuleService: created custom pricing {} tenant={} meter={}",
                ruleId, tenantId, meterKey);
        return rule;
    }

    public CustomPricingRule getCustomPricing(String ruleId) {
        return customPricingRules.get(ruleId);
    }

    public List<CustomPricingRule> getCustomPricingForTenant(String tenantId) {
        return customPricingRules.values().stream()
                .filter(r -> tenantId.equals(r.tenantId()))
                .toList();
    }

    public DiscountPolicy createDiscountPolicy(String policyKey, String name, String description,
                                                String discountType, double discountValue,
                                                Map<String, Object> conditions,
                                                Instant effectiveFrom, Instant effectiveTo) {
        String policyId = Ids.newId("dsc");
        DiscountPolicy policy = new DiscountPolicy(
                policyId, policyKey, name, description, discountType,
                discountValue, conditions, "ACTIVE",
                effectiveFrom, effectiveTo, Instant.now());
        discountPolicies.put(policyKey, policy);
        log.info("PricingRuleService: created discount policy {} key={}", policyId, policyKey);
        return policy;
    }

    public DiscountPolicy getDiscountPolicy(String policyKey) {
        return discountPolicies.get(policyKey);
    }

    public List<DiscountPolicy> listDiscountPolicies() {
        return discountPolicies.values().stream()
                .filter(p -> "ACTIVE".equals(p.status()))
                .toList();
    }

    public PricingPreviewResult previewPricing(String tenantId, String meterKey,
                                                 double quantity, Map<String, String> context) {
        PricingRule rule = pricingRules.values().stream()
                .filter(r -> meterKey.equals(r.meterKey()))
                .filter(r -> "ACTIVE".equals(r.status()))
                .findFirst()
                .orElse(null);

        long baseAmountMinor;
        String currencyCode = "USD";
        String pricingModel = "USAGE_BASED";

        if (rule != null) {
            currencyCode = rule.currencyCode();
            pricingModel = rule.pricingModel().name();
            if (rule.tiers() != null && !rule.tiers().isEmpty()) {
                baseAmountMinor = calculateTieredAmount(quantity, rule);
            } else {
                baseAmountMinor = Math.round(quantity * rule.unitPriceMinor());
            }
        } else {
            baseAmountMinor = Math.round(quantity * 100);
        }

        CustomPricingRule customOverride = customPricingRules.values().stream()
                .filter(c -> tenantId.equals(c.tenantId()))
                .filter(c -> meterKey.equals(c.meterKey()))
                .filter(c -> "ACTIVE".equals(c.status()))
                .findFirst()
                .orElse(null);

        long finalAmountMinor = baseAmountMinor;
        if (customOverride != null) {
            if (customOverride.overridePriceMinor() != null) {
                finalAmountMinor = Math.round(quantity * customOverride.overridePriceMinor());
            }
            if (customOverride.discountPercent() != null) {
                finalAmountMinor = Math.round(finalAmountMinor * (1.0 - customOverride.discountPercent() / 100.0));
            }
        }

        DiscountPolicy discount = discountPolicies.values().stream()
                .filter(d -> "ACTIVE".equals(d.status()))
                .filter(d -> isDiscountApplicable(d, context))
                .findFirst()
                .orElse(null);

        if (discount != null) {
            finalAmountMinor = applyDiscount(finalAmountMinor, discount);
        }

        Map<String, Object> breakdown = new java.util.HashMap<>();
        breakdown.put("baseAmountMinor", baseAmountMinor);
        breakdown.put("currencyCode", currencyCode);
        breakdown.put("pricingModel", pricingModel);
        breakdown.put("quantity", quantity);
        breakdown.put("meterKey", meterKey);
        if (customOverride != null) {
            breakdown.put("customPricingApplied", true);
        }
        if (discount != null) {
            breakdown.put("discountPolicyKey", discount.policyKey());
            breakdown.put("discountType", discount.discountType());
            breakdown.put("discountValue", discount.discountValue());
        }

        return new PricingPreviewResult(tenantId, meterKey, quantity,
                finalAmountMinor, currencyCode, breakdown);
    }

    private boolean isDiscountApplicable(DiscountPolicy discount, Map<String, String> context) {
        if (discount.conditions() == null || discount.conditions().isEmpty()) {
            return true;
        }
        return discount.conditions().entrySet().stream()
                .allMatch(e -> context != null && e.getValue().toString().equals(context.get(e.getKey())));
    }

    private long applyDiscount(long amountMinor, DiscountPolicy discount) {
        return switch (discount.discountType()) {
            case "PERCENTAGE" -> Math.round(amountMinor * (1.0 - discount.discountValue() / 100.0));
            case "FLAT" -> Math.max(0, amountMinor - Math.round(discount.discountValue()));
            default -> amountMinor;
        };
    }

    private long calculateTieredAmount(double quantity, PricingRule rule) {
        long totalMinor = 0;
        double remaining = quantity;
        for (PricingTier tier : rule.tiers()) {
            if (remaining <= 0) break;
            double tierQuantity = Math.min(remaining, tier.upToQuantity());
            totalMinor += Math.round(tierQuantity * tier.unitPriceMinor()) + tier.flatFeeMinor();
            remaining -= tierQuantity;
        }
        if (remaining > 0) {
            PricingTier lastTier = rule.tiers().get(rule.tiers().size() - 1);
            totalMinor += Math.round(remaining * lastTier.unitPriceMinor());
        }
        return totalMinor;
    }

    public record PricingPreviewResult(
            String tenantId,
            String meterKey,
            double quantity,
            long estimatedAmountMinor,
            String currencyCode,
            Map<String, Object> breakdown) {
    }
}
