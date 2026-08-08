package com.example.platform.entitlement.app;

/**
 * Quota consumption boundary (entitlement-module).
 *
 * <p>POST-EXECUTION accounting only. Pre-execution hard-limit decisions remain in
 * {@link QuotaDecisionService#evaluate} (unchanged). This boundary consumes canonical
 * usage facts; it is NOT a source of usage facts and performs no usage_record writes —
 * it only delegates post-execution consumption to the existing quota authority.</p>
 */
public interface QuotaConsumptionBoundary {

    /**
     * Record post-execution usage against the quota authority for the given tenant and
     * feature. This is a consumption call — the usage fact originates elsewhere; this
     * boundary merely accounts it.
     *
     * @param tenantId     the tenant (quota subject)
     * @param featureCode  the feature being accounted
     * @param amount       the consumed amount in the feature's base units
     */
    void recordPostExecutionUsage(String tenantId, String featureCode, long amount);
}
