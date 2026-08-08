package com.example.platform.entitlement.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Quota consumption boundary implementation.
 *
 * <p>POST-EXECUTION accounting only. Delegates to the existing
 * {@link QuotaDecisionService#recordUsage} hard-limit/usage path without modifying it.
 * Pre-execution decisions ({@code QuotaDecisionService.evaluate}) are untouched. This
 * boundary consumes canonical usage facts; it does NOT write usage_record rows and is
 * not a source of usage facts.</p>
 */
@Service
public class QuotaConsumptionBoundaryImpl implements QuotaConsumptionBoundary {

    private static final Logger log = LoggerFactory.getLogger(QuotaConsumptionBoundaryImpl.class);

    private final QuotaDecisionService quotaDecisionService;

    public QuotaConsumptionBoundaryImpl(QuotaDecisionService quotaDecisionService) {
        this.quotaDecisionService = quotaDecisionService;
    }

    @Override
    public void recordPostExecutionUsage(String tenantId, String featureCode, long amount) {
        quotaDecisionService.recordUsage(tenantId, featureCode, amount);
        log.debug("QuotaConsumptionBoundary: recorded post-execution usage tenant={} feature={} amount={}",
                tenantId, featureCode, amount);
    }
}
