package com.example.platform.execution.domain;

import com.example.platform.execution.domain.operation.MediaOperation;
import com.example.platform.execution.domain.projection.MediaExecutionGraphProjection;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.result.CycleDetectionResult;
import com.example.platform.graph.result.TopologicalOrderResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates execution plans and dependencies for correctness.
 *
 * <p>Performs O(V+E+I+O) validation:
 * <ul>
 *   <li>Cycle detection via topological sort (Kahn's algorithm) — delegated to graph kernel</li>
 *   <li>Duplicate step detection</li>
 *   <li>Duplicate dependency detection</li>
 *   <li>Self-dependency detection</li>
 *   <li>Orphan output detection (outputs with no producer)</li>
 *   <li>Missing producer detection (output references non-existent step)</li>
 *   <li>Output conflict detection (multiple steps produce same output)</li>
 * </ul>
 */
public final class MediaExecutionPlanValidator {

    private MediaExecutionPlanValidator() {
    }

    /**
     * Validates a complete execution plan.
     *
     * @throws ExecutionPlanDomainException if validation fails
     */
    public static void validate(MediaExecutionPlan plan) {
        validateSteps(plan);
        validateDependencies(plan);
        validateCycles(plan);
        validateOutputs(plan);
    }

    /**
     * Validates that all steps have unique IDs and valid operations.
     */
    public static void validateSteps(MediaExecutionPlan plan) {
        Set<ExecutionStepId> seen = new HashSet<>();
        for (MediaExecutionStep step : plan.steps()) {
            if (!seen.add(step.stepId())) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_DUPLICATE_STEP)
                                .planId(plan.planId().value())
                                .stepId(step.stepId().value())
                                .detail("Duplicate step ID: " + step.stepId().value())
                                .build());
            }
            validateStepInputs(step, plan);
        }
    }

    /**
     * Validates that step input references exist in the plan's inputs.
     */
    public static void validateStepInputs(MediaExecutionStep step, MediaExecutionPlan plan) {
        Set<ExecutionInputId> planInputIds = plan.inputs().stream()
                .map(ExecutionInputBinding::inputId)
                .collect(Collectors.toSet());
        for (ExecutionInputId ref : step.inputReferences()) {
            if (!planInputIds.contains(ref)) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_INPUT_NOT_FOUND)
                                .planId(plan.planId().value())
                                .stepId(step.stepId().value())
                                .inputId(ref.value())
                                .detail("Step references unknown input: " + ref.value())
                                .build());
            }
        }
    }

    /**
     * Validates dependency edges for uniqueness and self-dependency.
     */
    public static void validateDependencies(MediaExecutionPlan plan) {
        Set<ExecutionEdgeId> seenEdges = new HashSet<>();
        Set<String> seenPairs = new HashSet<>();
        Set<ExecutionStepId> stepIds = plan.steps().stream()
                .map(MediaExecutionStep::stepId)
                .collect(Collectors.toSet());

        for (ExecutionDependency edge : plan.edges()) {
            // Check duplicate edge ID
            if (!seenEdges.add(edge.edgeId())) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_DUPLICATE_DEPENDENCY)
                                .planId(plan.planId().value())
                                .detail("Duplicate edge ID: " + edge.edgeId().value())
                                .build());
            }

            // Check duplicate (from,to) pair
            String pair = edge.fromStepId().value() + "->" + edge.toStepId().value();
            if (!seenPairs.add(pair)) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_DUPLICATE_DEPENDENCY)
                                .planId(plan.planId().value())
                                .stepId(edge.fromStepId().value())
                                .detail("Duplicate dependency: " + pair)
                                .build());
            }

            // Check self-dependency
            if (edge.fromStepId().equals(edge.toStepId())) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_SELF_DEPENDENCY)
                                .planId(plan.planId().value())
                                .stepId(edge.fromStepId().value())
                                .detail("Self-dependency: " + edge.fromStepId().value())
                                .build());
            }

            // Check that referenced steps exist
            if (!stepIds.contains(edge.fromStepId()) || !stepIds.contains(edge.toStepId())) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_STEP_NOT_FOUND)
                                .planId(plan.planId().value())
                                .detail("Edge references unknown step: " + pair)
                                .build());
            }
        }
    }

    /**
     * Detects cycles using the platform graph kernel.
     * O(V+E) complexity.
     *
     * @throws ExecutionPlanDomainException if a cycle is detected
     */
    public static void validateCycles(MediaExecutionPlan plan) {
        DirectedGraphView<ExecutionStepId> graph = MediaExecutionGraphProjection.fromPlan(plan).graphView();
        CycleDetectionResult<ExecutionStepId> result = com.example.platform.graph.api.GraphAlgorithms.detectCycles(graph);

        if (result.hasCycle()) {
            String cycleNodes = result instanceof CycleDetectionResult.Cyclic<ExecutionStepId> cyclic
                    ? cyclic.cycleNodes().stream()
                            .map(ExecutionStepId::value)
                            .collect(Collectors.joining(", "))
                    : "unknown";
            throw new ExecutionPlanDomainException(
                    ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_CYCLE)
                            .planId(plan.planId().value())
                            .detail("Cycle detected involving steps: " + cycleNodes)
                            .build());
        }
    }

    /**
     * Validates output declarations: no orphans, no conflicts.
     */
    public static void validateOutputs(MediaExecutionPlan plan) {
        Set<ExecutionStepId> stepIds = plan.steps().stream()
                .map(MediaExecutionStep::stepId)
                .collect(Collectors.toSet());

        Set<ExecutionOutputId> producedOutputs = new HashSet<>();
        Set<ExecutionOutputId> declaredOutputs = new HashSet<>();

        for (ExecutionOutputDeclaration output : plan.outputs()) {
            // Check duplicate output ID
            if (!declaredOutputs.add(output.outputId())) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_OUTPUT_CONFLICT)
                                .planId(plan.planId().value())
                                .outputId(output.outputId().value())
                                .detail("Duplicate output declaration: " + output.outputId().value())
                                .build());
            }

            // Check that producing step exists
            if (!stepIds.contains(output.producingStepId())) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_MISSING_PRODUCER)
                                .planId(plan.planId().value())
                                .outputId(output.outputId().value())
                                .stepId(output.producingStepId().value())
                                .detail("Output references unknown producing step: " + output.producingStepId().value())
                                .build());
            }

            // Track produced outputs
            if (!producedOutputs.add(output.outputId())) {
                throw new ExecutionPlanDomainException(
                        ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_OUTPUT_CONFLICT)
                                .planId(plan.planId().value())
                                .outputId(output.outputId().value())
                                .detail("Multiple steps produce output: " + output.outputId().value())
                                .build());
            }
        }

        // Check that all step output references are declared
        Set<ExecutionOutputId> declaredOutputIds = plan.outputs().stream()
                .map(ExecutionOutputDeclaration::outputId)
                .collect(Collectors.toSet());
        for (MediaExecutionStep step : plan.steps()) {
            for (ExecutionOutputId outRef : step.outputReferences()) {
                if (!declaredOutputIds.contains(outRef)) {
                    throw new ExecutionPlanDomainException(
                            ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_ORPHAN_OUTPUT)
                                    .planId(plan.planId().value())
                                    .stepId(step.stepId().value())
                                    .outputId(outRef.value())
                                    .detail("Step produces undeclared output: " + outRef.value())
                                    .build());
                }
            }
        }
    }

    /**
     * Performs topological sort and returns the deterministic ordering.
     * Same semantic DAG + different insertion order → same topological order.
     *
     * @return ordered list of step IDs in topological order
     */
    public static List<ExecutionStepId> topologicalOrder(MediaExecutionPlan plan) {
        DirectedGraphView<ExecutionStepId> graph = MediaExecutionGraphProjection.fromPlan(plan).graphView();
        TopologicalOrderResult<ExecutionStepId> result = com.example.platform.graph.api.GraphAlgorithms.topologicalOrder(graph);

        if (result instanceof TopologicalOrderResult.Ordered<ExecutionStepId> ordered) {
            return ordered.order();
        } else if (result instanceof TopologicalOrderResult.CycleDetected<ExecutionStepId> cycle) {
            throw new ExecutionPlanDomainException(
                    ExecutionPlanErrorCode.Error.builder(ExecutionPlanErrorCode.Code.EXECUTION_PLAN_CYCLE)
                            .planId(plan.planId().value())
                            .detail("Cycle detected involving steps: " +
                                    cycle.cycleNodes().stream()
                                            .map(ExecutionStepId::value)
                                            .collect(Collectors.joining(", ")))
                            .build());
        }
        throw new IllegalStateException("Unknown topological order result type");
    }
}