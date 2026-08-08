package com.example.platform.workflow.definition.validation;

/**
 * A single validation issue with a stable code, message and severity.
 */
public record UserWorkflowValidationIssue(
        UserWorkflowValidationCode issueCode,
        String message,
        Severity severity) {

    public enum Severity {
        ERROR,
        WARNING
    }
}
