package com.example.platform.shared.capability.execution;

/**
 * Interface for executing system actions.
 *
 * <p>SystemActionExecutor defines the contract for executing platform actions.
 * Implementations may validate requests, support dry-run, and return results.</p>
 *
 * <p><strong>Contract only:</strong> This defines the executor interface.
 * Runtime execution is not fully implemented.</p>
 */
public interface SystemActionExecutor {

    /**
     * Execute a system action.
     *
     * @param context the execution context
     * @param request the execution request
     * @return the execution result
     */
    SystemActionExecutionResult execute(SystemActionExecutionContext context, SystemActionExecutionRequest request);
}
