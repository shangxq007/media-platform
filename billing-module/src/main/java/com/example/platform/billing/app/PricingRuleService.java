package com.example.platform.billing.app;

import com.example.platform.billing.domain.CustomPricingRule;
import com.example.platform.billing.domain.DiscountPolicy;
import com.example.platform.billing.domain.PricingModel;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.infrastructure.CommercialPricingJdbcRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.Money;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Billing-owned durable commercial pricing command/query authority. */
@Service
public class PricingRuleService {

    private final CommercialPricingJdbcRepository repository;

    public PricingRuleService(CommercialPricingJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PricingRule saveRule(PricingRule rule) {
        return repository.saveRule(rule);
    }

    @Transactional
    public CustomPricingRule saveOverride(CustomPricingRule rule) {
        return repository.saveOverride(rule);
    }

    @Transactional
    public PricingRule createPricingRule(String ruleKey, String name, String description,
                                         PricingModel pricingModel, String meterKey,
                                         long unitPriceMinor, String currencyCode,
                                         List<PricingTier> tiers,
                                         Instant effectiveFrom, Instant effectiveTo) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        PricingRule rule = new PricingRule(Ids.newId("prr"), "GLOBAL", ruleKey, 1,
                name, description, pricingModel, meterKey,
                new Money(unitPriceMinor, currencyCode), tiers, "ACTIVE",
                effectiveFrom == null ? now : effectiveFrom, effectiveTo, now, now);
        return repository.saveRule(rule);
    }

    @Transactional(readOnly = true)
    public PricingRule getPricingRule(String ruleKey) {
        return repository.findRule("GLOBAL", ruleKey, 1).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<PricingRule> listPricingRules() {
        return repository.findRulesByTenant("GLOBAL");
    }

    @Transactional
    public PricingRule archivePricingRule(String ruleKey) {
        PricingRule existing = repository.findRule("GLOBAL", ruleKey, 1)
                .orElseThrow(() -> new IllegalArgumentException("Pricing rule not found: " + ruleKey));
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        repository.updateRuleStatus("GLOBAL", ruleKey, existing.version(), "ACTIVE", "ARCHIVED", now);
        return new PricingRule(existing.ruleId(), existing.tenantId(), existing.ruleKey(),
                existing.version(), existing.name(), existing.description(), existing.pricingModel(),
                existing.meterKey(), existing.unitPrice(), existing.tiers(), "ARCHIVED",
                existing.effectiveFrom(), existing.effectiveTo(), existing.createdAt(), now);
    }

    @Transactional
    public CustomPricingRule createCustomPricing(String tenantId, String workspaceId,
                                                  String meterKey, Long overridePriceMinor,
                                                  String currencyCode, long discountNumerator,
                                                  long discountDenominator,
                                                  Instant effectiveFrom, Instant effectiveTo) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        CustomPricingRule rule = new CustomPricingRule(Ids.newId("cpr"), tenantId,
                workspaceId, meterKey, 1,
                overridePriceMinor == null ? null : new Money(overridePriceMinor, currencyCode),
                discountNumerator, discountDenominator,
                effectiveFrom == null ? now : effectiveFrom, effectiveTo, "ACTIVE", now);
        return repository.saveOverride(rule);
    }

    @Transactional(readOnly = true)
    public CustomPricingRule getCustomPricing(String tenantId, String ruleId) {
        return repository.findOverrideById(tenantId, ruleId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CustomPricingRule> getCustomPricingForTenant(String tenantId) {
        return repository.findOverridesByTenant(tenantId);
    }

    @Transactional
    public DiscountPolicy createDiscountPolicy(String tenantId, String policyKey, long version,
                                                String meterKey, String currencyCode,
                                                String name, String description, String discountType,
                                                long discountNumerator,
                                                long discountDenominator, long flatAmountMinor,
                                                Map<String, String> conditions,
                                                Instant effectiveFrom, Instant effectiveTo) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return repository.saveDiscount(new DiscountPolicy(Ids.newId("dsc"), tenantId,
                policyKey, version, meterKey, currencyCode, name, description,
                discountType, discountNumerator, discountDenominator,
                flatAmountMinor, conditions, "ACTIVE", effectiveFrom == null ? now : effectiveFrom,
                effectiveTo, now));
    }

    @Transactional(readOnly = true)
    public DiscountPolicy getDiscountPolicy(String tenantId, String policyKey, long version) {
        return repository.findDiscount(tenantId, policyKey, version).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<DiscountPolicy> listDiscountPolicies() {
        return repository.findDiscountsByTenant("GLOBAL", Instant.now());
    }

    @Transactional(readOnly = true)
    public PricingRule requireEffectiveRule(String tenantId, String ruleKey, long version, Instant at) {
        return repository.findEffectiveRule(tenantId, ruleKey, version, at)
                .orElseThrow(() -> new IllegalStateException(
                        "Unknown or inactive pricing rule/version: " + ruleKey + "/" + version));
    }

    @Transactional(readOnly = true)
    public PricingRule requireEffectiveRuleForMeter(String tenantId, String meterKey, Instant at) {
        List<PricingRule> candidates = repository.findRulesByTenant(tenantId).stream()
                .filter(rule -> rule.meterKey().equals(meterKey) && rule.effectiveAt(at)).toList();
        if (candidates.isEmpty()) {
            candidates = repository.findRulesByTenant("GLOBAL").stream()
                    .filter(rule -> rule.meterKey().equals(meterKey) && rule.effectiveAt(at)).toList();
        }
        if (candidates.size() != 1) {
            throw new IllegalStateException("Pricing meter must resolve to exactly one active rule: " + meterKey);
        }
        return candidates.get(0);
    }

    @Transactional(readOnly = true)
    public PricingPreviewResult previewPricing(PricingQuoteCommand command) {
        PricingRule rule = requireEffectiveRule(command.tenantId(), command.pricingRuleKey(),
                command.pricingRuleVersion(), command.pricedAt());
        if (!rule.meterKey().equals(command.meterKey())) {
            throw new IllegalStateException("Pricing rule does not authorize requested meter");
        }

        Money base = calculateAmount(command.quantityBaseUnits(), rule);
        Money adjusted = base;
        String overrideId = null;
        CustomPricingRule override = repository.findOverride(command.tenantId(), command.workspaceId(),
                command.meterKey(), command.pricedAt()).orElse(null);
        if (override != null) {
            overrideId = override.ruleId();
            if (override.overrideUnitPrice() != null) {
                if (!rule.currencyCode().equals(override.currencyCode())) {
                    throw new IllegalStateException("Pricing override currency does not match base rule");
                }
                adjusted = override.overrideUnitPrice().multiply(command.quantityBaseUnits());
            }
            adjusted = applyFractionDiscount(adjusted, override.discountNumerator(),
                    override.discountDenominator());
        }

        for (DiscountPolicy discount : repository.findEffectiveDiscounts(
                command.tenantId(), command.meterKey(), command.pricedAt())) {
            if (!applicable(discount, command.context())) continue;
            if (!discount.currencyCode().equals(adjusted.currency())) {
                throw new IllegalStateException("Discount currency does not match pricing rule");
            }
            adjusted = switch (discount.discountType()) {
                case "PERCENTAGE" -> applyFractionDiscount(adjusted,
                        discount.discountNumerator(), discount.discountDenominator());
                case "FLAT" -> new Money(Math.max(0,
                        Math.subtractExact(adjusted.amountMinor(), discount.flatAmountMinor())),
                        adjusted.currency());
                default -> throw new IllegalStateException("Unknown discount type: " + discount.discountType());
            };
        }

        Map<String, String> breakdown = new LinkedHashMap<>();
        breakdown.put("baseAmountMinor", Long.toString(base.amountMinor()));
        breakdown.put("currency", base.currency());
        breakdown.put("quantityBaseUnits", Long.toString(command.quantityBaseUnits()));
        breakdown.put("pricingRuleId", rule.ruleId());
        breakdown.put("pricingRuleVersion", Long.toString(rule.version()));
        if (overrideId != null) breakdown.put("overrideRuleId", overrideId);
        return new PricingPreviewResult(command.tenantId(), command.meterKey(),
                command.quantityBaseUnits(), adjusted, rule.ruleId(), rule.version(), overrideId,
                Map.copyOf(breakdown));
    }

    private static Money calculateAmount(long quantity, PricingRule rule) {
        if (rule.tiers().isEmpty()) return rule.unitPrice().multiply(quantity);
        Money total = new Money(0, rule.currencyCode());
        long previousLimit = 0;
        long remaining = quantity;
        for (PricingTier tier : rule.tiers()) {
            if (tier.upToQuantity() <= previousLimit) {
                throw new IllegalStateException("Pricing tier limits must be strictly increasing");
            }
            long capacity = Math.subtractExact(tier.upToQuantity(), previousLimit);
            long tierQuantity = Math.min(remaining, capacity);
            if (tierQuantity > 0) {
                Money variable = new Money(tier.unitPriceMinor(), rule.currencyCode()).multiply(tierQuantity);
                total = total.add(variable).add(new Money(tier.flatFeeMinor(), rule.currencyCode()));
                remaining -= tierQuantity;
            }
            previousLimit = tier.upToQuantity();
            if (remaining == 0) break;
        }
        if (remaining > 0) {
            PricingTier last = rule.tiers().get(rule.tiers().size() - 1);
            total = total.add(new Money(last.unitPriceMinor(), rule.currencyCode()).multiply(remaining));
        }
        return total;
    }

    private static Money applyFractionDiscount(Money amount, long numerator, long denominator) {
        if (numerator == 0) return amount;
        BigInteger retained = BigInteger.valueOf(amount.amountMinor())
                .multiply(BigInteger.valueOf(Math.subtractExact(denominator, numerator)));
        BigInteger[] division = retained.divideAndRemainder(BigInteger.valueOf(denominator));
        BigInteger rounded = division[0];
        if (division[1].abs().multiply(BigInteger.TWO).compareTo(BigInteger.valueOf(denominator)) >= 0) {
            rounded = rounded.add(BigInteger.ONE);
        }
        return new Money(rounded.longValueExact(), amount.currency());
    }

    private static boolean applicable(DiscountPolicy policy, Map<String, String> context) {
        return policy.conditions().entrySet().stream()
                .allMatch(entry -> entry.getValue().equals(context.get(entry.getKey())));
    }

    public record PricingPreviewResult(
            String tenantId, String meterKey, long quantityBaseUnits, Money amount,
            String pricingRuleId, long pricingRuleVersion, String overrideRuleId,
            Map<String, String> breakdown) {
        public long estimatedAmountMinor() { return amount.amountMinor(); }
        public String currencyCode() { return amount.currency(); }
        public long quantity() { return quantityBaseUnits; }
    }
}
