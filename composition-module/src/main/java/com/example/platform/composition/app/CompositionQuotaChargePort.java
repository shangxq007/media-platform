package com.example.platform.composition.app;

import com.example.platform.execution.planning.PlatformExecutionPlan;

/** Canonical quota boundary; implementations must make the operation idempotent. */
public interface CompositionQuotaChargePort {
    void charge(PlatformExecutionPlan plan, String executionId);
}
