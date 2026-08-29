package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCanonicalCodec;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable, in-process Stage-1 feasibility view bound to one exact source plan.
 * Positive nodes contain only opaque proofs emitted by {@link CompatibilityKernel}; transitions
 * cover every feasible candidate pair for every source dependency.
 */
public final class ProviderFeasibilityView {

    private final PhysicalExecutionPlan sourcePhysicalPlan;
    private final PhysicalExecutionPlanDigest sourcePlanSemanticDigest;
    private final List<UnitCandidates> unitCandidates;
    private final List<ProviderCompatibilityTransition> transitions;

    private ProviderFeasibilityView(
            PhysicalExecutionPlan sourcePhysicalPlan,
            PhysicalExecutionPlanDigest sourcePlanSemanticDigest,
            List<UnitCandidates> unitCandidates,
            List<ProviderCompatibilityTransition> transitions) {
        this.sourcePhysicalPlan = sourcePhysicalPlan;
        this.sourcePlanSemanticDigest = sourcePlanSemanticDigest;
        this.unitCandidates = List.copyOf(unitCandidates);
        this.transitions = List.copyOf(transitions);
    }

    /** The sole construction path: candidate discovery order and declaration order are nonsemantic. */
    public static ProviderFeasibilityView build(
            PhysicalExecutionPlan sourcePhysicalPlan,
            List<CompatibilityRequest> requests,
            List<ProviderCandidate> discoveredCandidates,
            List<ProviderBoundaryCompatibilityDeclaration> transitionDeclarations) {
        Objects.requireNonNull(sourcePhysicalPlan, "sourcePhysicalPlan");
        List<CompatibilityRequest> canonicalRequests =
                canonicalRequests(sourcePhysicalPlan, requests);
        List<ProviderCandidate> canonicalCandidates = canonicalCandidates(discoveredCandidates);
        List<ProviderBoundaryCompatibilityDeclaration> canonicalDeclarations =
                canonicalDeclarations(transitionDeclarations);

        List<UnitCandidates> nodes = new ArrayList<>(canonicalRequests.size());
        for (CompatibilityRequest request : canonicalRequests) {
            List<StaticProviderCompatibilityProof> proofs = new ArrayList<>();
            for (ProviderCandidate candidate : canonicalCandidates) {
                CompatibilityDecision decision = CompatibilityKernel.evaluate(request, candidate);
                if (decision.kernelProvenCompatible()) {
                    proofs.add(decision.staticCompatibilityProof().orElseThrow());
                }
            }
            nodes.add(new UnitCandidates(request, proofs));
        }

        List<LogicalDependencyEdge> dependencies = sourceDependencies(sourcePhysicalPlan);
        validateDeclarationsReferenceSource(dependencies, canonicalDeclarations);
        List<ProviderCompatibilityTransition> transitions = new ArrayList<>();
        Set<ProviderBoundaryCompatibilityDeclaration> usedDeclarations = new HashSet<>();
        for (LogicalDependencyEdge dependency : dependencies) {
            PhysicalPlanUnit producer = unitByLogicalNode(
                    sourcePhysicalPlan, dependency.producerLogicalNodeId());
            PhysicalPlanUnit consumer = unitByLogicalNode(
                    sourcePhysicalPlan, dependency.consumerLogicalNodeId());
            UnitCandidates producerNode = nodeFor(nodes, producer);
            UnitCandidates consumerNode = nodeFor(nodes, consumer);
            for (StaticProviderCompatibilityProof producerProof : producerNode.compatibilityProofs()) {
                for (StaticProviderCompatibilityProof consumerProof : consumerNode.compatibilityProofs()) {
                    ProviderCandidate producerCandidate = producerProof.providerCandidate();
                    ProviderCandidate consumerCandidate = consumerProof.providerCandidate();
                    Optional<ProviderBoundaryCompatibilityDeclaration> declaration =
                            declarationFor(
                                    canonicalDeclarations,
                                    dependency,
                                    producerCandidate.bindingPin(),
                                    consumerCandidate.bindingPin());
                    declaration.ifPresent(usedDeclarations::add);
                    transitions.add(transition(
                            dependency,
                            producer,
                            producerCandidate,
                            consumer,
                            consumerCandidate,
                            declaration));
                }
            }
        }
        if (usedDeclarations.size() != canonicalDeclarations.size()) {
            throw new IllegalArgumentException(
                    "transition declaration must reference a statically feasible candidate pair");
        }
        transitions.sort(ProviderFeasibilityView::compareTransitions);
        rejectAdjacentDuplicateTransitions(transitions);

        return new ProviderFeasibilityView(
                sourcePhysicalPlan,
                semanticDigest(sourcePhysicalPlan),
                nodes,
                transitions);
    }

    public PhysicalExecutionPlan sourcePhysicalPlan() {
        return sourcePhysicalPlan;
    }

    public PhysicalExecutionPlanDigest sourcePlanSemanticDigest() {
        return sourcePlanSemanticDigest;
    }

    public List<UnitCandidates> unitCandidates() {
        return unitCandidates;
    }

    public List<ProviderCompatibilityTransition> transitions() {
        return transitions;
    }

    public boolean bindsExactSourcePlan(PhysicalExecutionPlan sourcePlan) {
        return sourcePhysicalPlan.equals(sourcePlan)
                && sourcePlanSemanticDigest.equals(semanticDigest(sourcePlan));
    }

    public boolean isStaticallyFeasible(
            PhysicalPlanUnit unit,
            ProviderBindingPin bindingPin) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(bindingPin, "bindingPin");
        return unitCandidates.stream()
                .filter(node -> node.compatibilityRequest().physicalPlanUnit().equals(unit))
                .flatMap(node -> node.compatibilityProofs().stream())
                .anyMatch(proof -> proof.providerCandidate().bindingPin().equals(bindingPin));
    }

    public StaticProviderCompatibilityProof requireStaticallyFeasible(
            PhysicalPlanUnit unit,
            ProviderCandidate candidate) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(candidate, "candidate");
        return unitCandidates.stream()
                .filter(node -> node.compatibilityRequest().physicalPlanUnit().equals(unit))
                .flatMap(node -> node.compatibilityProofs().stream())
                .filter(proof -> proof.providerCandidate().equals(candidate)
                        && proof.proves(proof.compatibilityRequest(), candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "exact PhysicalPlanUnit/provider candidate is not statically feasible"));
    }

    public ProviderCompatibilityTransition requireTransition(
            LogicalDependencyEdge dependency,
            PhysicalPlanUnit producerUnit,
            ProviderBindingPin producerBinding,
            PhysicalPlanUnit consumerUnit,
            ProviderBindingPin consumerBinding) {
        return transitions.stream()
                .filter(value -> value.sourceDependency().equals(dependency)
                        && value.producerUnit().equals(producerUnit)
                        && value.producerBindingPin().equals(producerBinding)
                        && value.consumerUnit().equals(consumerUnit)
                        && value.consumerBindingPin().equals(consumerBinding))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "provider compatibility transition is absent for exact feasible pair"));
    }

    public record UnitCandidates(
            CompatibilityRequest compatibilityRequest,
            List<StaticProviderCompatibilityProof> compatibilityProofs) {

        public UnitCandidates {
            Objects.requireNonNull(compatibilityRequest, "compatibilityRequest");
            Objects.requireNonNull(compatibilityProofs, "compatibilityProofs");
            var canonical = new ArrayList<StaticProviderCompatibilityProof>(
                    compatibilityProofs.size());
            for (StaticProviderCompatibilityProof proof : compatibilityProofs) {
                Objects.requireNonNull(proof, "compatibilityProofs element");
                if (!proof.compatibilityRequest().equals(compatibilityRequest)) {
                    throw new IllegalArgumentException(
                            "proof must bind the node's exact CompatibilityRequest");
                }
                canonical.add(proof);
            }
            canonical.sort((first, second) -> compareBindings(
                    first.providerCandidate().bindingPin(),
                    second.providerCandidate().bindingPin()));
            for (int i = 1; i < canonical.size(); i++) {
                if (canonical.get(i - 1).providerCandidate().bindingPin().equals(
                        canonical.get(i).providerCandidate().bindingPin())) {
                    throw new IllegalArgumentException("duplicate feasible provider candidate");
                }
            }
            compatibilityProofs = List.copyOf(canonical);
        }

        public ExecutionStepId physicalPlanUnitId() {
            return compatibilityRequest.physicalPlanUnit().stepId();
        }

        public List<ProviderBindingPin> feasibleProviderBindings() {
            return compatibilityProofs.stream()
                    .map(proof -> proof.providerCandidate().bindingPin())
                    .toList();
        }
    }

    private static ProviderCompatibilityTransition transition(
            LogicalDependencyEdge dependency,
            PhysicalPlanUnit producer,
            ProviderCandidate producerCandidate,
            PhysicalPlanUnit consumer,
            ProviderCandidate consumerCandidate,
            Optional<ProviderBoundaryCompatibilityDeclaration> declaration) {
        ProviderCompatibilityTransitionDecision decision;
        Optional<StaticCompatibilityConstraint.BoundaryContractId> contract = Optional.empty();
        if (declaration.isPresent()) {
            ProviderBoundaryCompatibilityDeclaration value = declaration.orElseThrow();
            contract = Optional.of(value.boundaryContractId());
            decision = switch (value.declaration()) {
                case DIRECT_INTEROPERABILITY_ALLOWED -> directDecision(
                        producerCandidate, consumerCandidate, value);
                case ARTIFACT_MATERIALIZATION_REQUIRED ->
                        ProviderCompatibilityTransitionDecision.ARTIFACT_MATERIALIZATION_REQUIRED;
                case INCOMPATIBLE -> ProviderCompatibilityTransitionDecision.INCOMPATIBLE;
                case UNKNOWN_FAIL_CLOSED ->
                        ProviderCompatibilityTransitionDecision.UNKNOWN_FAIL_CLOSED;
            };
        } else {
            decision = ProviderCompatibilityTransitionDecision.ARTIFACT_MATERIALIZATION_REQUIRED;
        }
        return new ProviderCompatibilityTransition(
                dependency,
                producer,
                producerCandidate.bindingPin(),
                consumer,
                consumerCandidate.bindingPin(),
                decision,
                contract);
    }

    private static ProviderCompatibilityTransitionDecision directDecision(
            ProviderCandidate producer,
            ProviderCandidate consumer,
            ProviderBoundaryCompatibilityDeclaration declaration) {
        boolean producerSupports = producer.staticCompatibility().supportedBoundaryContracts()
                .contains(declaration.boundaryContractId());
        boolean consumerSupports = consumer.staticCompatibility().supportedBoundaryContracts()
                .contains(declaration.boundaryContractId());
        return producerSupports && consumerSupports
                ? ProviderCompatibilityTransitionDecision.DIRECT_COMPATIBLE
                : ProviderCompatibilityTransitionDecision.INCOMPATIBLE;
    }

    private static List<CompatibilityRequest> canonicalRequests(
            PhysicalExecutionPlan sourcePlan,
            List<CompatibilityRequest> requests) {
        Objects.requireNonNull(requests, "requests");
        var canonical = new ArrayList<CompatibilityRequest>(requests.size());
        for (CompatibilityRequest request : requests) {
            Objects.requireNonNull(request, "requests element");
            PhysicalPlanUnit sourceUnit = sourcePlan.units().stream()
                    .filter(unit -> unit.stepId().equals(request.physicalPlanUnit().stepId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "compatibility request unit is absent from source plan"));
            if (!sourceUnit.equals(request.physicalPlanUnit())) {
                throw new IllegalArgumentException(
                        "compatibility request must bind exact source PhysicalPlanUnit semantics");
            }
            canonical.add(request);
        }
        canonical.sort(Comparator.comparing(value -> value.physicalPlanUnit().stepId().value()));
        rejectAdjacentDuplicateRequestUnits(canonical);
        if (canonical.size() != sourcePlan.units().size()) {
            throw new IllegalArgumentException(
                    "every source PhysicalPlanUnit requires exactly one CompatibilityRequest");
        }
        return List.copyOf(canonical);
    }

    private static List<ProviderCandidate> canonicalCandidates(List<ProviderCandidate> candidates) {
        Objects.requireNonNull(candidates, "discoveredCandidates");
        var canonical = new ArrayList<ProviderCandidate>(candidates.size());
        candidates.forEach(candidate -> canonical.add(
                Objects.requireNonNull(candidate, "discoveredCandidates element")));
        canonical.sort((first, second) -> compareBindings(first.bindingPin(), second.bindingPin()));
        for (int i = 1; i < canonical.size(); i++) {
            if (canonical.get(i - 1).bindingPin().equals(canonical.get(i).bindingPin())) {
                throw new IllegalArgumentException("duplicate ProviderBindingPin candidate");
            }
        }
        return List.copyOf(canonical);
    }

    private static List<ProviderBoundaryCompatibilityDeclaration> canonicalDeclarations(
            List<ProviderBoundaryCompatibilityDeclaration> declarations) {
        Objects.requireNonNull(declarations, "transitionDeclarations");
        var canonical = new ArrayList<ProviderBoundaryCompatibilityDeclaration>(declarations.size());
        declarations.forEach(value -> canonical.add(
                Objects.requireNonNull(value, "transitionDeclarations element")));
        canonical.sort(ProviderFeasibilityView::compareDeclarations);
        for (int i = 1; i < canonical.size(); i++) {
            if (sameDeclarationKey(canonical.get(i - 1), canonical.get(i))) {
                throw new IllegalArgumentException("duplicate provider transition declaration");
            }
        }
        return List.copyOf(canonical);
    }

    private static List<LogicalDependencyEdge> sourceDependencies(PhysicalExecutionPlan plan) {
        var byIdentity = new TreeMap<ExecutionEdgeId, LogicalDependencyEdge>(
                Comparator.comparing(ExecutionEdgeId::value));
        for (PhysicalPlanUnit unit : plan.units()) {
            for (LogicalDependencyEdge dependency : unit.typedDependencies()) {
                LogicalDependencyEdge prior = byIdentity.putIfAbsent(
                        dependency.edgeId(), dependency);
                if (prior != null && !prior.equals(dependency)) {
                    throw new IllegalArgumentException(
                            "source dependency identity has conflicting semantics");
                }
            }
        }
        for (LogicalDependencyEdge dependency : byIdentity.values()) {
            unitByLogicalNode(plan, dependency.producerLogicalNodeId());
            unitByLogicalNode(plan, dependency.consumerLogicalNodeId());
        }
        return List.copyOf(byIdentity.values());
    }

    private static void validateDeclarationsReferenceSource(
            List<LogicalDependencyEdge> dependencies,
            List<ProviderBoundaryCompatibilityDeclaration> declarations) {
        for (ProviderBoundaryCompatibilityDeclaration declaration : declarations) {
            if (!dependencies.contains(declaration.sourceDependency())) {
                throw new IllegalArgumentException(
                        "transition declaration must reference exact source dependency semantics");
            }
        }
    }

    private static PhysicalPlanUnit unitByLogicalNode(PhysicalExecutionPlan plan, String logicalId) {
        return plan.units().stream()
                .filter(unit -> unit.logicalNodeId().equals(logicalId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "dependency endpoint is absent from source plan: " + logicalId));
    }

    private static UnitCandidates nodeFor(
            List<UnitCandidates> nodes,
            PhysicalPlanUnit unit) {
        return nodes.stream()
                .filter(node -> node.compatibilityRequest().physicalPlanUnit().equals(unit))
                .findFirst()
                .orElseThrow();
    }

    private static Optional<ProviderBoundaryCompatibilityDeclaration> declarationFor(
            List<ProviderBoundaryCompatibilityDeclaration> declarations,
            LogicalDependencyEdge dependency,
            ProviderBindingPin producer,
            ProviderBindingPin consumer) {
        return declarations.stream()
                .filter(value -> value.sourceDependency().equals(dependency)
                        && value.producerBindingPin().equals(producer)
                        && value.consumerBindingPin().equals(consumer))
                .findFirst();
    }

    private static PhysicalExecutionPlanDigest semanticDigest(PhysicalExecutionPlan plan) {
        return PhysicalExecutionPlanDigest.compute(
                plan.formatVersion(),
                plan.schemaVersion(),
                plan.units(),
                plan.planFingerprint(),
                plan.propagatedExtent());
    }

    private static int compareBindings(ProviderBindingPin first, ProviderBindingPin second) {
        return Arrays.compareUnsigned(
                ProviderCanonicalCodec.serialize(first), ProviderCanonicalCodec.serialize(second));
    }

    private static int compareTransitions(
            ProviderCompatibilityTransition first,
            ProviderCompatibilityTransition second) {
        int comparison = compareDependencies(first.sourceDependency(), second.sourceDependency());
        if (comparison == 0) {
            comparison = first.producerUnit().stepId().value()
                    .compareTo(second.producerUnit().stepId().value());
        }
        if (comparison == 0) {
            comparison = compareBindings(first.producerBindingPin(), second.producerBindingPin());
        }
        if (comparison == 0) {
            comparison = first.consumerUnit().stepId().value()
                    .compareTo(second.consumerUnit().stepId().value());
        }
        if (comparison == 0) {
            comparison = compareBindings(first.consumerBindingPin(), second.consumerBindingPin());
        }
        if (comparison == 0) {
            comparison = first.decision().compareTo(second.decision());
        }
        if (comparison == 0) {
            comparison = first.boundaryContractId().map(value -> value.value()).orElse("")
                    .compareTo(second.boundaryContractId().map(value -> value.value()).orElse(""));
        }
        return comparison;
    }

    private static int compareDeclarations(
            ProviderBoundaryCompatibilityDeclaration first,
            ProviderBoundaryCompatibilityDeclaration second) {
        int comparison = compareDependencies(first.sourceDependency(), second.sourceDependency());
        if (comparison == 0) {
            comparison = compareBindings(first.producerBindingPin(), second.producerBindingPin());
        }
        if (comparison == 0) {
            comparison = compareBindings(first.consumerBindingPin(), second.consumerBindingPin());
        }
        if (comparison == 0) {
            comparison = first.boundaryContractId().value()
                    .compareTo(second.boundaryContractId().value());
        }
        if (comparison == 0) {
            comparison = first.declaration().compareTo(second.declaration());
        }
        return comparison;
    }

    private static int compareDependencies(
            LogicalDependencyEdge first,
            LogicalDependencyEdge second) {
        int comparison = first.edgeId().value().compareTo(second.edgeId().value());
        if (comparison == 0) {
            comparison = first.producerLogicalNodeId().compareTo(second.producerLogicalNodeId());
        }
        if (comparison == 0) {
            comparison = first.consumerLogicalNodeId().compareTo(second.consumerLogicalNodeId());
        }
        if (comparison == 0) {
            comparison = first.producerRenderNodeId().value()
                    .compareTo(second.producerRenderNodeId().value());
        }
        if (comparison == 0) {
            comparison = first.consumerRenderNodeId().value()
                    .compareTo(second.consumerRenderNodeId().value());
        }
        if (comparison == 0) {
            comparison = first.dependencyVariant().getClass().getName()
                    .compareTo(second.dependencyVariant().getClass().getName());
        }
        return comparison;
    }

    private static boolean sameDeclarationKey(
            ProviderBoundaryCompatibilityDeclaration first,
            ProviderBoundaryCompatibilityDeclaration second) {
        return first.sourceDependency().equals(second.sourceDependency())
                && first.producerBindingPin().equals(second.producerBindingPin())
                && first.consumerBindingPin().equals(second.consumerBindingPin());
    }

    private static void rejectAdjacentDuplicateRequestUnits(List<CompatibilityRequest> sorted) {
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).physicalPlanUnit().stepId()
                    .equals(sorted.get(i).physicalPlanUnit().stepId())) {
                throw new IllegalArgumentException(
                        "duplicate PhysicalPlanUnit compatibility request");
            }
        }
    }

    private static void rejectAdjacentDuplicateTransitions(
            List<ProviderCompatibilityTransition> sorted) {
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).equals(sorted.get(i))) {
                throw new IllegalArgumentException("duplicate provider compatibility transition");
            }
        }
    }
}
