package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BillingDecisionService {

    private static final Logger log = LoggerFactory.getLogger(BillingDecisionService.class);

    private final ConcurrentHashMap<String, BillingDecision> decisions = new ConcurrentHashMap<>();

    public BillingDecision decideBilling(String action, BillingContext context) {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }

        String decisionId = Ids.newId("dec");
        String pricingModel = context.pricingModel() != null ? context.pricingModel().name() : "USAGE_BASED";
        long estimatedAmountMinor = context.estimatedAmountMinor();
        String currencyCode = context.currencyCode() != null ? context.currencyCode() : "USD";
        boolean useCredits = context.useCredits() && context.availableCreditMinor() != null
                && context.availableCreditMinor() >= estimatedAmountMinor;

        Map<String, Object> details = new HashMap<>();
        details.put("action", action);
        details.put("tenantId", context.tenantId());
        details.put("userId", context.userId());
        details.put("estimatedAmountMinor", estimatedAmountMinor);
        details.put("availableCreditMinor", context.availableCreditMinor());
        details.put("useCredits", useCredits);

        String status;
        if (estimatedAmountMinor <= 0) {
            status = BillingDecision.STATUS_APPROVED;
        } else if (useCredits) {
            status = BillingDecision.STATUS_APPROVED;
        } else if (context.estimatedAmountMinor() > 0 && context.availableCreditMinor() != null
                && context.availableCreditMinor() > 0) {
            details.put("partialCredit", true);
            details.put("remainingAfterCredit", estimatedAmountMinor - context.availableCreditMinor());
            if (context.availableCreditMinor() >= estimatedAmountMinor) {
                status = BillingDecision.STATUS_APPROVED;
            } else {
                status = BillingDecision.STATUS_DENIED;
                details.put("reason", "Insufficient credits: need " + estimatedAmountMinor
                        + " but only " + context.availableCreditMinor() + " available");
            }
        } else {
            status = BillingDecision.STATUS_DENIED;
            details.put("reason", "No payment method or credits available for amount " + estimatedAmountMinor);
        }

        BillingDecision decision = new BillingDecision(
                decisionId, action, context.tenantId(), context.userId(),
                pricingModel, estimatedAmountMinor, currencyCode,
                useCredits, details, status);

        decisions.put(decisionId, decision);
        log.info("BillingDecisionService: decision {} action={} status={} amount={} {}",
                decisionId, action, status, estimatedAmountMinor, currencyCode);
        return decision;
    }

    public BillingDecision getDecision(String decisionId) {
        return decisions.get(decisionId);
    }

    public record BillingContext(
            String tenantId,
            String userId,
            PricingModel pricingModel,
            long estimatedAmountMinor,
            String currencyCode,
            Long availableCreditMinor,
            boolean useCredits) {
    }
}
