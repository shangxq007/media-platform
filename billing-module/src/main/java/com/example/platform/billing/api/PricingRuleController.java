package com.example.platform.billing.api;

import com.example.platform.billing.api.dto.*;
import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/billing")
public class PricingRuleController {

    private final PricingRuleService pricingRuleService;

    public PricingRuleController(PricingRuleService pricingRuleService) {
        this.pricingRuleService = pricingRuleService;
    }

    @PostMapping("/pricing-rules")
    public PricingRuleResponse createPricingRule(@RequestBody CreatePricingRuleRequest request) {
        List<PricingTier> tiers = request.tiers() != null
                ? request.tiers().stream()
                        .map(t -> new PricingTier(t.upToQuantity(), t.unitPriceMinor(), t.flatFeeMinor()))
                        .toList()
                : List.of();

        PricingModel model;
        try {
            model = PricingModel.valueOf(request.pricingModel());
        } catch (IllegalArgumentException e) {
            model = PricingModel.USAGE_BASED;
        }

        PricingRule rule = pricingRuleService.createPricingRule(
                request.ruleKey(), request.name(), request.description(),
                model, request.meterKey(), request.unitPriceMinor(),
                request.currencyCode(), tiers,
                request.effectiveFrom(), request.effectiveTo());
        return toPricingRuleResponse(rule);
    }

    @GetMapping("/pricing-rules")
    public List<PricingRuleResponse> listPricingRules() {
        return pricingRuleService.listPricingRules().stream()
                .map(this::toPricingRuleResponse)
                .toList();
    }

    @PostMapping("/pricing-rules/{ruleKey}/archive")
    public PricingRuleResponse archivePricingRule(@PathVariable String ruleKey) {
        PricingRule rule = pricingRuleService.archivePricingRule(ruleKey);
        return toPricingRuleResponse(rule);
    }

    @PostMapping("/pricing-preview")
    public PricingPreviewResponse previewPricing(@RequestBody PricingPreviewRequest request) {
        PricingRuleService.PricingPreviewResult result = pricingRuleService.previewPricing(
                request.tenantId(), request.meterKey(), request.quantity(),
                request.context());
        return new PricingPreviewResponse(
                result.tenantId(), result.meterKey(), result.quantity(),
                result.estimatedAmountMinor(), result.currencyCode(),
                result.breakdown());
    }

    @PostMapping("/custom-pricing")
    public CustomPricingResponse createCustomPricing(@RequestBody CreateCustomPricingRequest request) {
        CustomPricingRule rule = pricingRuleService.createCustomPricing(
                request.tenantId(), request.workspaceId(), request.meterKey(),
                request.overridePriceMinor(), request.discountPercent(),
                request.effectiveFrom(), request.effectiveTo());
        return new CustomPricingResponse(
                rule.ruleId(), rule.tenantId(), rule.workspaceId(),
                rule.meterKey(), rule.overridePriceMinor(), rule.discountPercent(),
                rule.effectiveFrom(), rule.effectiveTo(), rule.status(),
                rule.createdAt());
    }

    @PostMapping("/discount-policies")
    public DiscountPolicyResponse createDiscountPolicy(@RequestBody CreateDiscountPolicyRequest request) {
        DiscountPolicy policy = pricingRuleService.createDiscountPolicy(
                request.policyKey(), request.name(), request.description(),
                request.discountType(), request.discountValue(),
                request.conditions(), request.effectiveFrom(), request.effectiveTo());
        return new DiscountPolicyResponse(
                policy.policyId(), policy.policyKey(), policy.name(),
                policy.description(), policy.discountType(), policy.discountValue(),
                policy.conditions(), policy.status(), policy.effectiveFrom(),
                policy.effectiveTo(), policy.createdAt());
    }

    @GetMapping("/discount-policies")
    public List<DiscountPolicyResponse> listDiscountPolicies() {
        return pricingRuleService.listDiscountPolicies().stream()
                .map(p -> new DiscountPolicyResponse(
                        p.policyId(), p.policyKey(), p.name(),
                        p.description(), p.discountType(), p.discountValue(),
                        p.conditions(), p.status(), p.effectiveFrom(),
                        p.effectiveTo(), p.createdAt()))
                .toList();
    }

    private PricingRuleResponse toPricingRuleResponse(PricingRule rule) {
        List<PricingTierDto> tierDtos = rule.tiers() != null
                ? rule.tiers().stream()
                        .map(t -> new PricingTierDto(t.upToQuantity(), t.unitPriceMinor(), t.flatFeeMinor()))
                        .toList()
                : List.of();
        return new PricingRuleResponse(
                rule.ruleId(), rule.ruleKey(), rule.name(), rule.description(),
                rule.pricingModel().name(), rule.meterKey(), rule.unitPriceMinor(),
                rule.currencyCode(), tierDtos, rule.status(),
                rule.effectiveFrom(), rule.effectiveTo(), rule.createdAt());
    }
}
