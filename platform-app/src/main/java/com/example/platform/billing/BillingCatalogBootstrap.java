package com.example.platform.billing;

import com.example.platform.billing.app.UsageMeteringService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BillingCatalogBootstrap {

    private final UsageMeteringService usageMeteringService;

    public BillingCatalogBootstrap(UsageMeteringService usageMeteringService) {
        this.usageMeteringService = usageMeteringService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        seedMeter("render.minutes", "Render Minutes", "minute", "SUM");
        seedMeter("gpu.minutes", "GPU Minutes", "minute", "SUM");
        seedMeter("api.calls", "API Calls", "call", "SUM");
        seedMeter("prompt.executions", "Prompt Executions", "execution", "SUM");
    }

    private void seedMeter(String key, String name, String unit, String aggregation) {
        if (usageMeteringService.getMeter(key) == null) {
            usageMeteringService.registerMeter(key, name, name, unit, aggregation);
        }
    }
}
