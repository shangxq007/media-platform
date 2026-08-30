package com.example.platform.billing.api;

import com.example.platform.billing.api.dto.*;
import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.PricingQuoteCommand;
import com.example.platform.billing.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import com.example.platform.shared.commercial.Money;

@RestController
@RequestMapping("/api/admin/billing")
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

        PricingModel model = PricingModel.valueOf(request.pricingModel());
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        PricingRule rule = pricingRuleService.saveRule(new PricingRule(
                request.ruleId(), request.tenantId(), request.ruleKey(), request.ruleVersion(),
                request.name(), request.description(), model, request.meterKey(),
                new Money(request.unitPriceMinor(), request.currencyCode()), tiers, "ACTIVE",
                request.effectiveFrom(), request.effectiveTo(), now, now));
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
                new PricingQuoteCommand(request.tenantId(), request.workspaceId(),
                        request.meterKey(), request.quantityBaseUnits(), request.pricingRuleKey(),
                        request.pricingRuleVersion(), request.pricedAt(), request.context()));
        return new PricingPreviewResponse(
                result.tenantId(), result.meterKey(), result.quantityBaseUnits(),
                result.estimatedAmountMinor(), result.currencyCode(),
                result.pricingRuleId(), result.pricingRuleVersion(), result.overrideRuleId(),
                result.breakdown());
    }

    @PostMapping("/custom-pricing")
    public CustomPricingResponse createCustomPricing(@RequestBody CreateCustomPricingRequest request) {
        CustomPricingRule rule = pricingRuleService.createCustomPricing(
                request.tenantId(), request.workspaceId(), request.meterKey(),
                request.overridePriceMinor(), request.currencyCode(),
                request.discountNumerator(), request.discountDenominator(),
                request.effectiveFrom(), request.effectiveTo());
        return new CustomPricingResponse(
                rule.ruleId(), rule.tenantId(), rule.workspaceId(),
                rule.meterKey(), rule.version(), rule.overridePriceMinor(), rule.currencyCode(),
                rule.discountNumerator(), rule.discountDenominator(),
                rule.effectiveFrom(), rule.effectiveTo(), rule.status(),
                rule.createdAt());
    }

    @PostMapping("/discount-policies")
    public DiscountPolicyResponse createDiscountPolicy(@RequestBody CreateDiscountPolicyRequest request) {
        DiscountPolicy policy = pricingRuleService.createDiscountPolicy(
                request.tenantId(), request.policyKey(), request.ruleVersion(),
                request.meterKey(), request.currencyCode(), request.name(), request.description(),
                request.discountType(), request.discountNumerator(), request.discountDenominator(),
                request.flatAmountMinor(),
                request.conditions(), request.effectiveFrom(), request.effectiveTo());
        return new DiscountPolicyResponse(
                policy.policyId(), policy.tenantId(), policy.policyKey(), policy.version(),
                policy.meterKey(), policy.currencyCode(), policy.name(),
                policy.description(), policy.discountType(), policy.discountNumerator(),
                policy.discountDenominator(), policy.flatAmountMinor(),
                policy.conditions(), policy.status(), policy.effectiveFrom(),
                policy.effectiveTo(), policy.createdAt());
    }

    @GetMapping("/discount-policies")
    public List<DiscountPolicyResponse> listDiscountPolicies() {
        return pricingRuleService.listDiscountPolicies().stream()
                .map(p -> new DiscountPolicyResponse(
                        p.policyId(), p.tenantId(), p.policyKey(), p.version(),
                        p.meterKey(), p.currencyCode(), p.name(),
                        p.description(), p.discountType(), p.discountNumerator(),
                        p.discountDenominator(), p.flatAmountMinor(),
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
                rule.ruleId(), rule.tenantId(), rule.ruleKey(), rule.version(),
                rule.name(), rule.description(),
                rule.pricingModel().name(), rule.meterKey(), rule.unitPriceMinor(),
                rule.currencyCode(), tierDtos, rule.status(),
                rule.effectiveFrom(), rule.effectiveTo(), rule.createdAt());
    }
}
