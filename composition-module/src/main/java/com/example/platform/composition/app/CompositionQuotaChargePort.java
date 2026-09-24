package com.example.platform.composition.app;

import com.example.platform.execution.planning.ProviderBoundExecutionPlan;

/** Canonical quota boundary; implementations must make the operation idempotent. */
public interface CompositionQuotaChargePort {
    void charge(ProviderBoundExecutionPlan plan, String executionId);
}
