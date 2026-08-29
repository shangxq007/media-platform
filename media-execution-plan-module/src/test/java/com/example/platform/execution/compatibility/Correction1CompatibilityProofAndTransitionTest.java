package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Correction1CompatibilityProofAndTransitionTest {

    private static final BoundaryContractId DIRECT_CONTRACT =
            BoundaryContractId.of("typed-direct-interop.v1");

    @Test
    void positiveProofIsKernelOnlyAndManualCompatibleDecisionIsNotProven() {
        PhysicalPlanUnit unit = isolatedUnit("unit-a", "decode");
        ProviderCandidate candidate = candidate("provider-a");
        CompatibilityRequest request = CompatibilityRequest.forUnit(unit);
        CompatibilityDecision kernelDecision = CompatibilityKernel.evaluate(request, candidate);
        CompatibilityDecision manualDecision = new CompatibilityDecision(
                CompatibilityDecision.Status.COMPATIBLE,
                request,
                candidate,
                List.of(),
                List.of());

        assertTrue(kernelDecision.kernelProvenCompatible());
        assertFalse(manualDecision.kernelProvenCompatible());
        assertTrue(StaticProviderCompatibilityProof.class.isSealed());
        assertEquals(1, StaticProviderCompatibilityProof.class.getPermittedSubclasses().length);
        Class<?> implementation = StaticProviderCompatibilityProof.class
                .getPermittedSubclasses()[0];
        assertTrue(Arrays.stream(implementation.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())),
                "STATIC_COMPATIBILITY_PROOF_FORGEABLE_COUNT=0");
    }

    @Test
    void feasibilityViewConstructionIsPrivateAndSourcePlanSemanticMismatchFailsClosed() {
        PhysicalPlanUnit semanticsA = isolatedUnit("unit-a", "decode");
        PhysicalPlanUnit semanticsB = isolatedUnit("unit-a", "encode");
        ProviderCandidate candidate = candidate("provider-a");
        PhysicalExecutionPlan sourceA = plan(semanticsA);
        PhysicalExecutionPlan sourceB = plan(semanticsB);
        ProviderFeasibilityView view = view(sourceA, List.of(candidate), List.of());

        assertTrue(Modifier.isFinal(ProviderFeasibilityView.class.getModifiers()));
        assertFalse(ProviderFeasibilityView.class.isRecord());
        assertEquals(0, Arrays.stream(ProviderFeasibilityView.class.getConstructors()).count(),
                "FORGEABLE_FEASIBLE_GRAPH_CONSTRUCTION_COUNT=0");
        assertTrue(view.bindsExactSourcePlan(sourceA));
        assertFalse(view.bindsExactSourcePlan(sourceB),
                "FEASIBILITY_VIEW_SOURCE_PLAN_MISMATCH_ACCEPTANCE_COUNT=0");
        assertFalse(view.isStaticallyFeasible(semanticsB, candidate.bindingPin()));
        assertThrows(IllegalArgumentException.class,
                () -> view.requireStaticallyFeasible(semanticsB, candidate),
                "COMPATIBILITY_PROOF_UNIT_SEMANTIC_MISMATCH_ACCEPTANCE_COUNT=0");
    }

    @Test
    void providerProofMismatchFailsClosed() {
        PhysicalPlanUnit unit = isolatedUnit("unit-a", "decode");
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView view = view(plan(unit), List.of(providerA), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> view.requireStaticallyFeasible(unit, providerB),
                "COMPATIBILITY_PROOF_PROVIDER_MISMATCH_ACCEPTANCE_COUNT=0");
    }

    @Test
    void transitionAlgebraAppliesSameBindingCrossProviderAndExplicitEvidence() {
        UnitPair pair = dependentPair();
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");

        ProviderFeasibilityView defaults = view(
                plan, List.of(providerA, providerB), List.of());
        assertEquals(ProviderCompatibilityTransitionDecision.ARTIFACT_MATERIALIZATION_REQUIRED,
                transition(defaults, pair, providerA, providerA).decision());
        assertEquals(ProviderCompatibilityTransitionDecision.ARTIFACT_MATERIALIZATION_REQUIRED,
                transition(defaults, pair, providerA, providerB).decision(),
                "CROSS_PROVIDER_DIRECT_WITHOUT_TYPED_INTEROP_PROOF_COUNT=0");

        assertEquals(ProviderCompatibilityTransitionDecision.DIRECT_COMPATIBLE,
                transition(view(plan, List.of(providerA, providerB), List.of(declaration(
                                pair, providerA, providerB,
                                Declaration.DIRECT_INTEROPERABILITY_ALLOWED))),
                        pair, providerA, providerB).decision());
        assertEquals(ProviderCompatibilityTransitionDecision.INCOMPATIBLE,
                transition(view(plan, List.of(providerA, providerB), List.of(declaration(
                                pair, providerA, providerB, Declaration.INCOMPATIBLE))),
                        pair, providerA, providerB).decision());
        assertEquals(ProviderCompatibilityTransitionDecision.UNKNOWN_FAIL_CLOSED,
                transition(view(plan, List.of(providerA, providerB), List.of(declaration(
                                pair, providerA, providerB, Declaration.UNKNOWN_FAIL_CLOSED))),
                        pair, providerA, providerB).decision());
    }

    @Test
    void everyCandidatePairAndInputPermutationIsDeterministic() {
        UnitPair pair = dependentPair();
        PhysicalExecutionPlan plan = plan(pair.consumer(), pair.producer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        List<ProviderBoundaryCompatibilityDeclaration> declarations = List.of(
                declaration(pair, providerA, providerA, Declaration.INCOMPATIBLE),
                declaration(pair, providerA, providerB, Declaration.DIRECT_INTEROPERABILITY_ALLOWED),
                declaration(pair, providerB, providerA, Declaration.UNKNOWN_FAIL_CLOSED),
                declaration(pair, providerB, providerB, Declaration.ARTIFACT_MATERIALIZATION_REQUIRED));

        ProviderFeasibilityView first = ProviderFeasibilityView.build(
                plan,
                List.of(
                        CompatibilityRequest.forUnit(pair.consumer()),
                        CompatibilityRequest.forUnit(pair.producer())),
                List.of(providerA, providerB),
                declarations);
        ProviderFeasibilityView permuted = ProviderFeasibilityView.build(
                plan,
                List.of(
                        CompatibilityRequest.forUnit(pair.producer()),
                        CompatibilityRequest.forUnit(pair.consumer())),
                List.of(providerB, providerA),
                List.of(
                        declarations.get(3), declarations.get(1),
                        declarations.get(0), declarations.get(2)));

        assertEquals(4, first.transitions().size(),
                "PROVIDER_FEASIBILITY_VIEW_TRANSITION_MISSING_COUNT=0");
        assertEquals(first.sourcePlanSemanticDigest(), permuted.sourcePlanSemanticDigest());
        assertEquals(first.unitCandidates(), permuted.unitCandidates());
        assertEquals(first.transitions(), permuted.transitions());
    }

    private static ProviderCompatibilityTransition transition(
            ProviderFeasibilityView view,
            UnitPair pair,
            ProviderCandidate producer,
            ProviderCandidate consumer) {
        return view.requireTransition(
                pair.edge(), pair.producer(), producer.bindingPin(),
                pair.consumer(), consumer.bindingPin());
    }

    private static ProviderBoundaryCompatibilityDeclaration declaration(
            UnitPair pair,
            ProviderCandidate producer,
            ProviderCandidate consumer,
            Declaration declaration) {
        return new ProviderBoundaryCompatibilityDeclaration(
                pair.edge(),
                producer.bindingPin(),
                consumer.bindingPin(),
                DIRECT_CONTRACT,
                declaration);
    }

    private static ProviderFeasibilityView view(
            PhysicalExecutionPlan plan,
            List<ProviderCandidate> candidates,
            List<ProviderBoundaryCompatibilityDeclaration> declarations) {
        return ProviderFeasibilityView.build(
                plan,
                plan.units().stream().map(CompatibilityRequest::forUnit).toList(),
                candidates,
                declarations);
    }

    private static ProviderCandidate candidate(String provider) {
        ProviderId providerId = ProviderId.of(provider);
        ProviderImplementationId implementationId =
                ProviderImplementationId.of(provider + ".native");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion =
                ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId,
                implementationId,
                version,
                contractVersion,
                profileReference,
                List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                providerId,
                implementationId,
                version,
                contractVersion,
                profileReference);
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of());
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(
                profileReference, List.of());
        ProviderStaticCompatibility staticCompatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(DIRECT_CONTRACT),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(
                binding, descriptor, contract, profile, staticCompatibility);
    }

    private static PhysicalExecutionPlan plan(PhysicalPlanUnit... units) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("correction-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("correction-fingerprint"),
                List.of(units),
                null,
                new PhysicalExecutionPlanDigest("declared-digest"));
    }

    private static PhysicalPlanUnit isolatedUnit(String id, String operationKey) {
        return unit(id, operationKey, List.of(), List.of(), List.of());
    }

    private static UnitPair dependentPair() {
        LogicalDependencyEdge edge = new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-a-b"),
                "logical-unit-a",
                "logical-unit-b",
                new RenderNodeId("render-unit-a"),
                new RenderNodeId("render-unit-b"),
                new RenderDependency.DecodedFrames());
        OutputDeclaration output = new OutputDeclaration(
                new ExecutionOutputId("output-unit-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                List.of(), List.of(), List.of(), List.of());
        PhysicalPlanUnit producer = unit(
                "unit-a", "decode", List.of(), List.of(output), List.of(edge));
        InputBinding input = new InputBinding(
                new ExecutionInputId("input-a-b"),
                "logical-unit-b",
                new ExecutionStepId("unit-b"),
                new RenderNodeId("render-unit-b"),
                "logical-unit-a",
                new ExecutionStepId("unit-a"),
                new RenderNodeId("render-unit-a"),
                edge.dependencyVariant(),
                null,
                null);
        PhysicalPlanUnit consumer = unit(
                "unit-b", "decode", List.of(input), List.of(), List.of(edge));
        return new UnitPair(producer, consumer, edge);
    }

    private static PhysicalPlanUnit unit(
            String id,
            String operationKey,
            List<InputBinding> inputs,
            List<OutputDeclaration> outputs,
            List<LogicalDependencyEdge> dependencies) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                new RenderNodeKind.Decode(),
                operationKey,
                inputs,
                outputs,
                dependencies,
                null,
                null,
                List.of(),
                List.of(),
                null,
                true);
    }

    private record UnitPair(
            PhysicalPlanUnit producer,
            PhysicalPlanUnit consumer,
            LogicalDependencyEdge edge) {
    }
}
