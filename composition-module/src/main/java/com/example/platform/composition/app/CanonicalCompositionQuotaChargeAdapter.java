package com.example.platform.composition.app;

import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.execution.planning.ProviderBoundExecutionPlan;
import com.example.platform.shared.commercial.*;
import java.time.*;
import org.springframework.stereotype.Service;

/** Routes admission charging through the canonical idempotent quota authority. */
@Service
public class CanonicalCompositionQuotaChargeAdapter implements CompositionQuotaChargePort {
    private final QuotaConsumptionPort quota;
    public CanonicalCompositionQuotaChargeAdapter(QuotaConsumptionPort quota) { this.quota = quota; }
    @Override public void charge(ProviderBoundExecutionPlan plan, String executionId) {
        final var amount = plan.entitlementQuota().quotaUnits();
        var now = Instant.now();
        var decision = quota.consume(new QuotaConsumptionRequest(
                new PrincipalRef(plan.scope().tenantId(), PrincipalType.USER, plan.scope().actorId(), plan.scope().workspaceId(), null),
                quotaKey(plan), amount,
                plan.entitlementQuota().quotaPeriodStart(), plan.entitlementQuota().quotaPeriodEnd(), "platform-admission:" + executionId,
                "composition-admission:" + executionId, "composition admission", now));
        if (!decision.allowed()) throw new IllegalStateException("quota admission rejected");
    }

    private static String quotaKey(ProviderBoundExecutionPlan plan) {
        String key = plan.entitlementQuota().quotaKey();
        if (key == null || key.isBlank()) throw new IllegalArgumentException("quota dimension is unavailable");
        return key;
    }
}