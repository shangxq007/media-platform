package com.example.platform.commerce;

import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.SubscriptionPlan;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Seeds default subscription plans aligned with {@link com.example.platform.commerce.app.CommerceCatalogService}.
 */
@Component
public class CommerceBillingCatalogBootstrap {

    private final SubscriptionBillingService subscriptionBillingService;

    public CommerceBillingCatalogBootstrap(SubscriptionBillingService subscriptionBillingService) {
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedCatalogPlans() {
        seedIfAbsent("basic_monthly", "Basic Monthly", 2999L,
                Map.of("render.minutes", 120L, "api.calls", 5000L));
        seedIfAbsent("pro_monthly", "Pro Monthly", 9999L,
                Map.of("render.minutes", 600L, "api.calls", 50000L));
        seedIfAbsent("team_monthly", "Team Monthly", 29999L,
                Map.of("render.minutes", 2400L, "api.calls", 200000L));
        seedIfAbsent("enterprise_monthly", "Enterprise Monthly", 99999L,
                Map.of("render.minutes", 12000L, "api.calls", 1000000L));
        seedIfAbsent("addon_gpu_monthly", "GPU Add-on", 4999L,
                Map.of("gpu.minutes", 300L));
        seedIfAbsent("addon_ai_monthly", "AI Add-on", 2999L,
                Map.of("prompt.executions", 5000L));
    }

    private void seedIfAbsent(String planKey, String name, long priceMinor, Map<String, Long> quota) {
        SubscriptionPlan existing = subscriptionBillingService.getPlan(planKey);
        if (existing == null) {
            subscriptionBillingService.createPlan(
                    planKey, name, "Seeded catalog plan", "MONTHLY", priceMinor, "USD", quota);
        }
    }
}
