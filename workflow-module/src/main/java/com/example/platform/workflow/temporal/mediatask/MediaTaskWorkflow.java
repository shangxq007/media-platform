package com.example.platform.workflow.temporal.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * P2-5b-1 durable per-graph media task workflow (architecture Option C).
 *
 * <p>Planning runs synchronously <em>outside</em> Temporal: this workflow is started only after the
 * bound graph has been persisted and addressed by a {@link BoundGraphReference}. The workflow
 * itself is deterministic and payload-disciplined — it carries stable references only (tenant,
 * render job, plan reference and canonical digests); the graph, the candidate declarations and any
 * media payload are rehydrated inside the activity from the canonical store.
 *
 * <p>Skeleton scope (P2-5b-1-R2): this interface and its implementation exist and are compiled, but
 * the workflow is <b>not registered</b> in any production worker. The whole-graph execution call
 * ({@code executeTask}) is a declared placeholder owned by P2-5b-2.
 *
 * <p>Independent of {@code RenderWorkflow} — the legacy render chain is not switched or retired.
 */
@WorkflowInterface
public interface MediaTaskWorkflow {

    /**
     * Prepares (and, from P2-5b-2, executes) the graph addressed by {@code reference}.
     *
     * @return a stable summary: digests and executable task ids only — never graph JSON, never bytes
     */
    @WorkflowMethod
    MediaTaskWorkflowResult run(BoundGraphReference reference, String tenantId);
}
