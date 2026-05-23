package com.example.platform.billing.api;

import com.example.platform.billing.api.dto.*;
import com.example.platform.billing.app.*;
import com.example.platform.billing.domain.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class UsageBillingController {

    private final UsageMeteringService usageMeteringService;
    private final RatingEngine ratingEngine;
    private final BillingLedgerService billingLedgerService;
    private final BillingDecisionService billingDecisionService;
    private final PricingRuleService pricingRuleService;

    public UsageBillingController(UsageMeteringService usageMeteringService,
                                   RatingEngine ratingEngine,
                                   BillingLedgerService billingLedgerService,
                                   BillingDecisionService billingDecisionService,
                                   PricingRuleService pricingRuleService) {
        this.usageMeteringService = usageMeteringService;
        this.ratingEngine = ratingEngine;
        this.billingLedgerService = billingLedgerService;
        this.billingDecisionService = billingDecisionService;
        this.pricingRuleService = pricingRuleService;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@RequestBody QuoteRequest request) {
        PricingRuleService.PricingPreviewResult preview = pricingRuleService.previewPricing(
                request.tenantId(), request.meterKey(), request.quantity(), Map.of());
        PricingRule rule = findRuleForMeter(request.meterKey());
        String pricingModel = rule != null ? rule.pricingModel().name() : "USAGE_BASED";
        return new QuoteResponse(
                request.tenantId(), request.meterKey(), request.quantity(),
                request.unit(), preview.estimatedAmountMinor(), preview.currencyCode(), pricingModel);
    }

    @PostMapping("/usage/record")
    public UsageRecordResponse recordUsage(@RequestBody RecordUsageRequest request) {
        Instant recordedAt = request.recordedAt() != null ? request.recordedAt() : Instant.now();
        UsageRecord record = usageMeteringService.recordUsage(
                request.tenantId(), request.workspaceId(), request.userId(),
                request.meterKey(), request.quantity(), request.unit(),
                recordedAt, request.idempotencyKey());

        PricingRule rule = findRuleForMeter(request.meterKey());
        if (rule != null) {
            ratingEngine.rateUsage(record, rule);
        }

        return new UsageRecordResponse(
                record.recordId(), record.tenantId(), record.workspaceId(),
                record.userId(), record.meterKey(), record.quantity(),
                record.unit(), record.recordedAt(), record.idempotencyKey());
    }

    @GetMapping("/usage")
    public List<UsageRecordResponse> listUsage(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String meterKey) {
        return usageMeteringService.getUsage(tenantId, meterKey).stream()
                .map(r -> new UsageRecordResponse(
                        r.recordId(), r.tenantId(), r.workspaceId(),
                        r.userId(), r.meterKey(), r.quantity(),
                        r.unit(), r.recordedAt(), r.idempotencyKey()))
                .toList();
    }

    @GetMapping("/ledger")
    public List<BillingLedgerResponse> getLedger(@RequestParam String tenantId) {
        return billingLedgerService.getLedger(tenantId).stream()
                .map(e -> new BillingLedgerResponse(
                        e.entryId(), e.tenantId(), e.workspaceId(),
                        e.userId(), e.entryType(), e.amountMinor(),
                        e.currencyCode(), e.referenceType(), e.referenceId(),
                        e.description(), e.createdAt()))
                .toList();
    }

    private PricingRule findRuleForMeter(String meterKey) {
        return pricingRuleService.listPricingRules().stream()
                .filter(r -> meterKey.equals(r.meterKey()))
                .filter(r -> "ACTIVE".equals(r.status()))
                .findFirst()
                .orElse(null);
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
}
