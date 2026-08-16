package com.example.platform.operation.plan;

import java.util.List;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (PT15/§19): deterministic projection of
 * an immutable OperationPlan. Binds exact PlanDigest; never mutates plan,
 * never re-resolves latest. Primary/secondary/warnings/blockers distinguished.
 */
public record OperationPlanPreview(
        String planDigest,
        String definitionId,
        String baseRevisionId,
        List<String> primaryChanges,
        List<String> secondaryConsequences,
        List<String> warnings,
        List<String> blockers,
        String candidateContentHash,
        boolean noOp) {

    public static OperationPlanPreview of(OperationPlan plan) {
        return new OperationPlanPreview(
                plan.planDigest(),
                plan.sourceInstance().definitionId().value(),
                plan.baseRevisionId(),
                plan.plannedChanges().stream()
                        .filter(PlannedChange::primary)
                        .map(OperationPlanDigest::changeKey).toList(),
                plan.plannedChanges().stream()
                        .filter(c -> !c.primary())
                        .map(OperationPlanDigest::changeKey).toList(),
                List.of(),
                List.of(),
                plan.candidateContentHash(),
                plan.noOp());
    }
}
