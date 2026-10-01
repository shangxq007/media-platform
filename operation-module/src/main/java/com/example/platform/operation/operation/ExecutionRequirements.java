package com.example.platform.operation.operation;

import java.time.Duration;
import java.util.Objects;

/**
 * Provider-neutral execution requirements
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, DOM-OPERATION-001 layer 4).
 *
 * <p>Constraints on <em>how</em> a resolved implementation must execute. They
 * do not change operation semantics; they enter the resolved plan digest, not
 * the operation semantic digest. No provider/plugin/backend/worker identity.</p>
 *
 * @param executionMode      user-facing execution mode
 * @param costCap            typed cost/quota cap
 * @param deadline           bounded wall-clock deadline
 * @param selectionObjective provider-neutral selection objective
 */
public record ExecutionRequirements(
        ExecutionMode executionMode,
        CostCap costCap,
        Deadline deadline,
        SelectionObjective selectionObjective) {

    public ExecutionRequirements {
        Objects.requireNonNull(executionMode, "executionMode");
        Objects.requireNonNull(costCap, "costCap");
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(selectionObjective, "selectionObjective");
    }

    /** Documented non-binding defaults (materialized and pinned at resolve). */
    public static ExecutionRequirements defaults() {
        return new ExecutionRequirements(
                ExecutionMode.ASYNCHRONOUS_DURABLE,
                new CostCap(java.math.BigDecimal.ZERO, "quota-unit"),
                new Deadline(Duration.ofMinutes(1)),
                SelectionObjective.PREFER_DETERMINISTIC);
    }
}
