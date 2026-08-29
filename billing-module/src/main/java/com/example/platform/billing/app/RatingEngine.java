package com.example.platform.billing.app;

import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RatingEngine {

    private static final Logger log = LoggerFactory.getLogger(RatingEngine.class);

    private final ConcurrentHashMap<String, RatedUsageRecord> ratedRecords = new ConcurrentHashMap<>();

    public RatedUsageRecord rateUsage(BillableUsage billableUsage, PricingRule pricingRule) {
        if (billableUsage == null) {
            throw new IllegalArgumentException("billableUsage is required");
        }
        if (pricingRule == null) {
            throw new IllegalArgumentException("pricingRule is required");
        }

        long ratedAmountMinor;

        long quantity = billableUsage.billableQuantity().baseUnits();
        if (pricingRule.tiers() != null && !pricingRule.tiers().isEmpty()) {
            ratedAmountMinor = calculateTieredAmount(quantity, pricingRule);
        } else {
            ratedAmountMinor = Math.multiplyExact(quantity, pricingRule.unitPriceMinor());
        }

        String ratedUsageId = Ids.newId("rat");
        Map<String, Object> details = new HashMap<>();
        details.put("meterKey", billableUsage.billableMeter());
        details.put("quantity", quantity);
        details.put("unit", billableUsage.billableQuantity().unit().name());
        details.put("pricingModel", pricingRule.pricingModel().name());
        details.put("unitPriceMinor", pricingRule.unitPriceMinor());

        RatedUsageRecord rated = new RatedUsageRecord(
                ratedUsageId,
                billableUsage.billableUsageId(),
                pricingRule.ruleId(),
                ratedAmountMinor,
                pricingRule.currencyCode(),
                details,
                Instant.now()
        );

        ratedRecords.put(ratedUsageId, rated);
        log.info("RatingEngine: rated usage {} amount={} {} rule={}",
                ratedUsageId, ratedAmountMinor, pricingRule.currencyCode(), pricingRule.ruleKey());
        return rated;
    }

    private long calculateTieredAmount(long quantity, PricingRule rule) {
        long totalMinor = 0;
        long remaining = quantity;

        for (PricingTier tier : rule.tiers()) {
            if (remaining <= 0) break;
            long tierQuantity = Math.min(remaining, tier.upToQuantity());
            totalMinor = Math.addExact(totalMinor,
                    Math.addExact(Math.multiplyExact(tierQuantity, tier.unitPriceMinor()),
                            tier.flatFeeMinor()));
            remaining -= tierQuantity;
        }

        if (remaining > 0) {
            PricingTier lastTier = rule.tiers().get(rule.tiers().size() - 1);
            totalMinor = Math.addExact(
                    totalMinor, Math.multiplyExact(remaining, lastTier.unitPriceMinor()));
        }

        return totalMinor;
    }

    public RatedUsageRecord getRatedRecord(String ratedUsageId) {
        return ratedRecords.get(ratedUsageId);
    }
}
