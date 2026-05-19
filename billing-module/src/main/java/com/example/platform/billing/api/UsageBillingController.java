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

    public UsageBillingController(UsageMeteringService usageMeteringService,
                                   RatingEngine ratingEngine,
                                   BillingLedgerService billingLedgerService,
                                   BillingDecisionService billingDecisionService) {
        this.usageMeteringService = usageMeteringService;
        this.ratingEngine = ratingEngine;
        this.billingLedgerService = billingLedgerService;
        this.billingDecisionService = billingDecisionService;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@RequestBody QuoteRequest request) {
        PricingRule rule = findRuleForMeter(request.meterKey());
        long estimatedAmountMinor;
        String currencyCode = "USD";
        String pricingModel = "USAGE_BASED";

        if (rule != null) {
            currencyCode = rule.currencyCode();
            pricingModel = rule.pricingModel().name();
            if (rule.tiers() != null && !rule.tiers().isEmpty()) {
                estimatedAmountMinor = calculateTieredAmount(request.quantity(), rule);
            } else {
                estimatedAmountMinor = Math.round(request.quantity() * rule.unitPriceMinor());
            }
        } else {
            estimatedAmountMinor = Math.round(request.quantity() * 100);
        }

        return new QuoteResponse(
                request.tenantId(), request.meterKey(), request.quantity(),
                request.unit(), estimatedAmountMinor, currencyCode, pricingModel);
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
        return null;
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
