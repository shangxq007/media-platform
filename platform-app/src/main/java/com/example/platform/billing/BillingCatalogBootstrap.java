package com.example.platform.billing;

import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.billing.domain.PricingModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class BillingCatalogBootstrap {

    private final PricingRuleService pricingRuleService;
    private final UsageMeteringService usageMeteringService;

    public BillingCatalogBootstrap(PricingRuleService pricingRuleService,
                                   UsageMeteringService usageMeteringService) {
        this.pricingRuleService = pricingRuleService;
        this.usageMeteringService = usageMeteringService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seedMeter("render.minutes", "Render Minutes", "minute", "SUM");
        seedMeter("gpu.minutes", "GPU Minutes", "minute", "SUM");
        seedMeter("api.calls", "API Calls", "call", "SUM");
        seedMeter("prompt.executions", "Prompt Executions", "execution", "SUM");

        seedRuleIfAbsent("render_minutes_overage", "Render overage", "render.minutes",
                10L, PricingModel.USAGE_BASED);
        seedRuleIfAbsent("gpu_minutes_overage", "GPU overage", "gpu.minutes",
                25L, PricingModel.USAGE_BASED);
        seedRuleIfAbsent("api_calls_overage", "API overage", "api.calls",
                1L, PricingModel.USAGE_BASED);
        seedRuleIfAbsent("prompt_executions_overage", "AI prompt overage", "prompt.executions",
                2L, PricingModel.USAGE_BASED);
    }

    private void seedMeter(String key, String name, String unit, String aggregation) {
        if (usageMeteringService.getMeter(key) == null) {
            usageMeteringService.registerMeter(key, name, name, unit, aggregation);
        }
    }

    private void seedRuleIfAbsent(String ruleKey, String name, String meterKey,
                                  long unitPriceMinor, PricingModel model) {
        if (pricingRuleService.getPricingRule(ruleKey) == null) {
            pricingRuleService.createPricingRule(
                    ruleKey, name, "Seeded overage rule", model, meterKey,
                    unitPriceMinor, "USD", List.of(), Instant.now(), null);
        }
    }
}
