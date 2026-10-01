package com.example.platform.execution.binding;

import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderCompatibilityTransition;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.composition.CompositionDecision;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.composition.ProviderCompositionDeclaration;
import com.example.platform.execution.composition.ProviderCompositionDeclaration.NativePipelineSupport;
import com.example.platform.execution.composition.ProviderLocalCompositionEvaluator;
import com.example.platform.execution.composition.ProviderLocalCompositionRequest;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutionArtifactBoundary;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Production entry into typed-chain stage #22 (provider binding).
 *
 * <p>Turns one canonical #21 {@link PhysicalExecutionPlan} plus the statically
 * declared provider candidates and inter-provider transition declarations into
 * the provider-bound executable task graph:
 *
 * <pre>
 *   PhysicalExecutionPlan
 *     + List&lt;ProviderCandidate&gt;                          (declared candidates)
 *     + List&lt;ProviderBoundaryCompatibilityDeclaration&gt;  (declared transitions)
 *     → CompatibilityRequest per physical plan unit
 *     → ProviderFeasibilityView.build(...)               (Stage-1 static legality)
 *     → one ExecutableTask per unit (exact ProviderBindingPin)
 *     → ProviderBoundExecutableTaskGraph.derive(...)
 * </pre>
 *
 * <p>Boundary contract:
 * <ul>
 *   <li>Candidates are DECLARED inputs. This entry performs no provider
 *       discovery, no registry lookup, and reads no mutable runtime state: the
 *       provider-plugin catalog to candidate projection belongs to the wiring
 *       layer that owns the catalog.</li>
 *   <li>Compatibility is decided ONLY by kernel-emitted proofs inside
 *       {@link ProviderFeasibilityView}; nothing here may declare a pair
 *       compatible.</li>
 *   <li>V1 defines NO provider selection policy, so a unit is bound only when
 *       exactly one declared candidate is statically feasible. Zero feasible
 *       candidates fail closed (UNIT_UNBINDABLE); more than one fail closed
 *       (UNIT_AMBIGUOUS) rather than inventing a preference.</li>
 *   <li>Tasks are 1:1 with physical plan units (no fusion), so every source
 *       dependency is an inter-task transfer. Each dependency that the
 *       feasibility view marks ARTIFACT_MATERIALIZATION_REQUIRED gets exactly
 *       one derived {@link ExecutionArtifactBoundary} whose typed outputs,
 *       inputs and materialization reason come from the plan and the transition
 *       — never invented.</li>
 * </ul>
 *
 * <p>Scope: stage #22 only. Runtime lowering/adapter execution (#15/#16) is out
 * of scope and is deliberately not invoked here.
 */
public final class ProviderBindingEntryService {

    /**
     * Binds a canonical physical plan to declared provider candidates.
     *
     * @throws ProviderBindingException when the plan cannot be bound fail-closed
     */
    public ProviderBindingOutcome bind(
            PhysicalExecutionPlan physicalExecutionPlan,
            List<ProviderCandidate> candidates,
            List<ProviderBoundaryCompatibilityDeclaration> transitionDeclarations) {
        Objects.requireNonNull(physicalExecutionPlan, "physicalExecutionPlan");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(transitionDeclarations, "transitionDeclarations");
        List<ProviderCandidate> declaredCandidates = List.copyOf(candidates);
        if (!physicalExecutionPlan.units().isEmpty() && declaredCandidates.isEmpty()) {
            throw new ProviderBindingException(
                    ProviderBindingException.Reason.NO_CANDIDATES,
                    "a non-empty physical plan requires at least one declared provider candidate");
        }

        List<CompatibilityRequest> requests = physicalExecutionPlan.units().stream()
                .map(CompatibilityRequest::forUnit)
                .toList();
        ProviderFeasibilityView feasibilityView = ProviderFeasibilityView.build(
                physicalExecutionPlan, requests, declaredCandidates, transitionDeclarations);

        Map<ExecutionStepId, ExecutableTask> taskByUnit = new LinkedHashMap<>();
        for (PhysicalPlanUnit unit : physicalExecutionPlan.units()) {
            ProviderCandidate candidate = singleFeasibleCandidate(feasibilityView, unit);
            taskByUnit.put(unit.stepId(), executableTask(feasibilityView, candidate, unit));
        }

        List<ExecutionArtifactBoundary> boundaries = executionArtifactBoundaries(
                physicalExecutionPlan, feasibilityView, taskByUnit);
        ProviderBoundExecutableTaskGraph graph = ProviderBoundExecutableTaskGraph.derive(
                physicalExecutionPlan, feasibilityView, List.copyOf(taskByUnit.values()), boundaries);
        return new ProviderBindingOutcome(feasibilityView, graph);
    }

    /** Typed #22 outcome: the Stage-1 view plus the derived provider-bound task graph. */
    public record ProviderBindingOutcome(
            ProviderFeasibilityView feasibilityView,
            ProviderBoundExecutableTaskGraph executableTaskGraph) {

        public ProviderBindingOutcome {
            Objects.requireNonNull(feasibilityView, "feasibilityView");
            Objects.requireNonNull(executableTaskGraph, "executableTaskGraph");
        }

        /** The provider-bound tasks, each carrying its exact {@code ProviderBindingPin}. */
        public List<ExecutableTask> tasks() {
            return executableTaskGraph.tasks();
        }

        public PhysicalExecutionPlan sourcePhysicalPlan() {
            return executableTaskGraph.sourcePhysicalPlan();
        }
    }

    // ── unit binding ─────────────────────────────────────────────────────────

    private static ProviderCandidate singleFeasibleCandidate(
            ProviderFeasibilityView view, PhysicalPlanUnit unit) {
        List<ProviderCandidate> feasible = view.unitCandidates().stream()
                .filter(node -> node.compatibilityRequest().physicalPlanUnit().equals(unit))
                .flatMap(node -> node.compatibilityProofs().stream())
                .map(proof -> proof.providerCandidate())
                .toList();
        if (feasible.isEmpty()) {
            throw new ProviderBindingException(
                    ProviderBindingException.Reason.UNIT_UNBINDABLE,
                    "no declared candidate is statically feasible for unit " + unit.stepId().value());
        }
        if (feasible.size() > 1) {
            throw new ProviderBindingException(
                    ProviderBindingException.Reason.UNIT_AMBIGUOUS,
                    "unit " + unit.stepId().value() + " has " + feasible.size()
                            + " statically feasible candidates and V1 defines no selection policy");
        }
        return feasible.getFirst();
    }

    private static ExecutableTask executableTask(
            ProviderFeasibilityView view, ProviderCandidate candidate, PhysicalPlanUnit unit) {
        ProviderCompositionDeclaration declaration = new ProviderCompositionDeclaration(
                candidate.bindingPin(), NativePipelineSupport.UNKNOWN);
        ProviderLocalCompositionRequest request = ProviderLocalCompositionRequest.of(
                ExecutableTaskMembership.canonicalForUnits(List.of(unit)),
                view,
                candidate,
                declaration,
                List.of());
        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(request);
        if (decision.status() != CompositionDecision.Status.ALLOWED) {
            throw new ProviderBindingException(
                    ProviderBindingException.Reason.COMPOSITION_FORBIDDEN,
                    "provider-local composition is not ALLOWED for unit " + unit.stepId().value()
                            + " (" + decision.status() + ")");
        }
        return ExecutableTask.create(decision, List.of());
    }

    // ── inter-task execution Artifact boundaries ─────────────────────────────

    private static List<ExecutionArtifactBoundary> executionArtifactBoundaries(
            PhysicalExecutionPlan plan,
            ProviderFeasibilityView view,
            Map<ExecutionStepId, ExecutableTask> taskByUnit) {
        Map<String, PhysicalPlanUnit> unitByLogicalNode = new LinkedHashMap<>();
        for (PhysicalPlanUnit unit : plan.units()) {
            unitByLogicalNode.put(unit.logicalNodeId(), unit);
        }
        List<ExecutionArtifactBoundary> boundaries = new ArrayList<>();
        for (LogicalDependencyEdge dependency : sourceDependencies(plan)) {
            PhysicalPlanUnit producer = unitByLogicalNode.get(dependency.producerLogicalNodeId());
            PhysicalPlanUnit consumer = unitByLogicalNode.get(dependency.consumerLogicalNodeId());
            if (producer == null || consumer == null) {
                throw new ProviderBindingException(
                        ProviderBindingException.Reason.PLAN_SHAPE_UNSUPPORTED,
                        "dependency endpoint is absent from the physical plan: "
                                + dependency.edgeId().value());
            }
            ExecutableTask producerTask = taskByUnit.get(producer.stepId());
            ExecutableTask consumerTask = taskByUnit.get(consumer.stepId());
            ProviderCompatibilityTransition transition = view.requireTransition(
                    dependency,
                    producer,
                    producerTask.providerBindingPin(),
                    consumer,
                    consumerTask.providerBindingPin());
            switch (transition.decision()) {
                case DIRECT_COMPATIBLE -> {
                    // no materialization boundary: the pair interoperates directly
                }
                case ARTIFACT_MATERIALIZATION_REQUIRED -> boundaries.add(
                        executionArtifactBoundary(
                                dependency, producer, consumer, producerTask, consumerTask, transition));
                case INCOMPATIBLE -> throw new ProviderBindingException(
                        ProviderBindingException.Reason.TRANSITION_INCOMPATIBLE,
                        "incompatible provider transition on dependency " + dependency.edgeId().value());
                case UNKNOWN_FAIL_CLOSED -> throw new ProviderBindingException(
                        ProviderBindingException.Reason.TRANSITION_UNKNOWN,
                        "unknown provider transition on dependency " + dependency.edgeId().value());
            }
        }
        return List.copyOf(boundaries);
    }

    private static ExecutionArtifactBoundary executionArtifactBoundary(
            LogicalDependencyEdge dependency,
            PhysicalPlanUnit producer,
            PhysicalPlanUnit consumer,
            ExecutableTask producerTask,
            ExecutableTask consumerTask,
            ProviderCompatibilityTransition transition) {
        if (producer.typedOutputs().size() != 1) {
            throw new ProviderBindingException(
                    ProviderBindingException.Reason.PLAN_SHAPE_UNSUPPORTED,
                    "bounded V1 requires exactly one output declaration per physical plan unit: "
                            + producer.stepId().value());
        }
        OutputDeclaration producerOutput = producer.typedOutputs().getFirst();
        InputBinding consumerInput = exactConsumerInput(consumer, dependency);
        return new ExecutionArtifactBoundary(
                dependency,
                producer.stepId(),
                consumer.stepId(),
                producerTask.providerBindingPin(),
                consumerTask.providerBindingPin(),
                producerOutput,
                consumerInput,
                ExecutionArtifactBoundary.MaterializationContract.IMMUTABLE_ARTIFACT_AUTHORITY_V1,
                requiredMaterializationReason(transition),
                transition.boundaryContractId());
    }

    /**
     * Resolves the one exact computed consumer input for a source dependency.
     * Mirrors the provider-bound graph's own resolution; zero or several matches
     * fail closed rather than guessing.
     */
    private static InputBinding exactConsumerInput(
            PhysicalPlanUnit consumer, LogicalDependencyEdge dependency) {
        List<InputBinding> matches = consumer.typedInputs().stream()
                .filter(input -> consumer.stepId().equals(input.consumerStepId()))
                .filter(input -> dependency.producerLogicalNodeId().equals(input.producerLogicalNodeId()))
                .filter(input -> dependency.producerRenderNodeId().equals(input.producerRenderNodeId()))
                .filter(input -> dependency.consumerLogicalNodeId().equals(input.consumerLogicalNodeId()))
                .filter(input -> dependency.consumerRenderNodeId().equals(input.consumerRenderNodeId()))
                .filter(input -> dependency.dependencyVariant().equals(input.dependencyVariant()))
                .filter(input -> input.sourceArtifact() == null)
                .toList();
        if (matches.size() != 1) {
            throw new ProviderBindingException(
                    ProviderBindingException.Reason.PLAN_SHAPE_UNSUPPORTED,
                    "source dependency " + dependency.edgeId().value()
                            + " must resolve exactly one computed consumer input");
        }
        return matches.getFirst();
    }

    /**
     * The materialization reason the provider-bound graph requires for this
     * transition: explicit when a boundary contract is present, otherwise the
     * binding-identity reason.
     */
    private static ExecutionArtifactBoundary.MaterializationReason requiredMaterializationReason(
            ProviderCompatibilityTransition transition) {
        if (transition.boundaryContractId().isPresent()) {
            return ExecutionArtifactBoundary.MaterializationReason.EXPLICIT_MATERIALIZATION_REQUIREMENT;
        }
        return transition.producerBindingPin().equals(transition.consumerBindingPin())
                ? ExecutionArtifactBoundary.MaterializationReason.INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN
                : ExecutionArtifactBoundary.MaterializationReason.PROVIDER_BINDING_CHANGE;
    }

    /** Canonical dependency set of the plan, deduplicated by edge identity. */
    private static List<LogicalDependencyEdge> sourceDependencies(PhysicalExecutionPlan plan) {
        Map<ExecutionEdgeId, LogicalDependencyEdge> byEdgeId = new TreeMap<>(
                Comparator.comparing(ExecutionEdgeId::value));
        for (PhysicalPlanUnit unit : plan.units()) {
            for (LogicalDependencyEdge dependency : unit.typedDependencies()) {
                LogicalDependencyEdge prior = byEdgeId.putIfAbsent(dependency.edgeId(), dependency);
                if (prior != null && !prior.equals(dependency)) {
                    throw new ProviderBindingException(
                            ProviderBindingException.Reason.PLAN_SHAPE_UNSUPPORTED,
                            "dependency identity carries conflicting semantics: "
                                    + dependency.edgeId().value());
                }
            }
        }
        return List.copyOf(byEdgeId.values());
    }
}
