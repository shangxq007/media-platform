package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCanonicalCodec;
import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import java.nio.charset.StandardCharsets;
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
 * Canonically built, immutable Stage-1 feasibility graph bound to one exact source plan.
 * Positive nodes contain only opaque proofs emitted by {@link CompatibilityKernel}; transitions
 * cover every feasible candidate pair for every source dependency.
 */
public final class ProviderCompatibilityGraph {

    public static final int CURRENT_SCHEMA_VERSION = 3;

    private final int schemaVersion;
    private final PhysicalExecutionPlan sourcePhysicalPlan;
    private final PhysicalExecutionPlanDigest sourcePlanSemanticDigest;
    private final List<UnitCandidates> unitCandidates;
    private final List<ProviderCompatibilityTransition> transitions;

    private ProviderCompatibilityGraph(
            PhysicalExecutionPlan sourcePhysicalPlan,
            PhysicalExecutionPlanDigest sourcePlanSemanticDigest,
            List<UnitCandidates> unitCandidates,
            List<ProviderCompatibilityTransition> transitions) {
        this.schemaVersion = CURRENT_SCHEMA_VERSION;
        this.sourcePhysicalPlan = sourcePhysicalPlan;
        this.sourcePlanSemanticDigest = sourcePlanSemanticDigest;
        this.unitCandidates = List.copyOf(unitCandidates);
        this.transitions = List.copyOf(transitions);
    }

    /** The sole construction path: candidate discovery order and declaration order are nonsemantic. */
    public static ProviderCompatibilityGraph build(
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
        transitions.sort(Comparator.comparing(ProviderCompatibilityGraph::canonicalTransition));
        rejectAdjacentDuplicateTransitions(transitions);

        return new ProviderCompatibilityGraph(
                sourcePhysicalPlan,
                semanticDigest(sourcePhysicalPlan),
                nodes,
                transitions);
    }

    public int schemaVersion() {
        return schemaVersion;
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

    public byte[] canonicalSerialization() {
        List<String> nodes = unitCandidates.stream()
                .map(ProviderCompatibilityGraph::canonicalUnitCandidates)
                .toList();
        List<String> transitionValues = transitions.stream()
                .map(ProviderCompatibilityGraph::canonicalTransition)
                .toList();
        String canonical = new CanonicalWriter()
                .tag("roadmap22.provider-compatibility-graph.v3")
                .field("schemaVersion", Integer.toString(schemaVersion))
                .field("sourcePlanSemanticDigest", sourcePlanSemanticDigest.sha256Hex())
                .field("unitCandidates", new CanonicalWriter().list(nodes).build())
                .field("transitions", new CanonicalWriter().list(transitionValues).build())
                .build();
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    public ProviderCompatibilityGraphDigest digest() {
        return ProviderCompatibilityGraphDigest.fromCanonicalBytes(canonicalSerialization());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProviderCompatibilityGraph that
                && sourcePhysicalPlan.equals(that.sourcePhysicalPlan)
                && sourcePlanSemanticDigest.equals(that.sourcePlanSemanticDigest)
                && unitCandidates.equals(that.unitCandidates)
                && transitions.equals(that.transitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePhysicalPlan, sourcePlanSemanticDigest, unitCandidates, transitions);
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
        canonical.sort(Comparator.comparing(ProviderCompatibilityGraph::canonicalDeclaration));
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

    private static String canonicalUnitCandidates(UnitCandidates candidates) {
        List<String> bindings = candidates.compatibilityProofs().stream()
                .map(proof -> canonicalCandidate(proof.providerCandidate()))
                .toList();
        List<String> constraints = candidates.compatibilityRequest().additionalConstraints().stream()
                .map(StaticCompatibilityConstraint::canonicalKey)
                .toList();
        return new CanonicalWriter()
                .tag("ProviderCompatibilityGraph.UnitCandidates.v2")
                .field("physicalPlanUnitId", candidates.physicalPlanUnitId().value())
                .field("additionalConstraints", new CanonicalWriter().list(constraints).build())
                .field("feasibleProviderCandidates", new CanonicalWriter().list(bindings).build())
                .build();
    }

    private static String canonicalCandidate(ProviderCandidate candidate) {
        ProviderStaticCompatibility support = candidate.staticCompatibility();
        return new CanonicalWriter()
                .tag("ProviderCandidate.v1")
                .field("binding", utf8(ProviderCanonicalCodec.serialize(candidate.bindingPin())))
                .field("descriptor", utf8(ProviderCanonicalCodec.serialize(candidate.descriptor())))
                .field("contract", utf8(ProviderCanonicalCodec.serialize(candidate.executionContract())))
                .field("profile", utf8(ProviderCanonicalCodec.serialize(candidate.capabilityProfile())))
                .field("knowledge", support.knowledge().name())
                .field("artifactRequirements", enumList(support.supportedArtifactRequirements()))
                .field("codecs", valueList(support.supportedCodecs().stream()
                        .map(StaticCompatibilityConstraint.CodecId::value).toList()))
                .field("deviceKinds", enumList(support.supportedDeviceKinds()))
                .field("runtimeClasses", enumList(support.supportedRuntimeClasses()))
                .field("sandboxModes", enumList(support.supportedSandboxModes()))
                .field("determinismClasses", enumList(support.supportedDeterminismClasses()))
                .field("boundaryContracts", valueList(support.supportedBoundaryContracts().stream()
                        .map(StaticCompatibilityConstraint.BoundaryContractId::value).toList()))
                .field("loweringSupport", support.loweringSupport().name())
                .build();
    }

    private static String canonicalTransition(ProviderCompatibilityTransition transition) {
        return new CanonicalWriter()
                .tag("ProviderCompatibilityTransition.v2")
                .field("sourceDependency", canonicalDependency(transition.sourceDependency()))
                .field("producerUnitId", transition.producerUnit().stepId().value())
                .field("producerBinding", utf8(ProviderCanonicalCodec.serialize(
                        transition.producerBindingPin())))
                .field("consumerUnitId", transition.consumerUnit().stepId().value())
                .field("consumerBinding", utf8(ProviderCanonicalCodec.serialize(
                        transition.consumerBindingPin())))
                .field("decision", transition.decision().name())
                .field("boundaryContract", new CanonicalWriter()
                        .optional(transition.boundaryContractId().isPresent(),
                                transition.boundaryContractId().map(value -> value.value()).orElse(null))
                        .build())
                .build();
    }

    private static String canonicalDeclaration(ProviderBoundaryCompatibilityDeclaration value) {
        return new CanonicalWriter()
                .tag("ProviderBoundaryCompatibilityDeclaration.v1")
                .field("dependency", canonicalDependency(value.sourceDependency()))
                .field("producerBinding", utf8(ProviderCanonicalCodec.serialize(
                        value.producerBindingPin())))
                .field("consumerBinding", utf8(ProviderCanonicalCodec.serialize(
                        value.consumerBindingPin())))
                .field("contract", value.boundaryContractId().value())
                .field("declaration", value.declaration().name())
                .build();
    }

    private static String canonicalDependency(LogicalDependencyEdge dependency) {
        return new CanonicalWriter()
                .tag("LogicalDependencyEdgeReference.v1")
                .field("edgeId", dependency.edgeId().value())
                .field("producerLogicalNodeId", dependency.producerLogicalNodeId())
                .field("consumerLogicalNodeId", dependency.consumerLogicalNodeId())
                .field("producerRenderNodeId", dependency.producerRenderNodeId().value())
                .field("consumerRenderNodeId", dependency.consumerRenderNodeId().value())
                .field("dependencyType", dependency.dependencyVariant().getClass().getName())
                .build();
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
            if (canonicalTransition(sorted.get(i - 1)).equals(canonicalTransition(sorted.get(i)))) {
                throw new IllegalArgumentException("duplicate provider compatibility transition");
            }
        }
    }

    private static String utf8(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static String valueList(List<String> values) {
        return new CanonicalWriter().list(values).build();
    }

    private static String enumList(List<? extends Enum<?>> values) {
        return valueList(values.stream().map(Enum::name).toList());
    }
}
