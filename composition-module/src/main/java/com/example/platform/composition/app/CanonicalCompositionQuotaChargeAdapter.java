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
        final long amount;
        try { amount = plan.entitlementQuota().quotaUnits().toBigIntegerExact().longValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("fractional quota is unsupported by the canonical quota unit", ex); }
        var now = Instant.now();
        var decision = quota.consume(new QuotaConsumptionRequest(
                new PrincipalRef(plan.scope().tenantId(), PrincipalType.USER, plan.scope().actorId(), plan.scope().workspaceId(), null),
                plan.capabilityBindings().getFirst().capabilityId(), amount,
                now.minusSeconds(1), now.plusSeconds(1), "platform-admission:" + executionId,
                "composition-admission:" + executionId, "composition admission", now));
        if (!decision.allowed()) throw new IllegalStateException("quota admission rejected");
    }
}
