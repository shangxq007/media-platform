package com.example.platform.render.infrastructure.billing.decision;

import com.example.platform.billing.app.CostEstimationService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.quota.app.QuotaService;
import com.example.platform.quota.domain.QuotaBucket;
import com.example.platform.render.app.RenderQuotaService;
import com.example.platform.render.infrastructure.billing.policy.CreditSystem;
import com.example.platform.render.infrastructure.billing.policy.PolicyDecisionTraceNode;
import com.example.platform.render.infrastructure.billing.policy.PolicyEngine;
import com.example.platform.render.infrastructure.billing.policy.PricingEngine;
import com.example.platform.render.infrastructure.providerruntime.trace.ProviderTraceEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified billing decision engine for render pipeline.
 * 
 * <p>This is a PURE DECISION LAYER - it does not mutate state.
 * All billing logic is centralized here.
 * 
 * <p>Features:
 * <ul>
 *   <li>Deterministic decisions based on input state</li>
 *   <li>No state mutation</li>
 *   <li>Full traceability via traceId</li>
 *   <li>Dry-run mode for UI cost preview</li>
 *   <li>Extensible policy evaluation</li>
 * </ul>
 */
@Service
public class BillingDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(BillingDecisionEngine.class);

    private final SubscriptionBillingService subscriptionService;
    private final QuotaService quotaService;
    private final RenderQuotaService renderQuotaService;
    private final CostEstimationService costEstimationService;
    private final PolicyEngine policyEngine;
    private final PricingEngine pricingEngine;
    private final CreditSystem creditSystem;
    private final ProviderTraceEmitter traceEmitter;

    @Value("${billing.enforcement.enabled:false}")
    private boolean enforcementEnabled;

    public BillingDecisionEngine(
            SubscriptionBillingService subscriptionService,
            QuotaService quotaService,
            RenderQuotaService renderQuotaService,
            CostEstimationService costEstimationService,
            PolicyEngine policyEngine,
            PricingEngine pricingEngine,
            CreditSystem creditSystem,
            ProviderTraceEmitter traceEmitter) {
        this.subscriptionService = subscriptionService;
        this.quotaService = quotaService;
        this.renderQuotaService = renderQuotaService;
        this.costEstimationService = costEstimationService;
        this.policyEngine = policyEngine;
        this.pricingEngine = pricingEngine;
        this.creditSystem = creditSystem;
        this.traceEmitter = traceEmitter;
    }

    /**
     * Make a billing decision.
     * 
     * @param request the billing decision request
     * @return the billing decision
     */
    public BillingDecision decide(BillingDecisionRequest request) {
        long startTime = System.currentTimeMillis();
        String traceId = request.traceId() != null ? request.traceId() : generateTraceId();

        log.info("[{}] Billing decision requested: tenant={} action={} provider={} dryRun={}",
                traceId, request.tenantId(), request.actionType(), 
                request.providerCandidate(), request.dryRun());

        // If enforcement disabled, always allow
        if (!enforcementEnabled) {
            log.debug("[{}] Billing enforcement disabled, allowing", traceId);
            return createAllowDecision(request, traceId, startTime);
        }

        // Step 1: Evaluate subscription
        BillingDecision subscriptionDecision = evaluateSubscription(request, traceId);
        if (subscriptionDecision != null) {
            return finalizeDecision(subscriptionDecision, request, startTime);
        }

        // Step 2: Evaluate quota
        BillingDecision quotaDecision = evaluateQuota(request, traceId);
        if (quotaDecision != null) {
            return finalizeDecision(quotaDecision, request, startTime);
        }

        // Step 3: Evaluate policies (NEW)
        PolicyEngine.PolicyEvaluationResult policyResult = evaluatePoliciesNew(request, traceId);
        if (policyResult != null && !policyResult.isAllowed()) {
            BillingDecision policyDecision = BillingDecision.deny(
                    BillingDecision.ReasonCode.POLICY_VIOLATION,
                    policyResult.denyReason(),
                    traceId
            );
            return finalizeDecision(policyDecision, request, startTime);
        }

        // Step 4: Calculate pricing (NEW)
        PricingEngine.PricingResult pricingResult = calculatePricing(request, traceId, policyResult);

        // Step 5: Evaluate credits (NEW)
        BillingDecision creditDecision = evaluateCreditsNew(request, pricingResult, traceId);
        if (creditDecision != null) {
            return finalizeDecision(creditDecision, request, startTime);
        }

        // Step 6: Build final cost estimate
        BillingDecision.CostEstimate costEstimate = buildCostEstimate(pricingResult, request, traceId);

        // All checks passed - allow
        BillingDecision.QuotaImpact quotaImpact = calculateQuotaImpact(request, traceId);
        BillingDecision allowDecision = BillingDecision.allow(costEstimate, quotaImpact, traceId);
        return finalizeDecision(allowDecision, request, startTime);
    }

    /**
     * Make a dry-run decision (for UI cost preview).
     */
    public BillingDecision dryRun(BillingDecisionRequest request) {
        BillingDecisionRequest dryRunRequest = new BillingDecisionRequest(
                request.tenantId(),
                request.userId(),
                request.workspaceId(),
                request.actionType(),
                request.estimatedCost(),
                request.resourceProfile(),
                request.providerCandidate(),
                request.preset(),
                request.currentUsage(),
                request.subscriptionState(),
                request.quotaState(),
                request.creditBalance(),
                true, // force dry run
                request.traceId()
        );
        return decide(dryRunRequest);
    }

    // ---------------------------------------------------------------------------
    // Evaluation Steps
    // ---------------------------------------------------------------------------

    private BillingDecision evaluateSubscription(BillingDecisionRequest request, String traceId) {
        if (request.subscriptionState() != null) {
            // Use provided state
            if (!request.subscriptionState().hasActiveSubscription()) {
                return BillingDecision.deny(
                        BillingDecision.ReasonCode.NO_SUBSCRIPTION,
                        "No active subscription found",
                        traceId
                );
            }
            return null; // Subscription OK
        }

        // Fetch subscription state
        SubscriptionContract subscription = subscriptionService.getCurrentSubscription(
                request.tenantId(), request.userId());

        if (subscription == null) {
            return BillingDecision.deny(
                    BillingDecision.ReasonCode.NO_SUBSCRIPTION,
                    "No active subscription found for tenant: " + request.tenantId(),
                    traceId
            );
        }

        if (!subscription.isActiveAt(Instant.now())) {
            return BillingDecision.deny(
                    BillingDecision.ReasonCode.SUBSCRIPTION_INACTIVE,
                    "Subscription is not active: " + subscription.lifecycleState(),
                    traceId
            );
        }

        return null; // Subscription OK
    }

    private BillingDecision evaluateQuota(BillingDecisionRequest request, String traceId) {
        // Check render-specific quota
        boolean hasRenderQuota = renderQuotaService.checkQuota(request.tenantId(), "render", 1);
        if (!hasRenderQuota) {
            return BillingDecision.deny(
                    BillingDecision.ReasonCode.QUOTA_EXCEEDED,
                    "Render quota exceeded for tenant: " + request.tenantId(),
                    traceId
            );
        }

        // Check general quota buckets
        List<QuotaBucket> buckets = quotaService.getBucketsForTenant(request.tenantId());
        for (QuotaBucket bucket : buckets) {
            if (bucket.usageRatio() >= 1.0) {
                return BillingDecision.deny(
                        BillingDecision.ReasonCode.QUOTA_BUCKET_FULL,
                        String.format("Quota bucket %s full (usage: %d/%d)", 
                                bucket.featureCode(), bucket.currentUsage(), bucket.limit()),
                        traceId
                );
            }

            // Warn if approaching limit (80%)
            if (bucket.usageRatio() >= 0.8) {
                log.warn("[{}] Quota bucket {} at {}% capacity for tenant {}",
                        traceId, bucket.featureCode(), 
                        Math.round(bucket.usageRatio() * 100), request.tenantId());
            }
        }

        return null; // Quota OK
    }

    private BillingDecision.CostEstimate estimateCost(BillingDecisionRequest request, String traceId) {
        if (request.estimatedCost() > 0) {
            // Use provided cost estimate
            return new BillingDecision.CostEstimate(
                    request.estimatedCost(),
                    "USD",
                    request.providerCandidate(),
                    request.preset(),
                    request.resourceProfile() != null ? request.resourceProfile().estimatedDurationSeconds() : 0,
                    request.resourceProfile() != null && request.resourceProfile().useGpu(),
                    Map.of()
            );
        }

        // Estimate cost using CostEstimationService
        long durationSeconds = request.resourceProfile() != null 
                ? request.resourceProfile().estimatedDurationSeconds() : 60;
        boolean useGpu = request.resourceProfile() != null && request.resourceProfile().useGpu();

        CostEstimationService.CostEstimate estimate = costEstimationService.estimate(
                request.providerCandidate() != null ? request.providerCandidate() : "javacv",
                request.preset() != null ? request.preset() : "default_1080p",
                "mp4",
                durationSeconds,
                useGpu
        );

        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("compute", estimate.estimatedCost() * 0.8);
        breakdown.put("storage", estimate.estimatedCost() * 0.15);
        breakdown.put("api", estimate.estimatedCost() * 0.05);

        return new BillingDecision.CostEstimate(
                estimate.estimatedCost(),
                estimate.currency(),
                estimate.providerKey(),
                estimate.preset(),
                estimate.estimatedDurationSeconds(),
                estimate.useGpu(),
                breakdown
        );
    }

    private BillingDecision evaluateCredits(BillingDecisionRequest request, 
                                             BillingDecision.CostEstimate costEstimate,
                                             String traceId) {
        // Credit evaluation would go here
        // For now, skip if no credit balance provided
        if (request.creditBalance() <= 0) {
            return null;
        }

        if (costEstimate != null && request.creditBalance() < costEstimate.estimatedCost()) {
            return BillingDecision.requireCredits(
                    costEstimate,
                    request.creditBalance(),
                    traceId
            );
        }

        return null; // Credits OK
    }

    private BillingDecision evaluatePolicies(BillingDecisionRequest request,
                                               BillingDecision.CostEstimate costEstimate,
                                               String traceId) {
        // Legacy policy evaluation - kept for backward compatibility
        return null;
    }

    // ---------------------------------------------------------------------------
    // New Policy/Pricing Integration
    // ---------------------------------------------------------------------------

    private PolicyEngine.PolicyEvaluationResult evaluatePoliciesNew(BillingDecisionRequest request,
                                                                      String traceId) {
        if (policyEngine == null) {
            return null;
        }

        // Build policy evaluation context
        PolicyEngine.PolicyEvaluationContext context = new PolicyEngine.PolicyEvaluationContext(
                request.tenantId(),
                request.workspaceId(),
                request.userId(),
                request.actionType().name(),
                request.providerCandidate(),
                request.preset(),
                request.subscriptionState() != null ? request.subscriptionState().tier() : null,
                request.currentUsage() != null ? request.currentUsage() : Map.of(),
                request.estimatedCost(),
                request.resourceProfile() != null ? request.resourceProfile().estimatedDurationSeconds() : 0,
                request.resourceProfile() != null ? request.resourceProfile().outputSizeBytes() : 0,
                request.resourceProfile() != null && request.resourceProfile().useGpu(),
                com.example.platform.render.infrastructure.billing.policy.PolicyScope.TENANT,
                request.tenantId(),
                Map.of()
        );

        PolicyEngine.PolicyEvaluationResult result = policyEngine.evaluate(context);

        // Emit policy trace
        emitPolicyTrace(traceId, request, result, null);

        return result;
    }

    private PricingEngine.PricingResult calculatePricing(BillingDecisionRequest request,
                                                           String traceId,
                                                           PolicyEngine.PolicyEvaluationResult policyResult) {
        if (pricingEngine == null) {
            return null;
        }

        long durationSeconds = request.resourceProfile() != null 
                ? request.resourceProfile().estimatedDurationSeconds() : 60;
        boolean useGpu = request.resourceProfile() != null && request.resourceProfile().useGpu();

        PricingEngine.PricingRequest pricingRequest = new PricingEngine.PricingRequest(
                request.providerCandidate() != null ? request.providerCandidate() : "ffmpeg",
                request.preset() != null ? request.preset() : "default_1080p",
                request.subscriptionState() != null ? request.subscriptionState().tier() : "FREE",
                durationSeconds,
                useGpu,
                0, // effect count
                request.resourceProfile() != null ? request.resourceProfile().outputSizeBytes() : 0,
                null // promo code
        );

        return pricingEngine.calculate(pricingRequest);
    }

    private BillingDecision evaluateCreditsNew(BillingDecisionRequest request,
                                                 PricingEngine.PricingResult pricingResult,
                                                 String traceId) {
        if (creditSystem == null || pricingResult == null) {
            return null;
        }

        double availableCredits = creditSystem.getAvailableBalance(request.tenantId());
        if (availableCredits < pricingResult.finalPrice()) {
            BillingDecision.CostEstimate costEstimate = buildCostEstimate(pricingResult, request, traceId);
            return BillingDecision.requireCredits(costEstimate, availableCredits, traceId);
        }

        return null;
    }

    private BillingDecision.CostEstimate buildCostEstimate(PricingEngine.PricingResult pricingResult,
                                                             BillingDecisionRequest request,
                                                             String traceId) {
        if (pricingResult == null) {
            return estimateCost(request, traceId);
        }

        Map<String, Double> breakdown = Map.of(
                "compute", pricingResult.breakdown().computeCost(),
                "storage", pricingResult.breakdown().storageCost(),
                "api", pricingResult.breakdown().apiCost(),
                "tierDiscount", pricingResult.breakdown().tierDiscount(),
                "promoDiscount", pricingResult.breakdown().promoDiscount()
        );

        return new BillingDecision.CostEstimate(
                pricingResult.finalPrice(),
                pricingResult.currency(),
                pricingResult.providerKey(),
                pricingResult.preset(),
                request.resourceProfile() != null ? request.resourceProfile().estimatedDurationSeconds() : 0,
                request.resourceProfile() != null && request.resourceProfile().useGpu(),
                breakdown
        );
    }

    private void emitPolicyTrace(String traceId, BillingDecisionRequest request,
                                   PolicyEngine.PolicyEvaluationResult policyResult,
                                   PricingEngine.PricingResult pricingResult) {
        if (traceEmitter == null || policyResult == null) {
            return;
        }

        try {
            PolicyDecisionTraceNode traceNode = PolicyDecisionTraceNode.fromEvaluation(
                    traceId, request.tenantId(), request.actionType().name(),
                    policyResult, pricingResult);
            traceEmitter.emitExecution(
                    traceId,
                    request.tenantId(),
                    "POLICY_DECISION",
                    traceNode.allowed(),
                    traceNode.allowed() ? null : traceNode.denyReason()
            );
        } catch (Exception e) {
            log.warn("Failed to emit policy trace: {}", e.getMessage());
        }
    }

    private BillingDecision.QuotaImpact calculateQuotaImpact(BillingDecisionRequest request, String traceId) {
        // Calculate quota impact
        long currentRenderUsage = 0;
        List<QuotaBucket> buckets = quotaService.getBucketsForTenant(request.tenantId());
        for (QuotaBucket bucket : buckets) {
            if ("render".equals(bucket.featureCode())) {
                currentRenderUsage = bucket.currentUsage();
                return BillingDecision.QuotaImpact.of(
                        "render",
                        currentRenderUsage,
                        bucket.limit(),
                        1
                );
            }
        }

        return BillingDecision.QuotaImpact.of("render", 0, 100, 1);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private BillingDecision createAllowDecision(BillingDecisionRequest request, 
                                                  String traceId, long startTime) {
        BillingDecision.CostEstimate costEstimate = request.estimatedCost() > 0
                ? new BillingDecision.CostEstimate(
                        request.estimatedCost(), "USD", request.providerCandidate(),
                        request.preset(), 0, false, Map.of())
                : null;

        BillingDecision.QuotaImpact quotaImpact = BillingDecision.QuotaImpact.of("render", 0, 100, 1);

        BillingDecision decision = BillingDecision.allow(costEstimate, quotaImpact, traceId);
        return finalizeDecision(decision, request, startTime);
    }

    private BillingDecision finalizeDecision(BillingDecision decision, 
                                               BillingDecisionRequest request,
                                               long startTime) {
        long durationMs = System.currentTimeMillis() - startTime;

        // Emit trace
        BillingDecisionTraceNode traceNode = BillingDecisionTraceNode.fromDecision(
                decision, request, durationMs);
        emitTrace(traceNode);

        log.info("[{}] Billing decision: {} ({}) in {}ms",
                decision.traceId(), decision.decision(), decision.reasonCode(), durationMs);

        return decision;
    }

    private void emitTrace(BillingDecisionTraceNode traceNode) {
        try {
            traceEmitter.emitExecution(
                    traceNode.traceId(),
                    traceNode.tenantId(),
                    "BILLING_DECISION",
                    traceNode.isAllowed(),
                    traceNode.isAllowed() ? null : traceNode.reasonMessage()
            );
        } catch (Exception e) {
            log.warn("Failed to emit billing decision trace: {}", e.getMessage());
        }
    }

    private String generateTraceId() {
        return "billing-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
