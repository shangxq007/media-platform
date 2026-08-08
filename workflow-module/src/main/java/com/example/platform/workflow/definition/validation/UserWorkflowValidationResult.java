package com.example.platform.workflow.definition.validation;

import java.util.List;

/**
 * Deterministic validation result: valid iff there are no ERROR issues.
 */
public record UserWorkflowValidationResult(boolean valid, List<UserWorkflowValidationIssue> issues) {

    public UserWorkflowValidationResult {
        issues = List.copyOf(issues == null ? List.of() : issues);
    }

    public List<UserWorkflowValidationIssue> blockingIssues() {
        return issues.stream()
                .filter(i -> i.severity() == UserWorkflowValidationIssue.Severity.ERROR)
                .toList();
    }
}
