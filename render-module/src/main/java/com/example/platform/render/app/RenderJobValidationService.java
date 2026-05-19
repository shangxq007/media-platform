package com.example.platform.render.app;

import com.example.platform.shared.cost.BudgetGuardPort;
import com.example.platform.shared.cost.BudgetGuardPort.BudgetCheckResult;
import com.example.platform.shared.cost.CostEstimationPort;
import com.example.platform.shared.cost.CostEstimationPort.CostEstimate;
import com.example.platform.shared.cost.CostReservationPort;
import com.example.platform.shared.entitlement.EntitlementPort;
import com.example.platform.shared.entitlement.EntitlementPort.ExportValidationResult;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Validates render job submissions by checking entitlements, costs, anomaly risk,
 * and reserving budget before job creation.
 */
@Service
public class RenderJobValidationService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobValidationService.class);

    private EntitlementPort entitlementPort;
    private CostEstimationPort costEstimationPort;
    private BudgetGuardPort budgetGuardPort;
    private CostReservationPort costReservationPort;
    private final RenderProviderRouter providerRouter;

    public RenderJobValidationService(RenderProviderRouter providerRouter) {
        this.providerRouter = providerRouter;
    }

    @Autowired(required = false)
    public void setEntitlementPort(EntitlementPort port) {
        this.entitlementPort = port;
    }

    @Autowired(required = false)
    public void setCostEstimationPort(CostEstimationPort port) {
        this.costEstimationPort = port;
    }

    @Autowired(required = false)
    public void setBudgetGuardPort(BudgetGuardPort port) {
        this.budgetGuardPort = port;
    }

    @Autowired(required = false)
    public void setCostReservationPort(CostReservationPort port) {
        this.costReservationPort = port;
    }

    /**
     * Full pre-submission validation pipeline.
     */
    public ValidationPipelineResult validate(String tenantId, String userId,
            String preset, String outputFormat, Long estimatedDurationSeconds) {
        long estDuration = estimatedDurationSeconds != null ? estimatedDurationSeconds : 60L;

        log.info("RenderJobValidationService: validating tenant={} preset={} format={}",
                tenantId, preset, outputFormat);

        // Step 1: Validate entitlement
        ExportValidationResult entitlementResult = entitlementPort.validateExport(
                tenantId, userId, preset, outputFormat, estDuration);

        if (!entitlementResult.allowed()) {
            log.warn("RenderJobValidationService: entitlement denied for tenant={}: {}",
                    tenantId, entitlementResult.reasonCode());
            return ValidationPipelineResult.denied(entitlementResult);
        }

        // Step 2: Estimate cost
        String providerKey = entitlementResult.providerCandidates().isEmpty()
                ? "javacv" : entitlementResult.providerCandidates().get(0);
        boolean useGpu = preset.startsWith("gpu_");
        CostEstimationPort.CostEstimate estimate = costEstimationPort.estimate(
                providerKey, preset, outputFormat, estDuration, useGpu);

        // Step 3: Check budget
        BudgetCheckResult budgetResult = budgetGuardPort.checkBudget(tenantId, estimate.estimatedCost());
        if (!budgetResult.allowed()) {
            log.warn("RenderJobValidationService: budget exceeded for tenant={}: {}",
                    tenantId, budgetResult.message());
            return ValidationPipelineResult.denied(entitlementResult, budgetResult, estimate);
        }

        // Step 4: Reserve cost
        costReservationPort.createReservation(tenantId, userId,
                "pending-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                estimate.estimatedCost(), estimate.currency());

        // Step 5: Select provider
        List<String> providerCandidates = entitlementResult.providerCandidates();

        log.info("RenderJobValidationService: validation passed for tenant={} preset={} cost={} {}",
                tenantId, preset, estimate.estimatedCost(), estimate.currency());

        return ValidationPipelineResult.allowed(entitlementResult, budgetResult, estimate, providerCandidates);
    }

    /**
     * Finalize cost after render job completes.
     */
    public void finalizeCost(String tenantId, String userId, String renderJobId,
            String providerKey, String preset, double actualCost, String currency) {
        costReservationPort.finalizeReservation(renderJobId, actualCost);
        // Budget spend recorded via event listener in billing module
        log.info("RenderJobValidationService: cost finalized for job={} actualCost={} {}",
                renderJobId, actualCost, currency);
    }

    /**
     * Release reservation for cancelled/failed jobs.
     */
    public void releaseCost(String renderJobId) {
        costReservationPort.releaseReservation(renderJobId);
        log.info("RenderJobValidationService: cost reservation released for job={}", renderJobId);
    }

    /**
     * Result of the validation pipeline.
     */
    public record ValidationPipelineResult(
            boolean allowed,
            String reasonCode,
            ExportValidationResult entitlementResult,
            BudgetCheckResult budgetResult,
            CostEstimate costEstimate,
            List<String> providerCandidates,
            String recommendedPreset,
            List<String> violations,
            List<String> recommendations) {

        public static ValidationPipelineResult allowed(ExportValidationResult entitlementResult,
                BudgetCheckResult budgetResult, CostEstimate estimate,
                List<String> providerCandidates) {
            return new ValidationPipelineResult(true, "ALLOWED",
                    entitlementResult, budgetResult, estimate, providerCandidates,
                    entitlementResult.recommendedPreset(),
                    List.of(), entitlementResult.recommendations());
        }

        public static ValidationPipelineResult denied(ExportValidationResult entitlementResult) {
            return new ValidationPipelineResult(false, entitlementResult.reasonCode(),
                    entitlementResult, null, null,
                    entitlementResult.providerCandidates(),
                    entitlementResult.recommendedPreset(),
                    entitlementResult.violations(),
                    entitlementResult.recommendations());
        }

        public static ValidationPipelineResult denied(ExportValidationResult entitlementResult,
                BudgetCheckResult budgetResult, CostEstimate estimate) {
            List<String> violations = new java.util.ArrayList<>(entitlementResult.violations());
            violations.add("BUDGET_EXCEEDED");
            List<String> recommendations = new java.util.ArrayList<>(entitlementResult.recommendations());
            if (budgetResult.message() != null) {
                recommendations.add(budgetResult.message());
            }
            return new ValidationPipelineResult(false, "BUDGET_EXCEEDED",
                    entitlementResult, budgetResult, estimate,
                    entitlementResult.providerCandidates(),
                    entitlementResult.recommendedPreset(),
                    violations, recommendations);
        }
    }
}
