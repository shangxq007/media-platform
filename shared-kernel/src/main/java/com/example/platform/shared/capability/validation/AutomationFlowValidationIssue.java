package com.example.platform.shared.capability.validation;

/**
 * Represents a single validation issue found during automation flow validation.
 *
 * <p><strong>Contract only:</strong> This defines the validation issue shape.
 * Runtime execution is not implemented.</p>
 */
public record AutomationFlowValidationIssue(
    AutomationFlowValidationCode code,
    AutomationFlowValidationSeverity severity,
    String message,
    String nodeId,
    String path
) {
    public AutomationFlowValidationIssue {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    /**
     * Create an ERROR issue.
     */
    public static AutomationFlowValidationIssue error(AutomationFlowValidationCode code, String message) {
        return new AutomationFlowValidationIssue(code, AutomationFlowValidationSeverity.ERROR, message, null, null);
    }

    /**
     * Create an ERROR issue with node context.
     */
    public static AutomationFlowValidationIssue error(AutomationFlowValidationCode code, String message, String nodeId) {
        return new AutomationFlowValidationIssue(code, AutomationFlowValidationSeverity.ERROR, message, nodeId, null);
    }

    /**
     * Create a WARNING issue.
     */
    public static AutomationFlowValidationIssue warning(AutomationFlowValidationCode code, String message) {
        return new AutomationFlowValidationIssue(code, AutomationFlowValidationSeverity.WARNING, message, null, null);
    }

    /**
     * Create a WARNING issue with node context.
     */
    public static AutomationFlowValidationIssue warning(AutomationFlowValidationCode code, String message, String nodeId) {
        return new AutomationFlowValidationIssue(code, AutomationFlowValidationSeverity.WARNING, message, nodeId, null);
    }

    /**
     * Create an INFO issue.
     */
    public static AutomationFlowValidationIssue info(AutomationFlowValidationCode code, String message) {
        return new AutomationFlowValidationIssue(code, AutomationFlowValidationSeverity.INFO, message, null, null);
    }
}
