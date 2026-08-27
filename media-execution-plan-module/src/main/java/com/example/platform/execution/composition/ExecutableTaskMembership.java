package com.example.platform.execution.composition;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphAlgorithms;
import com.example.platform.graph.api.GraphViews;
import com.example.platform.graph.result.TopologicalOrderResult;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One immutable membership of one canonical #21 {@link PhysicalPlanUnit}.
 *
 * <p>The upstream unit remains the semantic authority. IO, dependencies and
 * materialization markers are exposed as typed references to its canonical
 * fields; they are not copied into a Phase 5 shadow model.
 */
public final class ExecutableTaskMembership {

    public static final PhysicalPlanUnitMembershipCardinality
            PHYSICAL_PLAN_UNIT_MEMBERSHIP_CARDINALITY =
                    PhysicalPlanUnitMembershipCardinality.EXACTLY_ONE;

    private final PhysicalPlanUnit physicalPlanUnit;
    private final int canonicalPosition;
    private final FailureAttribution.MemberAttribution failureAttributionMapping;

    private ExecutableTaskMembership(PhysicalPlanUnit physicalPlanUnit, int canonicalPosition) {
        this.physicalPlanUnit = Objects.requireNonNull(physicalPlanUnit, "physicalPlanUnit");
        if (canonicalPosition < 0) {
            throw new IllegalArgumentException("canonicalPosition must be non-negative");
        }
        this.canonicalPosition = canonicalPosition;
        this.failureAttributionMapping = new FailureAttribution.MemberAttribution(physicalPlanUnit);
    }

    public PhysicalPlanUnit physicalPlanUnit() {
        return physicalPlanUnit;
    }

    public ExecutionStepId physicalPlanUnitId() {
        return physicalPlanUnit.stepId();
    }

    public int canonicalPosition() {
        return canonicalPosition;
    }

    public List<InputBinding> inputMapping() {
        return physicalPlanUnit.typedInputs();
    }

    public List<OutputDeclaration> outputMapping() {
        return physicalPlanUnit.typedOutputs();
    }

    public List<LogicalDependencyEdge> dependencyMapping() {
        return physicalPlanUnit.typedDependencies();
    }

    public List<RenderMaterializationRequirement> mandatoryMaterializationMarkers() {
        return physicalPlanUnit.typedOutputs().stream()
                .flatMap(output -> output.materializationRequirements().stream())
                .toList();
    }

    public FailureAttribution.MemberAttribution failureAttributionMapping() {
        return failureAttributionMapping;
    }

    /** Builds one-or-more memberships in dependency-preserving canonical order. */
    public static List<ExecutableTaskMembership> canonicalForUnits(
            Collection<PhysicalPlanUnit> units) {
        Objects.requireNonNull(units, "units");
        if (units.isEmpty()) {
            throw new IllegalArgumentException("every membership group requires ONE_OR_MORE units");
        }

        Map<ExecutionStepId, PhysicalPlanUnit> byStep = new HashMap<>();
        Map<String, PhysicalPlanUnit> byLogicalNode = new HashMap<>();
        for (PhysicalPlanUnit unit : units) {
            Objects.requireNonNull(unit, "units element");
            if (byStep.putIfAbsent(unit.stepId(), unit) != null) {
                throw new IllegalArgumentException(
                        "duplicate physical plan unit membership: " + unit.stepId().value());
            }
            if (byLogicalNode.putIfAbsent(unit.logicalNodeId(), unit) != null) {
                throw new IllegalArgumentException(
                        "duplicate logical node membership: " + unit.logicalNodeId());
            }
        }

        Map<String, Set<String>> successors = new HashMap<>();
        byStep.keySet().forEach(step -> successors.put(step.value(), new HashSet<>()));

        for (PhysicalPlanUnit consumer : byStep.values()) {
            for (InputBinding input : consumer.typedInputs()) {
                if (input.producerStepId() != null && byStep.containsKey(input.producerStepId())) {
                    addEdge(input.producerStepId(), consumer.stepId(), successors);
                }
            }
            for (LogicalDependencyEdge dependency : consumer.typedDependencies()) {
                PhysicalPlanUnit producer = byLogicalNode.get(dependency.producerLogicalNodeId());
                PhysicalPlanUnit dependent = byLogicalNode.get(dependency.consumerLogicalNodeId());
                if (producer != null && dependent != null) {
                    addEdge(producer.stepId(), dependent.stepId(), successors);
                }
            }
        }

        DirectedGraphView<String> topology = GraphViews.directedFromAdjacency(successors);
        TopologicalOrderResult<String> topologyOrder =
                GraphAlgorithms.topologicalOrder(topology, Comparator.naturalOrder());
        if (topologyOrder instanceof TopologicalOrderResult.CycleDetected<String>) {
            throw new IllegalArgumentException("membership dependencies must be acyclic");
        }
        Map<String, PhysicalPlanUnit> byStepValue = new HashMap<>();
        byStep.values().forEach(unit -> byStepValue.put(unit.stepId().value(), unit));
        List<PhysicalPlanUnit> ordered = topologyOrder.order().stream()
                .map(byStepValue::get)
                .toList();

        List<ExecutableTaskMembership> result = new ArrayList<>(ordered.size());
        for (int position = 0; position < ordered.size(); position++) {
            result.add(new ExecutableTaskMembership(ordered.get(position), position));
        }
        return List.copyOf(result);
    }

    /**
     * Enforces LAW_R22_025 against a complete canonical #21 physical plan.
     * Missing, duplicate, foreign or semantically altered unit references fail.
     */
    public static List<ExecutableTaskMembership> validateExactCoverage(
            PhysicalExecutionPlan physicalPlan,
            Collection<ExecutableTaskMembership> memberships) {
        Objects.requireNonNull(physicalPlan, "physicalPlan");
        Objects.requireNonNull(memberships, "memberships");

        Map<ExecutionStepId, PhysicalPlanUnit> expected = new HashMap<>();
        for (PhysicalPlanUnit unit : physicalPlan.units()) {
            if (expected.putIfAbsent(unit.stepId(), unit) != null) {
                throw new IllegalArgumentException(
                        "physical plan contains duplicate unit identity: " + unit.stepId().value());
            }
        }
        Map<ExecutionStepId, ExecutableTaskMembership> actual = new HashMap<>();
        for (ExecutableTaskMembership membership : memberships) {
            Objects.requireNonNull(membership, "memberships element");
            ExecutionStepId stepId = membership.physicalPlanUnitId();
            if (actual.putIfAbsent(stepId, membership) != null) {
                throw new IllegalArgumentException(
                        "duplicate physical plan unit membership: " + stepId.value());
            }
            PhysicalPlanUnit canonical = expected.get(stepId);
            if (canonical == null || !canonical.equals(membership.physicalPlanUnit())) {
                throw new IllegalArgumentException(
                        "membership must reference the canonical physical plan unit: " + stepId.value());
            }
        }
        List<String> missing = expected.keySet().stream()
                .filter(step -> !actual.containsKey(step))
                .map(ExecutionStepId::value)
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("physical plan units without membership: " + missing);
        }
        if (actual.size() != expected.size()) {
            throw new IllegalArgumentException("membership coverage does not match physical plan");
        }

        List<ExecutableTaskMembership> canonical = new ArrayList<>(memberships);
        canonical.sort(Comparator.comparingInt(ExecutableTaskMembership::canonicalPosition)
                .thenComparing(member -> member.physicalPlanUnitId().value()));
        return List.copyOf(canonical);
    }

    private static void addEdge(
            ExecutionStepId producer,
            ExecutionStepId consumer,
            Map<String, Set<String>> successors) {
        if (producer.equals(consumer)) {
            throw new IllegalArgumentException("membership cannot depend on itself: " + producer.value());
        }
        successors.get(producer.value()).add(consumer.value());
    }
}
