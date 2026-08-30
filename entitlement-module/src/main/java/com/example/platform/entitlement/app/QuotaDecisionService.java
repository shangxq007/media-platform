package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaProfile;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.QuotaDecision;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuotaDecisionService {

    private static final Logger log = LoggerFactory.getLogger(QuotaDecisionService.class);

    private final QuotaPolicyService quotaPolicyService;
    private final QuotaUsageAuthority quotaUsageAuthority;

    public QuotaDecisionService(
            QuotaPolicyService quotaPolicyService, QuotaUsageAuthority quotaUsageAuthority) {
        this.quotaPolicyService = quotaPolicyService;
        this.quotaUsageAuthority = quotaUsageAuthority;
    }

    public QuotaDecision evaluate(
            PrincipalRef principal,
            String quotaKey,
            Instant periodStart,
            Instant periodEnd,
            long requestedAmount,
            String traceId,
            Instant decidedAt) {
        long limit = quotaPolicyService.getQuotaPolicy(quotaKey).limitValue();
        QuotaDecision decision = quotaUsageAuthority.decide(new QuotaUsageQuery(
                principal, quotaKey, periodStart, periodEnd, requestedAmount,
                limit, traceId, decidedAt));
        log.debug("Quota evaluation for {} / {}: usage={}, limit={}, requested={}, allowed={}",
                principal, quotaKey, decision.usedUnits(), limit, requestedAmount, decision.allowed());
        return decision;
    }

    public QuotaDecision evaluateWithProfile(
            PrincipalRef principal,
            String quotaKey,
            QuotaProfile profile,
            Instant periodStart,
            Instant periodEnd,
            long requestedAmount,
            String traceId,
            Instant decidedAt) {
        long limit = quotaPolicyService.resolveLimitFromProfile(profile, quotaKey);
        return quotaUsageAuthority.decide(new QuotaUsageQuery(
                principal, quotaKey, periodStart, periodEnd, requestedAmount,
                limit, traceId, decidedAt));
    }
}
