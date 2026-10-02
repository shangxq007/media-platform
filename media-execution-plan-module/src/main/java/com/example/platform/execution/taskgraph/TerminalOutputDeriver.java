package com.example.platform.execution.taskgraph;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * TERMINAL-OUTPUT-DERIVER-001: derives the POST_EXECUTION boundary actions that make a graph's
 * terminal outputs authoritative.
 *
 * <p>The #22 binding chain only derived boundary actions for <em>inter-unit</em> Artifact
 * materialization (producer side of a dependency edge), so a unit whose output nothing consumes —
 * including every unit of a single-unit graph — had no post-execution semantics at all and
 * {@link ExecutableTask#authoritativeOutputIds()} stayed empty. This helper closes that gap, purely
 * plan-driven and stateless:
 *
 * <ul>
 *   <li>a unit is <em>terminal</em> when no dependency edge in the plan names its logical node as the
 *       producer, i.e. nothing inside the graph consumes its output;</li>
 *   <li>for every terminal output the plan <em>already declares</em> a materialization expectation
 *       for, exactly one POST_EXECUTION action is emitted — a {@link BoundaryAction.FinalArtifactTarget}
 *       when the output declares final expectations, otherwise an
 *       {@link BoundaryAction.IntermediateArtifactTarget} for its first declared intermediate
 *       expectation;</li>
 *   <li>an output that declares no expectation gets no action: the derivation never invents a
 *       materialization requirement, so a plan that declares nothing still fails closed in the
 *       publication authority.</li>
 * </ul>
 *
 * <p>Actions are keyed by the unit's {@link ExecutionStepId} (task identity only exists once the
 * {@link ExecutableTask} is built) and numbered from {@code 0} within POST_EXECUTION; the graph's
 * execution-Artifact lowering continues from the highest existing order, so the two never collide.
 */
public final class TerminalOutputDeriver {

    private TerminalOutputDeriver() {
    }

    /**
     * Derives the terminal POST_EXECUTION actions of every unit, keyed by unit step id.
     *
     * <p>Units with no terminal output, and terminal outputs with no declared expectation, are
     * absent from the map (or map to an empty list).
     */
    public static Map<ExecutionStepId, List<BoundaryAction>> derive(PhysicalExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Set<String> consumedLogicalNodes = consumedLogicalNodes(plan);
        Map<ExecutionStepId, List<BoundaryAction>> actions = new LinkedHashMap<>();
        for (PhysicalPlanUnit unit : plan.units()) {
            List<BoundaryAction> unitActions =
                    consumedLogicalNodes.contains(unit.logicalNodeId())
                            ? List.of()
                            : terminalActionsFor(unit);
            actions.put(unit.stepId(), unitActions);
        }
        return Map.copyOf(actions);
    }

    /**
     * Terminal POST_EXECUTION actions for one unit: one action per terminal output that declares a
     * materialization expectation, in declaration order.
     */
    public static List<BoundaryAction> terminalActionsFor(PhysicalPlanUnit unit) {
        Objects.requireNonNull(unit, "unit");
        List<BoundaryAction> actions = new ArrayList<>();
        int order = 0;
        for (OutputDeclaration output : unit.typedOutputs()) {
            BoundaryAction.Target target = terminalTarget(unit, output);
            if (target == null) {
                continue;
            }
            actions.add(new BoundaryAction(BoundaryAction.Phase.POST_EXECUTION, order++, target));
        }
        return List.copyOf(actions);
    }

    private static BoundaryAction.Target terminalTarget(
            PhysicalPlanUnit unit, OutputDeclaration output) {
        if (!output.finalArtifactExpectations().isEmpty()) {
            return new BoundaryAction.FinalArtifactTarget(
                    unit.stepId(), output, output.finalArtifactExpectations().getFirst());
        }
        if (!output.intermediateArtifactExpectations().isEmpty()) {
            return new BoundaryAction.IntermediateArtifactTarget(
                    unit.stepId(), output, output.intermediateArtifactExpectations().getFirst());
        }
        return null;
    }

    /** Logical node ids that some other unit consumes through a plan dependency edge. */
    private static Set<String> consumedLogicalNodes(PhysicalExecutionPlan plan) {
        Set<String> consumed = new TreeSet<>();
        for (PhysicalPlanUnit unit : plan.units()) {
            for (LogicalDependencyEdge dependency : unit.typedDependencies()) {
                consumed.add(dependency.producerLogicalNodeId());
            }
        }
        return consumed;
    }
}
