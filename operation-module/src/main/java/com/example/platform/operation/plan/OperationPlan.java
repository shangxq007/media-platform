package com.example.platform.operation.plan;

import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.timeline.canonical.TimelineDocument;

import java.util.List;

/**
 * OPERATION_PLAN_TRANSACTION_MODEL_V1 (PT1-PT5/PDR3): immutable semantic
 * transition plan. NOT a patch list; retains source instance, exact base
 * identity, planned semantic changes (primary/secondary), fully materialized
 * candidate Timeline, candidate hash, validation proof, deterministic digest.
 * Plan itself never contains authorization/principal/targetRef/expectedHead.
 *
 * <p>{@code replanRequired} tells the apply boundary whether the plan must be
 * reproduced from its {@code sourceInstance} via the 3-arg planner before the
 * atomic mutation. Non-text plans set it {@code true} (unchanged behavior);
 * typed text plans set it {@code false} — they are consumed as a frozen plan
 * (text resolution inputs are structurally absent at apply). It is NEVER an
 * input to {@link OperationPlanDigest}, so it does not affect the plan digest.
 */
public record OperationPlan(
        String formatVersion,
        String baseRevisionId,
        String baseContentHash,
        OperationInstance sourceInstance,
        List<PlannedChange> plannedChanges,
        TimelineDocument candidateTimeline,
        String candidateContentHash,
        boolean validated,
        String planDigest,
        boolean noOp,
        boolean replanRequired) {

    public OperationPlan {
        if (baseRevisionId == null || baseRevisionId.isBlank()) {
            throw new IllegalArgumentException("baseRevisionId required");
        }
        if (plannedChanges == null || candidateTimeline == null || planDigest == null) {
            throw new IllegalArgumentException("plan fields required");
        }
    }

    public static final String FORMAT_VERSION = "operation-plan-format-v1";
}
