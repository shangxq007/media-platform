package com.example.platform.billing.app;

import com.example.platform.billing.domain.BillingDecision;
import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.billing.domain.BillingState;
import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.billing.domain.PricingRule;
import com.example.platform.billing.usage.BillableUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Closes a billing period: aggregates metered usage, applies included subscription quota, rates overage, posts ledger charges.
 */
@Service
public class BillingCycleService {

    private static final Logger log = LoggerFactory.getLogger(BillingCycleService.class);

    private final UsageMeteringService usageMeteringService;
    private final SubscriptionBillingService subscriptionBillingService;
    private final PricingRuleService pricingRuleService;
    private final BillingLedgerService billingLedgerService;
    private final CreditWalletService creditWalletService;

    public BillingCycleService(UsageMeteringService usageMeteringService,
                               SubscriptionBillingService subscriptionBillingService,
                               PricingRuleService pricingRuleService,
                               BillingLedgerService billingLedgerService,
                               CreditWalletService creditWalletService) {
        this.usageMeteringService = usageMeteringService;
        this.subscriptionBillingService = subscriptionBillingService;
        this.pricingRuleService = pricingRuleService;
        this.billingLedgerService = billingLedgerService;
        this.creditWalletService = creditWalletService;
    }

    public BillingCycleResult runCycle(String tenantId, String userId) {
        Map<String, Long> included = subscriptionBillingService.getEffectiveIncludedQuota(tenantId, userId);
        List<BillableUsage> usage = usageMeteringService.getBillableUsageByTenant(tenantId);

        Map<String, Long> usageByMeter = new HashMap<>();
        for (BillableUsage record : usage) {
            usageByMeter.merge(record.billableMeter(), record.billableQuantity().baseUnits(), Long::sum);
        }

        long totalChargeMinor = 0L;
        List<BillingCycleLine> lines = new ArrayList<>();

        for (Map.Entry<String, Long> entry : usageByMeter.entrySet()) {
            String meterKey = entry.getKey();
            long totalUsed = entry.getValue();
            long includedAmount = included.getOrDefault(meterKey, 0L);
            long overage = Math.max(0, totalUsed - includedAmount);
            if (overage <= 0) {
                lines.add(new BillingCycleLine(meterKey, totalUsed, includedAmount, 0, 0L, "INCLUDED"));
                continue;
            }

            Instant pricedAt = Instant.now();
            PricingRule rule = pricingRuleService.requireEffectiveRuleForMeter(
                    tenantId, meterKey, pricedAt);
            PricingRuleService.PricingPreviewResult preview = pricingRuleService.previewPricing(
                    new PricingQuoteCommand(tenantId, null, meterKey, overage,
                            rule.ruleKey(), rule.version(), pricedAt, Map.of()));
            long chargeMinor = preview.estimatedAmountMinor();
            totalChargeMinor += chargeMinor;
            lines.add(new BillingCycleLine(meterKey, totalUsed, includedAmount, overage, chargeMinor, "OVERAGE"));

            billingLedgerService.writeEntry(
                    tenantId, null, userId,
                    BillingLedgerEntry.TYPE_CHARGE,
                    chargeMinor,
                    preview.currencyCode(),
                    "USAGE_CYCLE",
                    meterKey,
                    "Overage " + meterKey + ": " + overage + " units");
        }

        String walletNote = null;
        if (totalChargeMinor > 0) {
            CreditWallet wallet = creditWalletService.getWalletByTenant(tenantId, userId);
            if (wallet != null && wallet.balanceMinor() >= totalChargeMinor) {
                creditWalletService.debit(tenantId, wallet.walletId(), totalChargeMinor,
                        "USAGE_CYCLE", "cycle-" + Instant.now().toEpochMilli(),
                        "Billing cycle usage charge");
                walletNote = "debited_from_wallet";
            } else {
                walletNote = "ledger_only";
            }
        }

        log.info("BillingCycleService: tenant={} user={} lines={} totalChargeMinor={}",
                tenantId, userId, lines.size(), totalChargeMinor);
        return new BillingCycleResult(tenantId, userId, Instant.now(), lines, totalChargeMinor, walletNote);
    }

    public record BillingCycleLine(
            String meterKey,
            long totalUsed,
            long includedQuota,
            long overageQuantity,
            long chargeMinor,
            String disposition) {
    }

    public record BillingCycleResult(
            String tenantId,
            String userId,
            Instant closedAt,
            List<BillingCycleLine> lines,
            long totalChargeMinor,
            String settlement) {
    }
}
