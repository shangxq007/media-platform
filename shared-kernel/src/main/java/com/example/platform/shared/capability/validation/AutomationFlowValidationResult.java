package com.example.platform.shared.capability.validation;

import java.util.List;

/**
 * Result of automation flow validation.
 *
 * <p><strong>Contract only:</strong> This defines the validation result shape.
 * Runtime execution is not implemented.</p>
 */
public record AutomationFlowValidationResult(
    boolean valid,
    List<AutomationFlowValidationIssue> issues
) {
    public AutomationFlowValidationResult {
        issues = issues != null ? List.copyOf(issues) : List.of();
    }

    /**
     * Create a valid result with no issues.
     */
    public static AutomationFlowValidationResult success() {
        return new AutomationFlowValidationResult(true, List.of());
    }

    /**
     * Create a valid result with warnings/info issues.
     */
    public static AutomationFlowValidationResult success(List<AutomationFlowValidationIssue> issues) {
        return new AutomationFlowValidationResult(true, issues);
    }

    /**
     * Create an invalid result with issues.
     */
    public static AutomationFlowValidationResult failure(List<AutomationFlowValidationIssue> issues) {
        return new AutomationFlowValidationResult(false, issues);
    }

    /**
     * Create an invalid result with a single error.
     */
    public static AutomationFlowValidationResult failure(AutomationFlowValidationIssue issue) {
        return new AutomationFlowValidationResult(false, List.of(issue));
    }

    /**
     * Check if there are any errors.
     */
    public boolean hasErrors() {
        return issues.stream()
            .anyMatch(i -> i.severity() == AutomationFlowValidationSeverity.ERROR);
    }

    /**
     * Check if there are any warnings.
     */
    public boolean hasWarnings() {
        return issues.stream()
            .anyMatch(i -> i.severity() == AutomationFlowValidationSeverity.WARNING);
    }

    /**
     * Get only error issues.
     */
    public List<AutomationFlowValidationIssue> errors() {
        return issues.stream()
            .filter(i -> i.severity() == AutomationFlowValidationSeverity.ERROR)
            .toList();
    }

    /**
     * Get only warning issues.
     */
    public List<AutomationFlowValidationIssue> warnings() {
        return issues.stream()
            .filter(i -> i.severity() == AutomationFlowValidationSeverity.WARNING)
            .toList();
    }
}
