package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageResult;

/**
 * Quota consumption boundary (entitlement-module).
 *
 * <p>POST-EXECUTION accounting only. This boundary consumes canonical usage facts;
 * it is NOT a source of usage facts and delegates the complete command to the sole
 * quota authority.</p>
 */
public interface QuotaConsumptionBoundary {

    /**
     * Record post-execution usage against the quota authority for the given tenant and
     * feature. This is a consumption call — the usage fact originates elsewhere; this
     * boundary merely accounts it.
     *
     * @param command explicit tenant/principal/period/idempotency-scoped command
     */
    QuotaUsageResult recordPostExecutionUsage(QuotaUsageCommand command);
}
