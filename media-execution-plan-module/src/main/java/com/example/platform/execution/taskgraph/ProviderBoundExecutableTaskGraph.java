package com.example.platform.execution.taskgraph;

import com.example.platform.execution.compatibility.ProviderCompatibilityGraph;
import com.example.platform.execution.compatibility.ProviderCompatibilityTransition;
import com.example.platform.execution.compatibility.ProviderCompatibilityTransitionDecision;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphAlgorithms;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable provider-bound executable task graph derived from a canonical #21 physical plan.
 * The source plan remains referenced, never rewritten or mutated.
 */
public final class ProviderBoundExecutableTaskGraph {

    public static final String GRAPH_SCHEMA_VERSION = "roadmap22.provider-bound-etg.v1";

    private final PhysicalExecutionPlan sourcePhysicalPlan;
    private final ProviderCompatibilityGraph providerCompatibilityGraph;
    private final List<ExecutableTask> tasks;
    private final List<ProviderLocalTaskDependency> providerLocalDependencies;
    private final List<ExecutableTaskDependency> taskDependencies;
    private final List<ProviderCompatibilityTransition> selectedProviderTransitions;
    private final List<MandatoryArtifactBoundary> mandatoryArtifactBoundaries;
    private final List<CrossProviderArtifactBoundary> crossProviderArtifactBoundaries;
    private final List<ExecutableTask.RequiredInputArtifactPin> requiredInputArtifactPins;
    private final int sourceDependencyCount;
    private final ExecutableTaskGraphDigest digest;

    private ProviderBoundExecutableTaskGraph(
            PhysicalExecutionPlan sourcePhysicalPlan,
            ProviderCompatibilityGraph providerCompatibilityGraph,
            List<ExecutableTask> tasks,
            List<ProviderLocalTaskDependency> providerLocalDependencies,
            List<ExecutableTaskDependency> taskDependencies,
            List<ProviderCompatibilityTransition> selectedProviderTransitions,
            List<MandatoryArtifactBoundary> mandatoryArtifactBoundaries,
            List<CrossProviderArtifactBoundary> crossProviderArtifactBoundaries,
            List<ExecutableTask.RequiredInputArtifactPin> requiredInputArtifactPins,
            int sourceDependencyCount,
            ExecutableTaskGraphDigest digest) {
        this.sourcePhysicalPlan = sourcePhysicalPlan;
        this.providerCompatibilityGraph = providerCompatibilityGraph;
        this.tasks = tasks;
        this.providerLocalDependencies = providerLocalDependencies;
        this.taskDependencies = taskDependencies;
        this.selectedProviderTransitions = selectedProviderTransitions;
        this.mandatoryArtifactBoundaries = mandatoryArtifactBoundaries;
        this.crossProviderArtifactBoundaries = crossProviderArtifactBoundaries;
        this.requiredInputArtifactPins = requiredInputArtifactPins;
        this.sourceDependencyCount = sourceDependencyCount;
        this.digest = digest;
    }

    public static ProviderBoundExecutableTaskGraph derive(
            PhysicalExecutionPlan sourcePhysicalPlan,
            ProviderCompatibilityGraph providerCompatibilityGraph,
            Collection<ExecutableTask> tasks,
            Collection<CrossProviderArtifactBoundary> crossProviderArtifactBoundaries) {
        Objects.requireNonNull(sourcePhysicalPlan, "sourcePhysicalPlan");
        Objects.requireNonNull(providerCompatibilityGraph, "providerCompatibilityGraph");
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(crossProviderArtifactBoundaries, "crossProviderArtifactBoundaries");
        if (!providerCompatibilityGraph.bindsExactSourcePlan(sourcePhysicalPlan)) {
            throw new IllegalArgumentException(
                    "ProviderCompatibilityGraph must bind the exact source plan semantics");
        }
        if (tasks.isEmpty() && !sourcePhysicalPlan.units().isEmpty()) {
            throw new IllegalArgumentException("a non-empty physical plan requires executable tasks");
        }

        List<ExecutableTask> baseTasks = canonicalTasks(tasks);
        validateEvaluatorProvenComposition(
                sourcePhysicalPlan, providerCompatibilityGraph, baseTasks);
        Coverage baseCoverage = validateCoverage(sourcePhysicalPlan, baseTasks);
        SourceTopology sourceTopology = sourceTopology(sourcePhysicalPlan);
        validateInputDependencyMappings(sourcePhysicalPlan, sourceTopology);
        List<CrossProviderArtifactBoundary> canonicalCrossProviderBoundaries =
                canonicalCrossProviderBoundaries(crossProviderArtifactBoundaries);
        validatePreAttachedCrossProviderActions(
                baseTasks, canonicalCrossProviderBoundaries);
        List<ProviderCompatibilityTransition> selectedTransitions = validateTransitionsAndBoundaries(
                providerCompatibilityGraph,
                sourceTopology,
                baseCoverage,
                canonicalCrossProviderBoundaries);
        List<ExecutableTask> canonicalTasks = lowerCrossProviderBoundaryActions(
                baseTasks, canonicalCrossProviderBoundaries);
        Coverage coverage = validateCoverage(sourcePhysicalPlan, canonicalTasks);

        List<ProviderLocalTaskDependency> internalDependencies = new ArrayList<>();
        List<ExecutableTaskDependency> externalDependencies = new ArrayList<>();
        for (LogicalDependencyEdge dependency : sourceTopology.dependencies()) {
            PhysicalPlanUnit producer = sourceTopology.byLogicalNode()
                    .get(dependency.producerLogicalNodeId());
            PhysicalPlanUnit consumer = sourceTopology.byLogicalNode()
                    .get(dependency.consumerLogicalNodeId());
            ExecutableTask producerTask = coverage.taskByUnit().get(producer.stepId());
            ExecutableTask consumerTask = coverage.taskByUnit().get(consumer.stepId());
            if (producerTask.id().equals(consumerTask.id())) {
                validateInternalOrdering(producerTask, producer.stepId(), consumer.stepId());
                internalDependencies.add(new ProviderLocalTaskDependency(
                        producerTask.id(), producer.stepId(), consumer.stepId(), dependency));
            } else {
                externalDependencies.add(new ExecutableTaskDependency(
                        producerTask.id(), consumerTask.id(), dependency));
            }
        }
        internalDependencies.sort(Comparator.comparing(
                ExecutableTaskCanonicalCodec::internalDependency));
        externalDependencies.sort(Comparator.comparing(
                ExecutableTaskCanonicalCodec::taskDependency));

        List<MandatoryArtifactBoundary> boundaries = mandatoryBoundaries(
                sourcePhysicalPlan, sourceTopology, coverage.taskByUnit());
        List<ExecutableTask.RequiredInputArtifactPin> inputPins = canonicalTasks.stream()
                .flatMap(task -> task.requiredInputArtifactPins().stream())
                .sorted(Comparator.comparing(
                        ExecutableTaskCanonicalCodec::requiredInputArtifactPin))
                .toList();

        validateDependencyCoverage(
                sourceTopology.dependencies().size(), internalDependencies, externalDependencies);
        validateAcyclic(canonicalTasks, externalDependencies);

        String graphCanonical = ExecutableTaskCanonicalCodec.graphSemantics(
                GRAPH_SCHEMA_VERSION,
                sourcePhysicalPlan.formatVersion(),
                sourcePhysicalPlan.schemaVersion().value(),
                sourcePhysicalPlan.planFingerprint().sha256Hex(),
                canonicalTasks,
                internalDependencies,
                externalDependencies,
                selectedTransitions,
                boundaries,
                canonicalCrossProviderBoundaries,
                inputPins);
        ExecutableTaskGraphDigest digest = new ExecutableTaskGraphDigest(
                ExecutableTaskCanonicalCodec.sha256(graphCanonical));
        return new ProviderBoundExecutableTaskGraph(
                sourcePhysicalPlan,
                providerCompatibilityGraph,
                canonicalTasks,
                List.copyOf(internalDependencies),
                List.copyOf(externalDependencies),
                selectedTransitions,
                boundaries,
                canonicalCrossProviderBoundaries,
                List.copyOf(inputPins),
                sourceTopology.dependencies().size(),
                digest);
    }

    public PhysicalExecutionPlan sourcePhysicalPlan() {
        return sourcePhysicalPlan;
    }

    public ProviderCompatibilityGraph providerCompatibilityGraph() {
        return providerCompatibilityGraph;
    }

    public List<ExecutableTask> tasks() {
        return tasks;
    }

    public List<ProviderLocalTaskDependency> providerLocalDependencies() {
        return providerLocalDependencies;
    }

    public List<ExecutableTaskDependency> taskDependencies() {
        return taskDependencies;
    }

    public List<ProviderCompatibilityTransition> selectedProviderTransitions() {
        return selectedProviderTransitions;
    }

    public List<MandatoryArtifactBoundary> mandatoryArtifactBoundaries() {
        return mandatoryArtifactBoundaries;
    }

    public List<CrossProviderArtifactBoundary> crossProviderArtifactBoundaries() {
        return crossProviderArtifactBoundaries;
    }

    public List<ExecutableTask.RequiredInputArtifactPin> requiredInputArtifactPins() {
        return requiredInputArtifactPins;
    }

    public ExecutableTaskGraphDigest digest() {
        return digest;
    }

    public int sourcePhysicalPlanUnitCount() {
        return sourcePhysicalPlan.units().size();
    }

    public int uniqueMembershipPhysicalUnitCount() {
        return tasks.stream().mapToInt(task -> task.memberships().size()).sum();
    }

    public int missingMembershipCount() {
        return 0;
    }

    public int duplicateMembershipCount() {
        return 0;
    }

    public int dependencyLossCount() {
        return sourceDependencyCount
                - providerLocalDependencies.size()
                - taskDependencies.size();
    }

    public int mandatoryArtifactBoundaryViolationCount() {
        return 0;
    }

    private static List<ExecutableTask> canonicalTasks(Collection<ExecutableTask> values) {
        List<ExecutableTask> canonical = new ArrayList<>(values.size());
        Set<ExecutableTaskId> seen = new HashSet<>();
        for (ExecutableTask task : values) {
            Objects.requireNonNull(task, "tasks element");
            if (!seen.add(task.id())) {
                throw new IllegalArgumentException("duplicate ExecutableTaskId: " + task.id().sha256Hex());
            }
            canonical.add(task);
        }
        canonical.sort(Comparator.comparing(ExecutableTask::id));
        return List.copyOf(canonical);
    }

    private static void validateEvaluatorProvenComposition(
            PhysicalExecutionPlan sourcePlan,
            ProviderCompatibilityGraph compatibilityGraph,
            List<ExecutableTask> tasks) {
        for (ExecutableTask task : tasks) {
            if (!task.compositionDecision().evaluatorProvenAllowed()) {
                throw new IllegalArgumentException(
                        "every ExecutableTask requires an evaluator-proven ALLOWED "
                                + "ProviderLocalCompositionEvaluator decision: "
                                + task.id().sha256Hex());
            }
            if (!task.compositionDecision().provenCompatibilityGraph().equals(compatibilityGraph)
                    || !task.compositionDecision().provenCompatibilityGraph()
                            .bindsExactSourcePlan(sourcePlan)
                    || !task.compositionDecision().provenProviderCandidate().bindingPin()
                            .equals(task.providerBindingPin())) {
                throw new IllegalArgumentException(
                        "task proof context must bind the exact ETG graph/source/provider");
            }
            for (ExecutableTaskMembership membership : task.memberships()) {
                compatibilityGraph.requireStaticallyFeasible(
                        membership.physicalPlanUnit(),
                        task.compositionDecision().provenProviderCandidate());
            }
        }
    }

    private static List<CrossProviderArtifactBoundary> canonicalCrossProviderBoundaries(
            Collection<CrossProviderArtifactBoundary> values) {
        List<CrossProviderArtifactBoundary> canonical = new ArrayList<>(values.size());
        for (CrossProviderArtifactBoundary boundary : values) {
            canonical.add(Objects.requireNonNull(
                    boundary, "crossProviderArtifactBoundaries element"));
        }
        canonical.sort(Comparator.comparing(
                ExecutableTaskCanonicalCodec::crossProviderArtifactBoundary));
        for (int i = 1; i < canonical.size(); i++) {
            if (canonical.get(i - 1).equals(canonical.get(i))) {
                throw new IllegalArgumentException("duplicate cross-provider Artifact boundary");
            }
        }
        return List.copyOf(canonical);
    }

    private static List<ProviderCompatibilityTransition> validateTransitionsAndBoundaries(
            ProviderCompatibilityGraph compatibilityGraph,
            SourceTopology topology,
            Coverage coverage,
            List<CrossProviderArtifactBoundary> boundaries) {
        List<ProviderCompatibilityTransition> selected = new ArrayList<>();
        Set<CrossProviderArtifactBoundary> usedBoundaries = new HashSet<>();
        for (LogicalDependencyEdge dependency : topology.dependencies()) {
            PhysicalPlanUnit producer = topology.byLogicalNode()
                    .get(dependency.producerLogicalNodeId());
            PhysicalPlanUnit consumer = topology.byLogicalNode()
                    .get(dependency.consumerLogicalNodeId());
            ExecutableTask producerTask = coverage.taskByUnit().get(producer.stepId());
            ExecutableTask consumerTask = coverage.taskByUnit().get(consumer.stepId());
            ProviderCompatibilityTransition transition = compatibilityGraph.requireTransition(
                    dependency,
                    producer,
                    producerTask.providerBindingPin(),
                    consumer,
                    consumerTask.providerBindingPin());
            selected.add(transition);

            List<CrossProviderArtifactBoundary> matching = boundaries.stream()
                    .filter(boundary -> boundary.sourceDependency().equals(dependency)
                            && boundary.producerUnitId().equals(producer.stepId())
                            && boundary.consumerUnitId().equals(consumer.stepId())
                            && boundary.producerBindingPin()
                                    .equals(producerTask.providerBindingPin())
                            && boundary.consumerBindingPin()
                                    .equals(consumerTask.providerBindingPin()))
                    .toList();
            if (matching.size() > 1) {
                throw new IllegalArgumentException(
                        "one source dependency may have only one canonical cross-provider boundary");
            }
            switch (transition.decision()) {
                case DIRECT_COMPATIBLE -> {
                    if (!matching.isEmpty()) {
                        throw new IllegalArgumentException(
                                "direct provider transition must not carry materialization boundary");
                    }
                }
                case ARTIFACT_MATERIALIZATION_REQUIRED -> {
                    if (producerTask.id().equals(consumerTask.id())) {
                        throw new IllegalArgumentException(
                                "materializing provider transition cannot be hidden inside one task");
                    }
                    if (matching.size() != 1) {
                        throw new IllegalArgumentException(
                                "provider transition requires explicit cross-provider Artifact boundary");
                    }
                    CrossProviderArtifactBoundary boundary = matching.getFirst();
                    validateCrossProviderBoundary(
                            boundary, producer, producerTask, consumer, consumerTask, transition);
                    usedBoundaries.add(boundary);
                }
                case INCOMPATIBLE -> throw new IllegalArgumentException(
                        "incompatible provider transition rejects ETG derivation");
                case UNKNOWN_FAIL_CLOSED -> throw new IllegalArgumentException(
                        "unknown provider transition fails ETG derivation closed");
            }
        }
        if (usedBoundaries.size() != boundaries.size()) {
            throw new IllegalArgumentException(
                    "cross-provider Artifact boundary has no exact required source transition");
        }
        selected.sort(Comparator.comparing(ExecutableTaskCanonicalCodec::providerTransition));
        return List.copyOf(selected);
    }

    private static void validateCrossProviderBoundary(
            CrossProviderArtifactBoundary boundary,
            PhysicalPlanUnit producer,
            ExecutableTask producerTask,
            PhysicalPlanUnit consumer,
            ExecutableTask consumerTask,
            ProviderCompatibilityTransition transition) {
        if (!producer.typedOutputs().contains(boundary.producerOutput())
                || !consumer.typedInputs().contains(boundary.consumerInput())
                || !producerTask.providerBindingPin().equals(boundary.producerBindingPin())
                || !consumerTask.providerBindingPin().equals(boundary.consumerBindingPin())
                || !transition.boundaryContractId().equals(
                        boundary.interoperabilityContract())) {
            throw new IllegalArgumentException(
                    "cross-provider Artifact boundary must bind exact transition/output/input semantics");
        }
    }

    private static void validatePreAttachedCrossProviderActions(
            List<ExecutableTask> tasks,
            List<CrossProviderArtifactBoundary> canonicalBoundaries) {
        List<BoundaryAction> supplied = tasks.stream()
                .flatMap(task -> task.boundaryActions().stream())
                .filter(ProviderBoundExecutableTaskGraph::isCrossProviderAction)
                .toList();
        if (supplied.isEmpty()) {
            return;
        }

        Set<CrossProviderArtifactBoundary> manifested = Set.copyOf(canonicalBoundaries);
        for (BoundaryAction action : supplied) {
            CrossProviderArtifactBoundary boundary = crossProviderBoundary(action);
            if (!manifested.contains(boundary)) {
                throw new IllegalArgumentException(
                        "caller-supplied cross-provider action has no exact canonical boundary");
            }
        }
        for (CrossProviderArtifactBoundary boundary : canonicalBoundaries) {
            long producerActionCount = supplied.stream()
                    .filter(action -> action.target()
                            instanceof BoundaryAction.CrossProviderMaterializeTarget target
                            && target.boundary().equals(boundary))
                    .count();
            long consumerActionCount = supplied.stream()
                    .filter(action -> action.target()
                            instanceof BoundaryAction.CrossProviderAcquireTarget target
                            && target.boundary().equals(boundary))
                    .count();
            if (producerActionCount != 1 || consumerActionCount != 1) {
                throw new IllegalArgumentException(
                        "pre-attached cross-provider actions require exactly one producer and "
                                + "one consumer action per canonical boundary");
            }
        }
    }

    private static boolean isCrossProviderAction(BoundaryAction action) {
        return action.target() instanceof BoundaryAction.CrossProviderMaterializeTarget
                || action.target() instanceof BoundaryAction.CrossProviderAcquireTarget;
    }

    private static CrossProviderArtifactBoundary crossProviderBoundary(BoundaryAction action) {
        if (action.target() instanceof BoundaryAction.CrossProviderMaterializeTarget target) {
            return target.boundary();
        }
        if (action.target() instanceof BoundaryAction.CrossProviderAcquireTarget target) {
            return target.boundary();
        }
        throw new IllegalArgumentException("BoundaryAction is not cross-provider lowering");
    }

    private static List<ExecutableTask> lowerCrossProviderBoundaryActions(
            List<ExecutableTask> tasks,
            List<CrossProviderArtifactBoundary> boundaries) {
        List<ExecutableTask> lowered = new ArrayList<>(tasks.size());
        for (ExecutableTask task : tasks) {
            List<BoundaryAction> actions = new ArrayList<>(task.boundaryActions().stream()
                    .filter(action -> !isCrossProviderAction(action))
                    .toList());
            int nextPreOrder = nextOrder(actions, BoundaryAction.Phase.PRE_EXECUTION);
            int nextPostOrder = nextOrder(actions, BoundaryAction.Phase.POST_EXECUTION);
            for (CrossProviderArtifactBoundary boundary : boundaries) {
                if (task.memberships().stream().anyMatch(member ->
                        member.physicalPlanUnitId().equals(boundary.producerUnitId()))) {
                    actions.add(new BoundaryAction(
                            BoundaryAction.Phase.POST_EXECUTION,
                            nextPostOrder++,
                            new BoundaryAction.CrossProviderMaterializeTarget(boundary)));
                }
                if (task.memberships().stream().anyMatch(member ->
                        member.physicalPlanUnitId().equals(boundary.consumerUnitId()))) {
                    actions.add(new BoundaryAction(
                            BoundaryAction.Phase.PRE_EXECUTION,
                            nextPreOrder++,
                            new BoundaryAction.CrossProviderAcquireTarget(boundary)));
                }
            }
            lowered.add(ExecutableTask.create(task.compositionDecision(), actions));
        }
        return canonicalTasks(lowered);
    }

    private static int nextOrder(
            List<BoundaryAction> actions,
            BoundaryAction.Phase phase) {
        return actions.stream()
                .filter(action -> action.phase() == phase)
                .mapToInt(BoundaryAction::deterministicOrder)
                .max()
                .orElse(-1) + 1;
    }

    private static Coverage validateCoverage(
            PhysicalExecutionPlan sourcePlan,
            List<ExecutableTask> tasks) {
        List<ExecutableTaskMembership> flattened = tasks.stream()
                .flatMap(task -> task.memberships().stream())
                .toList();
        ExecutableTaskMembership.validateExactCoverage(sourcePlan, flattened);

        Map<ExecutionStepId, ExecutableTask> taskByUnit = new HashMap<>();
        for (ExecutableTask task : tasks) {
            for (ExecutableTaskMembership membership : task.memberships()) {
                if (taskByUnit.putIfAbsent(membership.physicalPlanUnitId(), task) != null) {
                    throw new IllegalArgumentException(
                            "duplicate physical plan unit membership across executable tasks: "
                                    + membership.physicalPlanUnitId().value());
                }
            }
        }
        if (sourcePlan.units().size() != taskByUnit.size()) {
            throw new IllegalArgumentException("physical plan unit coverage count mismatch");
        }
        return new Coverage(Map.copyOf(taskByUnit));
    }

    private static SourceTopology sourceTopology(PhysicalExecutionPlan sourcePlan) {
        Map<String, PhysicalPlanUnit> byLogicalNode = new HashMap<>();
        for (PhysicalPlanUnit unit : sourcePlan.units()) {
            if (byLogicalNode.putIfAbsent(unit.logicalNodeId(), unit) != null) {
                throw new IllegalArgumentException(
                        "duplicate physical plan logical node identity: " + unit.logicalNodeId());
            }
        }

        Map<ExecutionEdgeId, LogicalDependencyEdge> byEdgeId = new TreeMap<>(
                Comparator.comparing(ExecutionEdgeId::value));
        for (PhysicalPlanUnit unit : sourcePlan.units()) {
            for (LogicalDependencyEdge dependency : unit.typedDependencies()) {
                LogicalDependencyEdge prior = byEdgeId.putIfAbsent(dependency.edgeId(), dependency);
                if (prior != null && !prior.equals(dependency)) {
                    throw new IllegalArgumentException(
                            "dependency identity carries conflicting semantics: "
                                    + dependency.edgeId().value());
                }
            }
        }
        for (LogicalDependencyEdge dependency : byEdgeId.values()) {
            if (!byLogicalNode.containsKey(dependency.producerLogicalNodeId())
                    || !byLogicalNode.containsKey(dependency.consumerLogicalNodeId())) {
                throw new IllegalArgumentException(
                        "dependency must reference physical plan units: "
                                + dependency.edgeId().value());
            }
        }
        return new SourceTopology(
                Map.copyOf(byLogicalNode), List.copyOf(byEdgeId.values()));
    }

    private static void validateInputDependencyMappings(
            PhysicalExecutionPlan sourcePlan,
            SourceTopology topology) {
        for (PhysicalPlanUnit consumer : sourcePlan.units()) {
            for (InputBinding input : consumer.typedInputs()) {
                if (input.producerStepId() == null) {
                    continue;
                }
                PhysicalPlanUnit producer = sourcePlan.units().stream()
                        .filter(unit -> unit.stepId().equals(input.producerStepId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "input dependency producer is absent from the physical plan"));
                boolean retained = topology.dependencies().stream().anyMatch(dependency ->
                        dependency.producerLogicalNodeId().equals(producer.logicalNodeId())
                                && dependency.consumerLogicalNodeId().equals(consumer.logicalNodeId())
                                && dependency.dependencyVariant().equals(input.dependencyVariant()));
                if (!retained) {
                    throw new IllegalArgumentException(
                            "typed input dependency has no canonical source dependency mapping");
                }
            }
        }
    }

    private static List<MandatoryArtifactBoundary> mandatoryBoundaries(
            PhysicalExecutionPlan sourcePlan,
            SourceTopology topology,
            Map<ExecutionStepId, ExecutableTask> taskByUnit) {
        List<MandatoryArtifactBoundary> boundaries = new ArrayList<>();
        for (PhysicalPlanUnit producer : sourcePlan.units()) {
            List<LogicalDependencyEdge> downstream = topology.dependencies().stream()
                    .filter(dependency -> dependency.producerLogicalNodeId()
                            .equals(producer.logicalNodeId()))
                    .sorted(Comparator.comparing(dependency -> dependency.edgeId().value()))
                    .toList();
            for (var output : producer.typedOutputs()) {
                for (var requirement : output.materializationRequirements()) {
                    for (LogicalDependencyEdge dependency : downstream) {
                        PhysicalPlanUnit consumer = topology.byLogicalNode()
                                .get(dependency.consumerLogicalNodeId());
                        if (taskByUnit.get(producer.stepId()).id()
                                .equals(taskByUnit.get(consumer.stepId()).id())) {
                            throw new IllegalArgumentException(
                                    "mandatory Artifact boundary cannot be coalesced into one task");
                        }
                    }
                    boundaries.add(new MandatoryArtifactBoundary(
                            producer.stepId(), output, requirement, downstream));
                }
            }
        }
        boundaries.sort(Comparator.comparing(
                ExecutableTaskCanonicalCodec::mandatoryArtifactBoundary));
        return List.copyOf(boundaries);
    }

    private static void validateDependencyCoverage(
            int sourceCount,
            List<ProviderLocalTaskDependency> internalDependencies,
            List<ExecutableTaskDependency> taskDependencies) {
        if (sourceCount != internalDependencies.size() + taskDependencies.size()) {
            throw new IllegalArgumentException("source physical dependency was lost during task grouping");
        }
    }

    private static void validateInternalOrdering(
            ExecutableTask task,
            ExecutionStepId producerUnitId,
            ExecutionStepId consumerUnitId) {
        Map<ExecutionStepId, Integer> positions = new HashMap<>();
        task.memberships().forEach(membership -> positions.put(
                membership.physicalPlanUnitId(), membership.canonicalPosition()));
        if (positions.get(producerUnitId) >= positions.get(consumerUnitId)) {
            throw new IllegalArgumentException(
                    "coalesced dependency must remain provider-local membership ordering");
        }
    }

    private static void validateAcyclic(
            List<ExecutableTask> tasks,
            List<ExecutableTaskDependency> taskDependencies) {
        DirectedGraphView<ExecutableTaskId> topology = new TaskGraphView(tasks, taskDependencies);
        if (!GraphAlgorithms.detectCycles(topology).isAcyclic()) {
            throw new IllegalArgumentException("provider-bound executable task graph must be acyclic");
        }
    }

    private record Coverage(Map<ExecutionStepId, ExecutableTask> taskByUnit) {
    }

    private record SourceTopology(
            Map<String, PhysicalPlanUnit> byLogicalNode,
            List<LogicalDependencyEdge> dependencies) {
    }

    private static final class TaskGraphView implements DirectedGraphView<ExecutableTaskId> {

        private final Set<ExecutableTaskId> nodes;
        private final Map<ExecutableTaskId, Set<ExecutableTaskId>> successors;
        private final Map<ExecutableTaskId, Set<ExecutableTaskId>> predecessors;
        private final int edgeCount;

        private TaskGraphView(
                List<ExecutableTask> tasks,
                List<ExecutableTaskDependency> dependencies) {
            Comparator<ExecutableTaskId> order = Comparator.naturalOrder();
            TreeSet<ExecutableTaskId> nodeSet = new TreeSet<>(order);
            tasks.forEach(task -> nodeSet.add(task.id()));
            this.nodes = Collections.unmodifiableSet(nodeSet);

            Map<ExecutableTaskId, Set<ExecutableTaskId>> next = new LinkedHashMap<>();
            Map<ExecutableTaskId, Set<ExecutableTaskId>> prior = new LinkedHashMap<>();
            for (ExecutableTaskId node : nodeSet) {
                next.put(node, new TreeSet<>(order));
                prior.put(node, new TreeSet<>(order));
            }
            for (ExecutableTaskDependency dependency : dependencies) {
                next.get(dependency.producerTaskId()).add(dependency.consumerTaskId());
                prior.get(dependency.consumerTaskId()).add(dependency.producerTaskId());
            }
            this.successors = immutableAdjacency(next);
            this.predecessors = immutableAdjacency(prior);
            this.edgeCount = this.successors.values().stream().mapToInt(Set::size).sum();
        }

        @Override
        public Set<ExecutableTaskId> nodes() {
            return nodes;
        }

        @Override
        public Set<ExecutableTaskId> successors(ExecutableTaskId node) {
            return adjacency(successors, node);
        }

        @Override
        public Set<ExecutableTaskId> predecessors(ExecutableTaskId node) {
            return adjacency(predecessors, node);
        }

        @Override
        public int nodeCount() {
            return nodes.size();
        }

        @Override
        public int edgeCount() {
            return edgeCount;
        }

        private static Map<ExecutableTaskId, Set<ExecutableTaskId>> immutableAdjacency(
                Map<ExecutableTaskId, Set<ExecutableTaskId>> source) {
            Map<ExecutableTaskId, Set<ExecutableTaskId>> result = new LinkedHashMap<>();
            source.forEach((key, value) -> result.put(
                    key, Collections.unmodifiableSet(new TreeSet<>(value))));
            return Collections.unmodifiableMap(result);
        }

        private static Set<ExecutableTaskId> adjacency(
                Map<ExecutableTaskId, Set<ExecutableTaskId>> adjacency,
                ExecutableTaskId node) {
            Set<ExecutableTaskId> result = adjacency.get(node);
            if (result == null) {
                throw new IllegalArgumentException("task is not in graph");
            }
            return result;
        }
    }
}
