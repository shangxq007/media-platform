package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.template.*;
import com.example.platform.render.domain.workflow.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Workflow dry-run planner — validates, orders, and summarizes workflow steps
 * without executing templates, rendering, storage, or product operations.
 *
 * <p>Internal domain model. Provider-neutral, storage-neutral.</p>
 */
public class WorkflowDryRunPlanner {

    private final WorkflowGraphValidator validator;
    private final WorkflowCycleDetector cycleDetector;
    private final WorkflowStepOrderResolver orderResolver;

    public WorkflowDryRunPlanner() {
        this.validator = new WorkflowGraphValidator();
        this.cycleDetector = new WorkflowCycleDetector();
        this.orderResolver = new WorkflowStepOrderResolver();
    }

    /**
     * Plan a workflow dry-run.
     */
    public WorkflowDryRunPlan plan(WorkflowDefinition definition) {
        if (definition == null) {
            return invalidPlan(null, null, List.of(
                    new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.BLOCKING,
                            WorkflowDryRunIssueCode.MISSING_STEP, "_",
                            "Workflow definition is null", Map.of())));
        }

        List<WorkflowDryRunIssue> allIssues = new ArrayList<>();

        // 1. Validate graph
        WorkflowGraphValidationResult validation = validator.validate(definition);
        allIssues.addAll(validation.issues());

        // 2. Detect cycles
        List<WorkflowDryRunIssue> cycleIssues = cycleDetector.detectCycle(definition);
        allIssues.addAll(cycleIssues);

        // 3. Resolve order
        List<String> orderedStepIds = orderResolver.resolveOrder(definition);
        if (orderedStepIds.isEmpty() && !definition.steps().isEmpty()) {
            // Cycle already reported, but ensure we have a blocking issue
            if (cycleIssues.isEmpty()) {
                allIssues.add(new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.BLOCKING,
                        WorkflowDryRunIssueCode.CYCLE_DETECTED, "_",
                        "Could not resolve step order", Map.of()));
            }
        }

        boolean valid = allIssues.stream().noneMatch(WorkflowDryRunIssue::isBlocking);

        if (!valid) {
            return invalidPlan(definition.id(), definition.version(), allIssues);
        }

        // 4. Build dry-run steps in topological order
        Map<String, WorkflowStep> stepMap = definition.steps().stream()
                .collect(Collectors.toMap(s -> s.id().value(), s -> s, (a, b) -> a, LinkedHashMap::new));

        List<WorkflowDryRunStep> dryRunSteps = new ArrayList<>();
        int order = 0;
        for (String stepId : orderedStepIds) {
            WorkflowStep step = stepMap.get(stepId);
            if (step == null) continue;

            WorkflowTemplateStepDryRunSummary templateSummary = null;
            List<WorkflowDryRunIssue> stepIssues = new ArrayList<>();
            WorkflowDryRunStepStatus status = WorkflowDryRunStepStatus.READY;

            if (step.isApplyTemplate()) {
                templateSummary = buildTemplateSummary(step);
                if (step.templateApplicationSpec() == null) {
                    status = WorkflowDryRunStepStatus.VALIDATION_FAILED;
                    stepIssues.add(new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.ERROR,
                            WorkflowDryRunIssueCode.APPLY_TEMPLATE_MISSING_SPEC,
                            "steps." + stepId, "Missing template application spec", Map.of()));
                }
            }

            // Check for unsupported step types
            if (!isSupportedStepType(step.type())) {
                status = WorkflowDryRunStepStatus.NOT_IMPLEMENTED;
                stepIssues.add(new WorkflowDryRunIssue(WorkflowDryRunIssueSeverity.INFO,
                        WorkflowDryRunIssueCode.EXECUTION_NOT_IMPLEMENTED,
                        "steps." + stepId,
                        "Step type not yet implemented: " + step.type(), Map.of()));
            }

            dryRunSteps.add(new WorkflowDryRunStep(
                    step.id(), step.type(), order++, status,
                    templateSummary, stepIssues, Map.of()));
        }

        return new WorkflowDryRunPlan(
                new WorkflowDryRunPlanId("dryrun-" + definition.id().value()),
                definition.id(), definition.version(),
                dryRunSteps, allIssues, true, Map.of());
    }

    private WorkflowTemplateStepDryRunSummary buildTemplateSummary(WorkflowStep step) {
        WorkflowTemplateApplicationStepSpec spec = step.templateApplicationSpec();
        if (spec == null) return null;

        TemplateApplicationRequest request = spec.templateApplicationRequest();
        int targetCount = request != null && request.targets() != null ? request.targets().size() : 0;
        int paramCount = request != null && request.parameters() != null ? request.parameters().size() : 0;

        // Determine template kind
        String kind = "ATOMIC";
        if (spec.templateId() != null) {
            String tid = spec.templateId().value();
            if (tid.contains("composite") || tid.contains("social") || tid.contains("ecommerce")) {
                kind = "COMPOSITE_CANDIDATE";
            }
        }

        return new WorkflowTemplateStepDryRunSummary(
                spec.templateId(), spec.templateVersion(),
                kind, targetCount, paramCount,
                "COMPOSITE_CANDIDATE".equals(kind), Map.of());
    }

    private boolean isSupportedStepType(WorkflowStepType type) {
        return type == WorkflowStepType.INGEST_PRODUCT
                || type == WorkflowStepType.APPLY_TEMPLATE
                || type == WorkflowStepType.COMPILE_TIMELINE
                || type == WorkflowStepType.RENDER_TIMELINE
                || type == WorkflowStepType.LOOKUP_RESULT
                || type == WorkflowStepType.DELIVER_PRODUCT
                || type == WorkflowStepType.VALIDATE_INPUT
                || type == WorkflowStepType.NORMALIZE_TIMELINE
                || type == WorkflowStepType.REGISTER_PRODUCT
                || type == WorkflowStepType.NOTIFY;
    }

    private WorkflowDryRunPlan invalidPlan(
            WorkflowDefinitionId workflowId,
            com.example.platform.render.domain.workflow.WorkflowVersion version,
            List<WorkflowDryRunIssue> issues) {
        return new WorkflowDryRunPlan(
                new WorkflowDryRunPlanId("dryrun-invalid"),
                workflowId, version,
                List.of(), issues, false, Map.of());
    }
}
