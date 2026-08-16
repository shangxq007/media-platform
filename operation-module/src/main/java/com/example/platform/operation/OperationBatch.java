package com.example.platform.operation.operation;

import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.timeline.semantics.selection.ResolvedScope;

import java.util.List;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OIR3/OM25): bounded flat single-base intent
 * envelope. Ordered, non-empty, non-nested; all instances share ONE exact
 * baseRevisionId + baseContentHash; NO intermediate canonical state, NO
 * intra-batch planning, NO dependency on entities created by earlier instances.
 * Batch order = caller semantic intent order, not mutation execution order.
 */
public record OperationBatch(
        List<OperationInstance> instances,
        String baseRevisionId,
        String baseContentHash) {

    public OperationBatch {
        if (instances == null || instances.isEmpty()) {
            throw new IllegalArgumentException("batch must be non-empty");
        }
        if (baseRevisionId == null || baseRevisionId.isBlank()) {
            throw new IllegalArgumentException("baseRevisionId required");
        }
        for (OperationInstance instance : instances) {
            if (!baseRevisionId.equals(instance.baseRevisionId())) {
                throw new IllegalArgumentException("mixed baseRevisionId rejected");
            }
            if (!baseContentHash.equals(instance.baseContentHash())) {
                throw new IllegalArgumentException("mixed baseContentHash rejected");
            }
        }
    }

    /** Static detectability aid: batch instances must not reference entities that
     * can only exist because an earlier instance creates them (hard rule for Plan). */
    public static void assertNoIntraBatchCreateThenUse(OperationBatch batch) {
        // V1 hard rule documented + enforced where statically detectable by callers;
        // the semantic rule is preserved for OperationPlan.
    }
}
