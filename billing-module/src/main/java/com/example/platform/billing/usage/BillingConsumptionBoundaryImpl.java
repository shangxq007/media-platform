package com.example.platform.billing.usage;

import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.RatingEngine;
import com.example.platform.billing.domain.PricingRule;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Canonical billing consumption boundary. Consumes {@link BillableUsage} facts and hands
 * them to the RatingEngine-compatible flow. Rating is purely commercial; absence of a matching
 * rule does not invent or discard usage. There is no legacy double projection — the typed
 * quantity is the only representation.
 */
@Service
public class BillingConsumptionBoundaryImpl implements BillingConsumptionBoundary {

    private static final Logger log = LoggerFactory.getLogger(BillingConsumptionBoundaryImpl.class);

    private final RatingEngine ratingEngine;
    private final PricingRuleService pricingRuleService;

    public BillingConsumptionBoundaryImpl(RatingEngine ratingEngine,
                                          PricingRuleService pricingRuleService) {
        this.ratingEngine = ratingEngine;
        this.pricingRuleService = pricingRuleService;
    }

    @Override
    public void consume(BillableUsage canonical) {
        Objects.requireNonNull(canonical, "billable usage must not be null");
        PricingRule rule = pricingRuleService.listPricingRules().stream()
                .filter(r -> canonical.billableMeter().equals(r.meterKey()))
                .filter(r -> "ACTIVE".equals(r.status()))
                .findFirst()
                .orElse(null);

        if (rule != null) {
            ratingEngine.rateUsage(canonical, rule);
            log.debug("BillingConsumptionBoundary: consumed canonical record {} dimension={}",
                    canonical.billableUsageId(), canonical.billableDimension().name());
        } else {
            log.debug("BillingConsumptionBoundary: consumed canonical record {} dimension={} (no active rule)",
                    canonical.billableUsageId(), canonical.billableDimension().name());
        }
    }
}
