package com.example.platform.billing.app;

import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.domain.PricingTier;
import com.example.platform.billing.usage.UsageRecord;
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

    public RatedUsageRecord rateUsage(UsageRecord usageRecord, PricingRule pricingRule) {
        if (usageRecord == null) {
            throw new IllegalArgumentException("usageRecord is required");
        }
        if (pricingRule == null) {
            throw new IllegalArgumentException("pricingRule is required");
        }

        long ratedAmountMinor;

        double quantity = (double) usageRecord.quantity().baseUnits();
        if (pricingRule.tiers() != null && !pricingRule.tiers().isEmpty()) {
            ratedAmountMinor = calculateTieredAmount(quantity, pricingRule);
        } else {
            ratedAmountMinor = Math.round(quantity * pricingRule.unitPriceMinor());
        }

        String ratedUsageId = Ids.newId("rat");
        Map<String, Object> details = new HashMap<>();
        details.put("meterKey", usageRecord.dimension().name());
        details.put("quantity", quantity);
        details.put("unit", usageRecord.quantity().unit().name());
        details.put("pricingModel", pricingRule.pricingModel().name());
        details.put("unitPriceMinor", pricingRule.unitPriceMinor());

        RatedUsageRecord rated = new RatedUsageRecord(
                ratedUsageId,
                usageRecord.recordId(),
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

    public RatedUsageRecord getRatedRecord(String ratedUsageId) {
        return ratedRecords.get(ratedUsageId);
    }
}
