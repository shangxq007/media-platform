package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.workflow.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates workflow graph structure.
 * Internal domain model. Does not execute steps.
 */
public class WorkflowGraphValidator {

    public WorkflowGraphValidationResult validate(WorkflowDefinition definition) {
        if (definition == null) {
            return WorkflowGraphValidationResult.failure(List.of(
                    new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.BLOCKING,
                            WorkflowDryRunIssueCode.MISSING_STEP, "_", "Workflow definition is null", Map.of())));
        }

        List<WorkflowDryRunIssue> issues = new ArrayList<>();

        // Check duplicate step IDs
        Set<String> seenIds = new HashSet<>();
        for (WorkflowStep step : definition.steps()) {
            if (!seenIds.add(step.id().value())) {
                issues.add(new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.BLOCKING,
                        WorkflowDryRunIssueCode.DUPLICATE_STEP_ID,
                        "steps." + step.id().value(),
                        "Duplicate step ID: " + step.id().value(), Map.of()));
            }
        }

        // Check dependencies reference existing steps
        Set<String> stepIds = definition.steps().stream()
                .map(s -> s.id().value()).collect(Collectors.toSet());
        for (WorkflowStep step : definition.steps()) {
            if (step.dependencies() != null) {
                for (WorkflowStepDependency dep : step.dependencies()) {
                    if (!stepIds.contains(dep.dependsOnStepId().value())) {
                        issues.add(new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.BLOCKING,
                                WorkflowDryRunIssueCode.UNKNOWN_DEPENDENCY,
                                "steps." + step.id() + ".dependencies",
                                "Unknown dependency: " + dep.dependsOnStepId().value(), Map.of()));
                    }
                }
            }
        }

        // Check APPLY_TEMPLATE steps have spec
        for (WorkflowStep step : definition.steps()) {
            if (step.isApplyTemplate() && step.templateApplicationSpec() == null) {
                issues.add(new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.ERROR,
                        WorkflowDryRunIssueCode.APPLY_TEMPLATE_MISSING_SPEC,
                        "steps." + step.id(),
                        "APPLY_TEMPLATE step missing template application spec", Map.of()));
            }
        }

        return issues.isEmpty()
                ? WorkflowGraphValidationResult.success()
                : WorkflowGraphValidationResult.failure(issues);
    }
}
