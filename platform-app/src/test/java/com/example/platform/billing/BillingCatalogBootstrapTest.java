package com.example.platform.billing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.platform.billing.app.PricingRuleService;
import com.example.platform.billing.app.UsageMeteringService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class BillingCatalogBootstrapTest {

    @Test
    void applicationReadyBootstrapDoesNotWriteCommercialPricing() {
        PricingRuleService pricing = mock(PricingRuleService.class);
        UsageMeteringService metering = mock(UsageMeteringService.class);

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PricingRuleService.class, () -> pricing);
            context.registerBean(UsageMeteringService.class, () -> metering);
            context.register(BillingCatalogBootstrap.class);
            context.refresh();

            context.publishEvent(new ApplicationReadyEvent(
                    new SpringApplication(BillingCatalogBootstrap.class),
                    new String[0], context, Duration.ZERO));
        }

        verifyNoInteractions(pricing);
        verify(metering).getMeter("render.minutes");
        verify(metering).registerMeter(
                "render.minutes", "Render Minutes", "Render Minutes", "minute", "SUM");
        verify(metering).getMeter("gpu.minutes");
        verify(metering).registerMeter(
                "gpu.minutes", "GPU Minutes", "GPU Minutes", "minute", "SUM");
        verify(metering).getMeter("api.calls");
        verify(metering).registerMeter(
                "api.calls", "API Calls", "API Calls", "call", "SUM");
        verify(metering).getMeter("prompt.executions");
        verify(metering).registerMeter(
                "prompt.executions", "Prompt Executions", "Prompt Executions", "execution", "SUM");
        verifyNoMoreInteractions(metering);
    }
}
