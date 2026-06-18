package com.example.platform.shared.capability.execution;

import com.example.platform.shared.capability.registry.SystemActionRegistry;

import java.util.Objects;

/**
 * Validating executor for system actions.
 *
 * <p>ValidatingSystemActionExecutor validates action requests and supports dry-run.
 * For non-dry-run requests, it returns NOT_IMPLEMENTED for all actions.</p>
 *
 * <p><strong>Contract only:</strong> This is a skeleton implementation.
 * Runtime execution is not implemented.</p>
 */
public class ValidatingSystemActionExecutor implements SystemActionExecutor {

    private final SystemActionRegistry actionRegistry;

    /**
     * Create a validating executor with the given registry.
     *
     * @param actionRegistry the system action registry
     */
    public ValidatingSystemActionExecutor(SystemActionRegistry actionRegistry) {
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry must not be null");
    }

    @Override
    public SystemActionExecutionResult execute(SystemActionExecutionContext context, SystemActionExecutionRequest request) {
        // Validate context
        if (context == null) {
            return SystemActionExecutionResult.validationFailed(null, "CONTEXT_NULL");
        }

        // Validate request
        if (request == null) {
            return SystemActionExecutionResult.validationFailed(null, "REQUEST_NULL");
        }

        // Validate action key
        String actionKey = request.actionKey();
        if (actionKey == null || actionKey.isBlank()) {
            return SystemActionExecutionResult.validationFailed(null, "ACTION_KEY_MISSING");
        }

        // Check if action exists in registry
        boolean actionExists = actionRegistry.findByKey(actionKey).isPresent();
        if (!actionExists) {
            return SystemActionExecutionResult.validationFailed(actionKey, "ACTION_NOT_FOUND");
        }

        // Handle dry-run
        if (context.dryRun()) {
            return SystemActionExecutionResult.dryRunSucceeded(actionKey);
        }

        // For non-dry-run, return NOT_IMPLEMENTED
        return SystemActionExecutionResult.notImplemented(actionKey);
    }
}
