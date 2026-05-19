package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaDecision;
import com.example.platform.entitlement.domain.QuotaProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QuotaDecisionService {

    private static final Logger log = LoggerFactory.getLogger(QuotaDecisionService.class);

    private final QuotaPolicyService quotaPolicyService;
    private final QuotaUsageService quotaUsageService;

    public QuotaDecisionService(QuotaPolicyService quotaPolicyService, QuotaUsageService quotaUsageService) {
        this.quotaPolicyService = quotaPolicyService;
        this.quotaUsageService = quotaUsageService;
    }

    public QuotaDecision evaluate(String subjectId, String featureCode, long requestedAmount) {
        long currentUsage = quotaUsageService.getUsage(subjectId, featureCode);
        long remaining = quotaPolicyService.remaining(featureCode, currentUsage);
        long afterRequest = remaining - requestedAmount;
        boolean allowed = afterRequest >= 0;
        log.debug("Quota evaluation for {} / {}: usage={}, remaining={}, requested={}, allowed={}",
                subjectId, featureCode, currentUsage, remaining, requestedAmount, allowed);
        return new QuotaDecision(subjectId, featureCode, allowed, remaining, currentUsage);
    }

    public QuotaDecision evaluateWithProfile(String subjectId, String featureCode,
            QuotaProfile profile, long requestedAmount) {
        long limit = quotaPolicyService.resolveLimitFromProfile(profile, featureCode);
        long currentUsage = quotaUsageService.getUsage(subjectId, featureCode);
        long remaining = Math.max(0, limit - currentUsage);
        boolean allowed = requestedAmount <= remaining;
        log.debug("Quota evaluation (profile) for {} / {}: limit={}, usage={}, requested={}, allowed={}",
                subjectId, featureCode, limit, currentUsage, requestedAmount, allowed);
        return new QuotaDecision(subjectId, featureCode, allowed, remaining, currentUsage);
    }

    public void recordUsage(String subjectId, String featureCode, long amount) {
        quotaUsageService.incrementUsage(subjectId, featureCode, amount);
    }

    public long getRemaining(String subjectId, String featureCode) {
        long currentUsage = quotaUsageService.getUsage(subjectId, featureCode);
        return quotaPolicyService.remaining(featureCode, currentUsage);
    }

    public long getRemainingWithProfile(String subjectId, String featureCode, QuotaProfile profile) {
        long limit = quotaPolicyService.resolveLimitFromProfile(profile, featureCode);
        long currentUsage = quotaUsageService.getUsage(subjectId, featureCode);
        return Math.max(0, limit - currentUsage);
    }
}
