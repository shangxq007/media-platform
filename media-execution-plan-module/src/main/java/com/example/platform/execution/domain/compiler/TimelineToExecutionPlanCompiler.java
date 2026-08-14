package com.example.platform.execution.domain;
import com.example.platform.shared.time.MediaTime;

import com.example.platform.execution.domain.operation.MediaOperation;

import java.util.List;

/**
 * Minimal backend-neutral compiler from Timeline to Execution Plan.
 *
 * <p>This is a STUB interface — the full implementation that generates execution steps
 * from timeline semantics (Rational MediaTime, playback rate, transition duration policy,
 * effect temporal impact, automation ordering, audio tail policy, source handles) is
 * deferred to a future iteration.
 *
 * <p>The interface is FROZEN — do not implement. Only the minimal contract is defined here
 * to allow the domain types to reference it.
 */
public sealed interface TimelineToExecutionPlanCompiler permits
        TimelineToExecutionPlanCompiler.Stub {

    /**
     * Compiles a Timeline revision into an execution plan.
     *
     * @param tenantId the tenant
     * @param productId the product
     * @param timelineRevisionId the timeline revision ID
     * @param timelineRevisionDigest the timeline content digest
     * @param inputs the resolved artifact inputs
     * @param outputs the requested output declarations
     * @return the compiled execution plan
     * @throws UnsupportedOperationException always — not implemented
     */
    MediaExecutionPlan compile(
            String tenantId,
            String productId,
            String timelineRevisionId,
            String timelineRevisionDigest,
            List<ExecutionInputBinding> inputs,
            List<ExecutionOutputDeclaration> outputs);

    /**
     * Stub implementation that always throws UnsupportedOperationException.
     */
    record Stub() implements TimelineToExecutionPlanCompiler {
        @Override
        public MediaExecutionPlan compile(
                String tenantId,
                String productId,
                String timelineRevisionId,
                String timelineRevisionDigest,
                List<ExecutionInputBinding> inputs,
                List<ExecutionOutputDeclaration> outputs) {
            throw new UnsupportedOperationException(
                    "TimelineToExecutionPlanCompiler is not yet implemented");
        }
    }
}
