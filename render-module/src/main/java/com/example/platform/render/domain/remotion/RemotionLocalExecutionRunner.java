package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Remotion local execution runner — skeleton only, disabled by default.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: All requests are refused. No external process is started.
 * No Node/npm/npx/remotion invocation. No StorageRuntime mutation.
 * No ProductRuntime mutation. No output file produced.</p>
 *
 * <p>This runner is NOT wired into LocalExecutionPlanRunner.
 * FFmpeg remains the only executable provider.</p>
 */
public class RemotionLocalExecutionRunner {

    private final RemotionExecutionPolicyEvaluator evaluator;

    public RemotionLocalExecutionRunner() {
        this.evaluator = new RemotionExecutionPolicyEvaluator();
    }

    /**
     * Execute a Remotion local execution request.
     *
     * <p>v0: Always returns NOT_IMPLEMENTED or BLOCKED status.
     * Never starts a process. Never produces output.</p>
     *
     * @param request the execution request
     * @return the execution result (always non-executed in v0)
     */
    public RemotionLocalExecutionResult execute(RemotionLocalExecutionRequest request) {
        // 1. Null request fails closed
        if (request == null) {
            return RemotionLocalExecutionResult.failedClosed("Request must not be null");
        }

        // 2. Unsupported document rejected
        if (request.documentGenerationResult() != null
                && !request.documentGenerationResult().isGenerated()
                && request.documentGenerationResult().isRejected()) {
            return RemotionLocalExecutionResult.rejectedUnsupported(
                    "Document generation rejected: " + request.documentGenerationResult().generationStatus());
        }

        // 3. Run preflight evaluation
        RemotionExecutionPreflightResult preflight = evaluator.evaluate(
                request.executionPolicy() != null
                        ? request.executionPolicy() : RemotionExecutionPolicy.disabledDefault(),
                request.sandboxPolicy() != null
                        ? request.sandboxPolicy() : RemotionSandboxPolicy.lockedDown(),
                request.providerReadiness(),
                request.commandPlan());

        // 4. Map preflight to execution result
        return mapPreflightToResult(preflight);
    }

    private RemotionLocalExecutionResult mapPreflightToResult(RemotionExecutionPreflightResult preflight) {
        return switch (preflight.status()) {
            case BLOCKED_BY_POLICY -> RemotionLocalExecutionResult.blockedByPolicy(
                    preflight.explanation());
            case BLOCKED_BY_RUNTIME -> RemotionLocalExecutionResult.blockedByRuntime(
                    preflight.explanation());
            case BLOCKED_BY_SANDBOX -> RemotionLocalExecutionResult.blockedByPreflight(
                    preflight.status(), preflight.explanation(), preflight.violations());
            case BLOCKED_BY_UNSUPPORTED_DOCUMENT -> RemotionLocalExecutionResult.rejectedUnsupported(
                    preflight.explanation());
            case BLOCKED_BY_UNSAFE_COMMAND -> RemotionLocalExecutionResult.blockedByPreflight(
                    preflight.status(), preflight.explanation(), preflight.violations());
            case READY_BUT_EXECUTION_DISABLED -> RemotionLocalExecutionResult.notImplemented(
                    "All checks passed but execution remains disabled: " + preflight.explanation());
            case NOT_IMPLEMENTED -> RemotionLocalExecutionResult.notImplemented(
                    preflight.explanation());
        };
    }
}
