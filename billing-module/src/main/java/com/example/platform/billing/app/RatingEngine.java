package com.example.platform.billing.app;

import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.RateUsageCommand;
import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.billing.infrastructure.CommercialPricingJdbcRepository;
import com.example.platform.billing.infrastructure.RatedUsageJdbcRepository;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.shared.commercial.Money;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable, idempotent rating authority. */
@Service
public class RatingEngine {

    private final CommercialPricingJdbcRepository pricing;
    private final RatedUsageJdbcRepository ratedUsage;
    private final RatedUsageAuditPort audit;

    public RatingEngine(CommercialPricingJdbcRepository pricing,
                        RatedUsageJdbcRepository ratedUsage,
                        RatedUsageAuditPort audit) {
        this.pricing = pricing;
        this.ratedUsage = ratedUsage;
        this.audit = audit;
    }

    @Transactional
    public RatedUsageRecord rate(RateUsageCommand command) {
        BillableUsage usage = command.billableUsage();
        PricingRule rule = pricing.findEffectiveRule(usage.tenantId(), command.pricingRuleKey(),
                        command.pricingRuleVersion(), command.ratedAt())
                .orElseThrow(() -> new IllegalStateException("Unknown or inactive pricing rule/version"));
        if (!rule.meterKey().equals(usage.billableMeter())) {
            throw new IllegalStateException("Pricing rule meter does not match BillableUsage meter");
        }
        long quantity = usage.billableQuantity().baseUnits();
        Money amount = calculate(quantity, rule);
        String fingerprint = RatedUsageRecord.fingerprint(usage.tenantId(), usage.billableUsageId(),
                rule.ruleId(), rule.version(), quantity, amount, command.ratedAt(), command.traceId());
        String ratedId = "rat_" + fingerprint.substring(0, 24);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("meterKey", usage.billableMeter());
        details.put("quantityBaseUnits", Long.toString(quantity));
        details.put("quantityUnit", usage.billableQuantity().unit().name());
        details.put("pricingModel", rule.pricingModel().name());
        details.put("unitPriceMinor", Long.toString(rule.unitPriceMinor()));
        details.put("billableUsageId", usage.billableUsageId());
        details.put("meteringRuleId", usage.meteringRuleId());
        details.put("meteringRuleVersion", usage.meteringRuleVersion());
        RatedUsageRecord candidate = new RatedUsageRecord(ratedId, usage.tenantId(),
                usage.billableUsageId(), rule.ruleId(), rule.version(), quantity, amount,
                Map.copyOf(details), command.ratedAt(), command.traceId(),
                command.idempotencyKey(), fingerprint);
        RatedUsageJdbcRepository.AppendResult result = ratedUsage.append(candidate);
        if (result.inserted()) audit.record(result.record());
        return result.record();
    }

    @Transactional(readOnly = true)
    public RatedUsageRecord getRatedRecord(String tenantId, String ratedUsageId) {
        return ratedUsage.findByTenantAndId(tenantId, ratedUsageId).orElse(null);
    }

    private static Money calculate(long quantity, PricingRule rule) {
        if (rule.tiers().isEmpty()) return rule.unitPrice().multiply(quantity);
        Money total = new Money(0, rule.currencyCode());
        long prior = 0;
        long remaining = quantity;
        for (var tier : rule.tiers()) {
            if (tier.upToQuantity() <= prior) throw new IllegalStateException("invalid pricing tiers");
            long used = Math.min(remaining, Math.subtractExact(tier.upToQuantity(), prior));
            if (used > 0) {
                total = total.add(new Money(tier.unitPriceMinor(), rule.currencyCode()).multiply(used))
                        .add(new Money(tier.flatFeeMinor(), rule.currencyCode()));
                remaining -= used;
            }
            prior = tier.upToQuantity();
            if (remaining == 0) break;
        }
        if (remaining > 0) {
            var last = rule.tiers().get(rule.tiers().size() - 1);
            total = total.add(new Money(last.unitPriceMinor(), rule.currencyCode()).multiply(remaining));
        }
        return total;
    }
}
