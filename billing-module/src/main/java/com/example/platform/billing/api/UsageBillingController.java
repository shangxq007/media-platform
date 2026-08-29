package com.example.platform.billing.api;

import com.example.platform.billing.api.dto.*;
import com.example.platform.billing.app.*;
import com.example.platform.billing.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class UsageBillingController {

    private final UsageMeteringService usageMeteringService;
    private final BillingLedgerService billingLedgerService;
    private final BillingDecisionService billingDecisionService;
    private final PricingRuleService pricingRuleService;

    public UsageBillingController(UsageMeteringService usageMeteringService,
                                   BillingLedgerService billingLedgerService,
                                   BillingDecisionService billingDecisionService,
                                   PricingRuleService pricingRuleService) {
        this.usageMeteringService = usageMeteringService;
        this.billingLedgerService = billingLedgerService;
        this.billingDecisionService = billingDecisionService;
        this.pricingRuleService = pricingRuleService;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@RequestBody QuoteRequest request) {
        String tenantId = resolveTenantId(request.tenantId());
        PricingRuleService.PricingPreviewResult preview = pricingRuleService.previewPricing(
                tenantId, request.meterKey(), request.quantity(), Map.of());
        PricingRule rule = findRuleForMeter(request.meterKey());
        String pricingModel = rule != null ? rule.pricingModel().name() : "USAGE_BASED";
        return new QuoteResponse(
                tenantId, request.meterKey(), request.quantity(),
                request.unit(), preview.estimatedAmountMinor(), preview.currencyCode(), pricingModel);
    }

    @GetMapping("/usage")
    public List<UsageRecordResponse> listUsage(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String meterKey) {
        String effectiveTenant = resolveTenantId(tenantId);
        return usageMeteringService.getUsage(effectiveTenant, meterKey).stream()
                .map(r -> new UsageRecordResponse(
                        r.recordId(), r.tenantId(), r.dimension().name(),
                        r.quantity().baseUnits(), r.quantity().unit().name(),
                        r.recordedAt()))
                .toList();
    }

    @GetMapping("/ledger")
    public List<BillingLedgerResponse> getLedger(@RequestParam(required = false) String tenantId) {
        String effectiveTenant = resolveTenantId(tenantId);
        return billingLedgerService.getLedger(effectiveTenant).stream()
                .map(e -> new BillingLedgerResponse(
                        e.entryId(), e.tenantId(), e.workspaceId(),
                        e.userId(), e.entryType(), e.amountMinor(),
                        e.currencyCode(), e.referenceType(), e.referenceId(),
                        e.description(), e.createdAt()))
                .toList();
    }

    private String resolveTenantId(String requestedTenantId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant == null || contextTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.equals(contextTenant)) {
            throw new SecurityException("Tenant ID does not match authenticated tenant");
        }
        return contextTenant;
    }

    private PricingRule findRuleForMeter(String meterKey) {
        return pricingRuleService.listPricingRules().stream()
                .filter(r -> meterKey.equals(r.meterKey()))
                .filter(r -> "ACTIVE".equals(r.status()))
                .findFirst()
                .orElse(null);
    }
}
