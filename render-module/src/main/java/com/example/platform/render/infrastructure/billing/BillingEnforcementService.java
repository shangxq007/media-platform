package com.example.platform.render.infrastructure.billing;

import com.example.platform.billing.app.CostEstimationService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.quota.app.QuotaBucketSummary;
import com.example.platform.quota.app.QuotaService;
import com.example.platform.render.app.RenderQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Gateway service for billing and quota enforcement in the render pipeline.
 * 
 * <p>This service acts as the single point of enforcement for all billing-related
 * checks in the render execution path. It integrates with:
 * <ul>
 *   <li>SubscriptionBillingService - validates active subscriptions</li>
 *   <li>QuotaService - checks and reserves quota</li>
 *   <li>CostEstimationService - estimates render costs</li>
 *   <li>UsageMeteringService - records actual usage</li>
 * </ul>
 */
@Service
public class BillingEnforcementService {

    private static final Logger log = LoggerFactory.getLogger(BillingEnforcementService.class);

    private final SubscriptionBillingService subscriptionService;
    private final QuotaService quotaService;
    private final RenderQuotaService renderQuotaService;
    private final CostEstimationService costEstimationService;
    private final UsageMeteringService usageMeteringService;
    private final RenderBillingRecordRepository billingRecordRepository;

    @Value("${billing.enforcement.enabled:false}")
    private boolean enforcementEnabled;

    public BillingEnforcementService(
            SubscriptionBillingService subscriptionService,
            QuotaService quotaService,
            RenderQuotaService renderQuotaService,
            CostEstimationService costEstimationService,
            UsageMeteringService usageMeteringService,
            RenderBillingRecordRepository billingRecordRepository) {
        this.subscriptionService = subscriptionService;
        this.quotaService = quotaService;
        this.renderQuotaService = renderQuotaService;
        this.costEstimationService = costEstimationService;
        this.usageMeteringService = usageMeteringService;
        this.billingRecordRepository = billingRecordRepository;
    }

    /**
     * Validate that a tenant has an active subscription.
     *
     * @param tenantId the tenant to validate
     * @return ValidationResult with success/failure and reason
     */
    public ValidationResult validateSubscription(String tenantId) {
        if (!enforcementEnabled) {
            log.debug("Billing enforcement disabled, skipping subscription check for tenant {}", tenantId);
            return ValidationResult.success("enforcement_disabled");
        }

        if (tenantId == null || tenantId.isBlank()) {
            return ValidationResult.failure("MISSING_TENANT", "Tenant ID is required");
        }

        SubscriptionContract subscription = subscriptionService.getCurrentSubscription(tenantId, null);
        if (subscription == null) {
            log.warn("No active subscription found for tenant {}", tenantId);
            return ValidationResult.failure("NO_SUBSCRIPTION", "No active subscription found");
        }

        if (!subscription.isActiveAt(Instant.now())) {
            log.warn("Subscription {} for tenant {} is not active: {}", 
                    subscription.contractId(), tenantId, subscription.lifecycleState());
            return ValidationResult.failure("SUBSCRIPTION_INACTIVE", 
                    "Subscription is not active: " + subscription.lifecycleState());
        }

        log.debug("Subscription validated for tenant {}: {}", tenantId, subscription.contractId());
        return ValidationResult.success(subscription.contractId());
    }

    /**
     * Validate that a tenant has sufficient quota for a render job.
     *
     * @param tenantId the tenant to validate
     * @param estimatedUsageSeconds estimated render duration in seconds
     * @return ValidationResult with success/failure and reason
     */
    public ValidationResult validateQuota(String tenantId, long estimatedUsageSeconds) {
        if (!enforcementEnabled) {
            log.debug("Billing enforcement disabled, skipping quota check for tenant {}", tenantId);
            return ValidationResult.success("enforcement_disabled");
        }

        // Check render quota via RenderQuotaService
        boolean hasQuota = renderQuotaService.checkQuota(tenantId, "render", 1);
        if (!hasQuota) {
            log.warn("Render quota exceeded for tenant {}", tenantId);
            return ValidationResult.failure("QUOTA_EXCEEDED", "Render quota exceeded");
        }

        // Check usage quota via QuotaService
        List<QuotaBucketSummary> buckets = quotaService.getBucketSummariesForTenant(tenantId);
        for (QuotaBucketSummary bucket : buckets) {
            if (bucket.usageRatio() >= 1.0) {
                log.warn("Quota bucket {} full for tenant {} (usage: {}/{})", 
                        bucket.featureCode(), tenantId, bucket.currentUsage(), bucket.limit());
                return ValidationResult.failure("QUOTA_BUCKET_FULL", 
                        "Quota exceeded for " + bucket.featureCode());
            }
        }

        log.debug("Quota validated for tenant {}", tenantId);
        return ValidationResult.success("quota_available");
    }

    /**
     * Reserve quota for a render job execution.
     *
     * @param tenantId the tenant
     * @param jobId the render job ID
     * @param estimatedCost estimated cost of the render
     * @return ReservationResult with reservation ID
     */
    public ReservationResult reserveQuota(String tenantId, String jobId, double estimatedCost) {
        if (!enforcementEnabled) {
            log.debug("Billing enforcement disabled, skipping quota reservation for job {}", jobId);
            return ReservationResult.success("reservation_disabled", 0);
        }

        // Consume one render quota unit
        renderQuotaService.consumeQuota(tenantId, "render", 1);

        // Create billing record
        RenderBillingRecord record = RenderBillingRecord.create(
                jobId, tenantId, estimatedCost, Instant.now());
        billingRecordRepository.save(record);

        log.info("Reserved quota for job {} tenant {}: estimated cost ${}", 
                jobId, tenantId, String.format("%.4f", estimatedCost));
        return ReservationResult.success(record.id(), estimatedCost);
    }

    /**
     * Estimate the cost of a render job.
     *
     * @param providerKey the provider to use
     * @param preset the render preset
     * @param outputFormat the output format
     * @param estimatedDurationSeconds estimated duration
     * @param useGpu whether GPU is required
     * @return CostEstimate
     */
    public CostEstimationService.CostEstimate estimateCost(
            String providerKey, String preset, String outputFormat,
            long estimatedDurationSeconds, boolean useGpu) {
        return costEstimationService.estimate(providerKey, preset, outputFormat, 
                estimatedDurationSeconds, useGpu);
    }

    /**
     * Finalize cost and record usage after render completion.
     *
     * @param tenantId the tenant
     * @param jobId the render job ID
     * @param providerId the provider that executed the render
     * @param actualDurationSeconds actual render duration
     * @param outputSizeBytes output file size
     * @return the finalized billing record
     */
    public RenderBillingRecord finalizeCost(
            String tenantId, String jobId, String providerId,
            long actualDurationSeconds, long outputSizeBytes) {
        if (!enforcementEnabled) {
            log.debug("Billing enforcement disabled, skipping cost finalization for job {}", jobId);
            return null;
        }

        // Calculate actual cost
        double actualCost = costEstimationService.estimate(
                providerId, "default", "mp4", actualDurationSeconds, false
        ).estimatedCost();

        // Update billing record
        RenderBillingRecord existing = billingRecordRepository.findByJobId(jobId);
        if (existing == null) {
            log.warn("No billing record found for job {}", jobId);
            existing = RenderBillingRecord.create(jobId, tenantId, actualCost, Instant.now());
        }

        RenderBillingRecord finalized = existing.finalize(
                actualCost, actualDurationSeconds, providerId, outputSizeBytes);
        billingRecordRepository.save(finalized);

        // Record usage in metering system
        usageMeteringService.recordUsage(
                tenantId, null, null,
                "render_seconds", actualDurationSeconds, "seconds",
                Instant.now(), "job-" + jobId + "-seconds"
        );

        usageMeteringService.recordUsage(
                tenantId, null, null,
                "render_output_bytes", outputSizeBytes, "bytes",
                Instant.now(), "job-" + jobId + "-bytes"
        );

        log.info("Finalized cost for job {}: actual ${} ({} seconds, {} bytes)", 
                jobId, String.format("%.4f", actualCost), actualDurationSeconds, outputSizeBytes);
        return finalized;
    }

    /**
     * Check if enforcement is enabled.
     */
    public boolean isEnforcementEnabled() {
        return enforcementEnabled;
    }

    /**
     * Get billing record for a job.
     */
    public RenderBillingRecord getBillingRecord(String jobId) {
        return billingRecordRepository.findByJobId(jobId);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ValidationResult(
            boolean success,
            String code,
            String reason,
            String referenceId
    ) {
        public static ValidationResult success(String referenceId) {
            return new ValidationResult(true, "OK", null, referenceId);
        }

        public static ValidationResult failure(String code, String reason) {
            return new ValidationResult(false, code, reason, null);
        }
    }

    public record ReservationResult(
            boolean success,
            String reservationId,
            double reservedAmount,
            String error
    ) {
        public static ReservationResult success(String reservationId, double amount) {
            return new ReservationResult(true, reservationId, amount, null);
        }

        public static ReservationResult failure(String error) {
            return new ReservationResult(false, null, 0, error);
        }
    }
}
