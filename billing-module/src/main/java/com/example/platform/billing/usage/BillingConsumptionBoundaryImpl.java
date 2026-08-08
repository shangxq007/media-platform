package com.example.platform.billing.usage;

import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.RatingEngine;
import com.example.platform.billing.domain.PricingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Billing consumption boundary implementation.
 *
 * <p>Projects a canonical {@code billing.usage.UsageRecord} onto the legacy
 * {@code billing.domain.UsageRecord} via {@link BillingUsageCompatibilityAdapter} and
 * feeds it into the existing {@link RatingEngine} consumption path. This is a consumer
 * of canonical usage facts only — it never invents execution usage. If no active pricing
 * rule matches the meter key, the fact is projected but not rated (rating is a separate
 * commercial concern, not a usage-fact authority).</p>
 */
@Service
public class BillingConsumptionBoundaryImpl implements BillingConsumptionBoundary {

    private static final Logger log = LoggerFactory.getLogger(BillingConsumptionBoundaryImpl.class);

    private final BillingUsageCompatibilityAdapter adapter;
    private final RatingEngine ratingEngine;
    private final PricingRuleService pricingRuleService;

    public BillingConsumptionBoundaryImpl(BillingUsageCompatibilityAdapter adapter,
                                          RatingEngine ratingEngine,
                                          PricingRuleService pricingRuleService) {
        this.adapter = adapter;
        this.ratingEngine = ratingEngine;
        this.pricingRuleService = pricingRuleService;
    }

    @Override
    public void consume(UsageRecord canonical) {
        Objects.requireNonNull(canonical, "canonical usage record must not be null");
        // Project the canonical fact onto the legacy consumer shape. The adapter is the
        // ONLY place where the canonical typed quantity becomes a legacy double.
        // Note: UsageRecord (parameter) = canonical billing.usage.UsageRecord; the local
        // 'legacy' is the legacy billing.domain.UsageRecord shape.
        com.example.platform.billing.domain.UsageRecord legacy = adapter.adapt(canonical);

        // Hand the adapted fact to the existing RatingEngine-compatible flow. Rating is
        // purely commercial; absence of a matching rule does not invent or discard usage.
        PricingRule rule = pricingRuleService.listPricingRules().stream()
                .filter(r -> legacy.meterKey().equals(r.meterKey()))
                .filter(r -> "ACTIVE".equals(r.status()))
                .findFirst()
                .orElse(null);

        if (rule != null) {
            ratingEngine.rateUsage(legacy, rule);
            log.debug("BillingConsumptionBoundary: consumed canonical record {} meterKey={}",
                    legacy.recordId(), legacy.meterKey());
        } else {
            log.debug("BillingConsumptionBoundary: projected canonical record {} meterKey={} (no active rule)",
                    legacy.recordId(), legacy.meterKey());
        }
    }
}
