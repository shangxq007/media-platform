package com.example.platform.workflow.temporal.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activities of the per-graph media task workflow (P2-5b-1-R2 skeleton).
 *
 * <p>All side effects live here: the activity rehydrates the bound graph from the canonical store,
 * re-derives it and verifies its digest. The implementation lives in {@code platform-app}
 * ({@code com.example.platform.runtime.mediatask.MediaTaskActivity}) and is currently
 * <b>test-wired only</b> — no production Spring registration until P2-6.
 */
@ActivityInterface
public interface MediaTaskActivities {

    /**
     * Loads the bound inputs, re-derives the executable task graph and verifies the digest.
     *
     * @return stable identity/digest summary of the prepared graph (never the graph itself)
     */
    @ActivityMethod
    PreparedTaskRef prepareTask(BoundGraphReference reference, String tenantId);

    /**
     * Whole-graph execution call — <b>placeholder only (P2-5b-2)</b>.
     *
     * <p>Not implemented in P2-5b-1-R2: constructing {@code RuntimeClosedLoopRequest.taskExecutions}
     * requires the platform attempt/generation mechanism (P2-5b-2) and the catalog-derived runtime
     * binding map. The signature is declared so the workflow skeleton and the P2-5b-2 inputs are
     * explicit; the implementation fails closed until then.
     */
    @ActivityMethod
    MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId);
}
