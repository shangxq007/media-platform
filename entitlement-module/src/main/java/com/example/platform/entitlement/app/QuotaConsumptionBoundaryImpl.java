package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Quota consumption boundary implementation.
 *
 * <p>POST-EXECUTION accounting only. This boundary consumes canonical usage facts;
 * it does not produce observations or persist quota rows directly.</p>
 */
@Service
public class QuotaConsumptionBoundaryImpl implements QuotaConsumptionBoundary {

    private static final Logger log = LoggerFactory.getLogger(QuotaConsumptionBoundaryImpl.class);

    private final QuotaUsageAuthority quotaUsageAuthority;

    public QuotaConsumptionBoundaryImpl(QuotaUsageAuthority quotaUsageAuthority) {
        this.quotaUsageAuthority = quotaUsageAuthority;
    }

    @Override
    public QuotaUsageResult recordPostExecutionUsage(QuotaUsageCommand command) {
        QuotaUsageResult result = quotaUsageAuthority.execute(command);
        log.debug("QuotaConsumptionBoundary: operation={} outcome={}",
                result.operationId(), result.outcome());
        return result;
    }
}
