package com.example.platform.execution.taskgraph;

import com.example.platform.execution.compatibility.CompatibilityDecision;
import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderCompatibilityGraph;
import com.example.platform.execution.compatibility.ProviderCompatibilityTransition;
import com.example.platform.execution.compatibility.ProviderCompatibilityTransitionDecision;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.ProviderRuntimeClass;
import com.example.platform.execution.composition.CompositionBlocker;
import com.example.platform.execution.composition.CompositionDecision;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.composition.ProviderCompositionDeclaration;
import com.example.platform.execution.composition.ProviderCompositionDeclaration.NativePipelineSupport;
import com.example.platform.execution.composition.ProviderLocalCompositionEvaluator;
import com.example.platform.execution.composition.ProviderLocalCompositionRequest;
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
import com.example.platform.render.domain.renderplan.AudioProcessMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adversarial closure suite for Correction 2 requirements T1-T31. */
class Correction2SameBindingRuntimeBoundaryTest {

    private static final BoundaryContractId DIRECT_CONTRACT =
            BoundaryContractId.of("typed-direct-interop.v1");
    private static final List<ProviderRuntimeClass> ALL_RUNTIME_CLASSES =
            List.of(ProviderRuntimeClass.values());

    @Test
    void t1SameBindingProviderLocalCompositionIsInternalAndBoundaryFree() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate provider = candidate("provider-a");
        ProviderCompatibilityGraph graph = graph(plan, List.of(provider), List.of());

        ProviderBoundExecutableTaskGraph etg = ProviderBoundExecutableTaskGraph.derive(
                plan, graph, List.of(task(graph, provider, pair.producer(), pair.consumer())), List.of());

        assertEquals(1, etg.providerLocalDependencies().size());
        assertTrue(etg.taskDependencies().isEmpty());
        assertTrue(etg.selectedProviderTransitions().isEmpty());
        assertTrue(etg.executionArtifactBoundaries().isEmpty(),
                "INTERNAL_PROVIDER_LOCAL_COMPOSITION_FALSE_MATERIALIZATION_COUNT=0");
    }

    @Test
    void t2SameBindingSeparateTasksDefaultToMaterialization() {
        Scenario scenario = sameBindingScenario();
        assertEquals(ProviderCompatibilityTransitionDecision.ARTIFACT_MATERIALIZATION_REQUIRED,
                transition(scenario.graph(), scenario.pair(), scenario.producer(), scenario.consumer())
                        .decision(),
                "SAME_BINDING_DEFAULT_DIRECT_TRANSITION_COUNT=0");
    }

    @Test
    void t3SameBindingSeparateTasksWithoutBoundaryAreRejected() {
        Scenario scenario = sameBindingScenario();
        assertThrows(IllegalArgumentException.class,
                () -> deriveSeparate(scenario, List.of()),
                "SAME_BINDING_EXTERNAL_DEPENDENCY_WITHOUT_BOUNDARY_ACCEPTANCE_COUNT=0");
    }

    @Test
    void t4SameBindingSeparateTasksWithExactBoundaryAreAccepted() {
        Scenario scenario = sameBindingScenario();
        ExecutionArtifactBoundary boundary = boundary(
                scenario.pair(), scenario.producer(), scenario.consumer(),
                ExecutionArtifactBoundary.MaterializationReason
                        .INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN,
                Optional.empty());

        ProviderBoundExecutableTaskGraph etg = deriveSeparate(scenario, List.of(boundary));

        assertEquals(List.of(boundary), etg.executionArtifactBoundaries(),
                "SAME_BINDING_EXTERNAL_DEPENDENCY_WITH_BOUNDARY_REJECTION_COUNT=0");
        assertEquals(1, etg.taskDependencies().size());
    }

    @Test
    void t5SameBindingNativeToRemoteRuntimeConstraintIsNotDirect() {
        assertRuntimeConstraintPairMaterializes(
                ProviderRuntimeClass.NATIVE_PROCESS, ProviderRuntimeClass.REMOTE_SERVICE);
    }

    @Test
    void t6SameBindingContainerToNativeRuntimeConstraintIsNotDirect() {
        assertRuntimeConstraintPairMaterializes(
                ProviderRuntimeClass.CONTAINERIZED, ProviderRuntimeClass.NATIVE_PROCESS);
    }

    @Test
    void t7SameBindingAmbiguousRuntimeSeparateTasksAreNotDirect() {
        Scenario scenario = sameBindingScenario();
        assertNotEquals(ProviderCompatibilityTransitionDecision.DIRECT_COMPATIBLE,
                selectedTransition(scenario).decision(),
                "SAME_BINDING_UNKNOWN_RUNTIME_BOUNDARY_DIRECT_ACCEPTANCE_COUNT=0");
    }

    @Test
    void t8DifferentBindingWithoutDeclarationRequiresMaterialization() {
        Scenario scenario = differentBindingScenario(List.of());
        assertEquals(ProviderCompatibilityTransitionDecision.ARTIFACT_MATERIALIZATION_REQUIRED,
                selectedTransition(scenario).decision());
    }

    @Test
    void t9DifferentBindingWithoutBoundaryIsRejected() {
        Scenario scenario = differentBindingScenario(List.of());
        assertThrows(IllegalArgumentException.class, () -> deriveSeparate(scenario, List.of()));
    }

    @Test
    void t10DifferentBindingWithExactBoundaryIsAccepted() {
        Scenario scenario = differentBindingScenario(List.of());
        ExecutionArtifactBoundary boundary = boundary(
                scenario.pair(), scenario.producer(), scenario.consumer(),
                ExecutionArtifactBoundary.MaterializationReason.PROVIDER_BINDING_CHANGE,
                Optional.empty());
        assertEquals(List.of(boundary),
                deriveSeparate(scenario, List.of(boundary)).executionArtifactBoundaries());
    }

    @Test
    void t11SameBindingTypedDirectWithBilateralSupportIsBoundaryFree() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate provider = candidate("provider-a");
        ProviderCompatibilityGraph graph = graph(
                plan,
                List.of(provider),
                List.of(declaration(pair, provider, provider,
                        Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));
        Scenario scenario = new Scenario(plan, pair, provider, provider, graph);

        assertEquals(ProviderCompatibilityTransitionDecision.DIRECT_COMPATIBLE,
                selectedTransition(scenario).decision());
        assertTrue(deriveSeparate(scenario, List.of()).executionArtifactBoundaries().isEmpty());
    }

    @Test
    void t12DifferentBindingTypedDirectWithBilateralSupportIsDirect() {
        UnitPair pair = dependentPair(false);
        ProviderCandidate producer = candidate("provider-a");
        ProviderCandidate consumer = candidate("provider-b");
        Scenario scenario = differentBindingScenario(List.of(
                declaration(pair, producer, consumer,
                        Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));

        assertEquals(ProviderCompatibilityTransitionDecision.DIRECT_COMPATIBLE,
                selectedTransition(scenario).decision());
        assertTrue(deriveSeparate(scenario, List.of()).executionArtifactBoundaries().isEmpty());
    }

    @Test
    void t13DirectDeclarationFailsWhenProducerLacksContract() {
        assertDirectDeclarationMissingSupportIsIncompatible(false, true);
    }

    @Test
    void t14DirectDeclarationFailsWhenConsumerLacksContract() {
        assertDirectDeclarationMissingSupportIsIncompatible(true, false);
    }

    @Test
    void t15ExplicitIncompatibleTransitionRejectsEtg() {
        assertExplicitFailClosed(Declaration.INCOMPATIBLE);
    }

    @Test
    void t16ExplicitUnknownTransitionRejectsEtg() {
        assertExplicitFailClosed(Declaration.UNKNOWN_FAIL_CLOSED);
    }

    @Test
    void t17ExplicitSameBindingMaterializationIsRepresentable() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate provider = candidate("provider-a");
        ProviderCompatibilityGraph graph = graph(
                plan,
                List.of(provider),
                List.of(declaration(pair, provider, provider,
                        Declaration.ARTIFACT_MATERIALIZATION_REQUIRED)));
        Scenario scenario = new Scenario(plan, pair, provider, provider, graph);
        ExecutionArtifactBoundary boundary = boundary(
                pair, provider, provider,
                ExecutionArtifactBoundary.MaterializationReason
                        .EXPLICIT_MATERIALIZATION_REQUIREMENT,
                Optional.of(DIRECT_CONTRACT));

        assertEquals(List.of(boundary),
                deriveSeparate(scenario, List.of(boundary)).executionArtifactBoundaries(),
                "SAME_BINDING_REQUIRED_MATERIALIZATION_UNREPRESENTABLE_COUNT=0");
    }

    @Test
    void t18ForeignBindingExecutionBoundaryIsRejected() {
        Scenario scenario = differentBindingScenario(List.of());
        ProviderCandidate foreign = candidate("provider-foreign");
        ProviderCompatibilityGraph graph = graph(
                scenario.plan(),
                List.of(scenario.producer(), scenario.consumer(), foreign),
                List.of());
        Scenario graphScenario = new Scenario(
                scenario.plan(), scenario.pair(), scenario.producer(), scenario.consumer(), graph);
        ExecutionArtifactBoundary foreignBoundary = boundary(
                scenario.pair(), scenario.producer(), foreign,
                ExecutionArtifactBoundary.MaterializationReason.PROVIDER_BINDING_CHANGE,
                Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> deriveSeparate(graphScenario, List.of(foreignBoundary)));

        ExecutionArtifactBoundary exact = defaultBoundary(scenario);
        ExecutableTask producerTask = task(
                scenario.graph(), scenario.producer(), scenario.pair().producer());
        ExecutableTask consumerTask = task(
                scenario.graph(), scenario.consumer(), scenario.pair().consumer());
        assertThrows(IllegalArgumentException.class, () -> ExecutableTask.create(
                producerTask.compositionDecision(),
                List.of(new BoundaryAction(
                        BoundaryAction.Phase.PRE_EXECUTION,
                        0,
                        new BoundaryAction.ExecutionArtifactMaterializeTarget(exact)))));
        assertThrows(IllegalArgumentException.class, () -> ExecutableTask.create(
                consumerTask.compositionDecision(), List.of(materializeAction(exact, 0))));

        OutputDeclaration wrongOutput = new OutputDeclaration(
                new ExecutionOutputId("wrong-output"),
                exact.producerOutput().logicalNodeId(),
                exact.producerOutput().sourceRenderNodeId(),
                exact.producerOutput().outputRequirements(),
                exact.producerOutput().materializationRequirements(),
                exact.producerOutput().intermediateArtifactExpectations(),
                exact.producerOutput().finalArtifactExpectations());
        ExecutionArtifactBoundary wrongOutputBoundary = new ExecutionArtifactBoundary(
                exact.sourceDependency(), exact.producerUnitId(), exact.consumerUnitId(),
                exact.producerBindingPin(), exact.consumerBindingPin(),
                wrongOutput, exact.consumerInput(), exact.materializationContract(),
                exact.reason(), exact.interoperabilityContract());
        assertThrows(IllegalArgumentException.class, () -> ExecutableTask.create(
                producerTask.compositionDecision(),
                List.of(materializeAction(wrongOutputBoundary, 0))));

        InputBinding input = exact.consumerInput();
        InputBinding wrongInput = new InputBinding(
                new ExecutionInputId("wrong-input"),
                input.consumerLogicalNodeId(), input.consumerStepId(), input.consumerRenderNodeId(),
                input.producerLogicalNodeId(), input.producerStepId(), input.producerRenderNodeId(),
                input.dependencyVariant(), input.sourceArtifact(), input.requiredSampleWindow());
        ExecutionArtifactBoundary wrongInputBoundary = new ExecutionArtifactBoundary(
                exact.sourceDependency(), exact.producerUnitId(), exact.consumerUnitId(),
                exact.producerBindingPin(), exact.consumerBindingPin(),
                exact.producerOutput(), wrongInput, exact.materializationContract(),
                exact.reason(), exact.interoperabilityContract());
        assertThrows(IllegalArgumentException.class, () -> ExecutableTask.create(
                consumerTask.compositionDecision(),
                List.of(acquireAction(wrongInputBoundary, 0))));
    }

    @Test
    void t19UnmanifestedExecutionBoundaryActionsAreRejected() {
        Scenario scenario = directDifferentBindingScenario();
        ExecutionArtifactBoundary unmanifested = boundary(
                scenario.pair(), scenario.producer(), scenario.consumer(),
                ExecutionArtifactBoundary.MaterializationReason.PROVIDER_BINDING_CHANGE,
                Optional.empty());
        ExecutableTask producer = task(
                scenario.graph(), scenario.producer(), scenario.pair().producer());
        ExecutableTask consumer = task(
                scenario.graph(), scenario.consumer(), scenario.pair().consumer());

        assertThrows(IllegalArgumentException.class, () -> ProviderBoundExecutableTaskGraph.derive(
                scenario.plan(), scenario.graph(),
                List.of(
                        ExecutableTask.create(producer.compositionDecision(),
                                List.of(materializeAction(unmanifested, 0))),
                        ExecutableTask.create(consumer.compositionDecision(),
                                List.of(acquireAction(unmanifested, 0)))),
                List.of()));
    }

    @Test
    void t20DuplicateProducerMaterializeActionIsRejected() {
        Scenario scenario = differentBindingScenario(List.of());
        ExecutionArtifactBoundary boundary = defaultBoundary(scenario);
        assertPreAttachedRejected(
                scenario,
                List.of(materializeAction(boundary, 0), materializeAction(boundary, 1)),
                List.of(acquireAction(boundary, 0)),
                boundary);
    }

    @Test
    void t21DuplicateConsumerAcquireActionIsRejected() {
        Scenario scenario = differentBindingScenario(List.of());
        ExecutionArtifactBoundary boundary = defaultBoundary(scenario);
        assertPreAttachedRejected(
                scenario,
                List.of(materializeAction(boundary, 0)),
                List.of(acquireAction(boundary, 0), acquireAction(boundary, 1)),
                boundary);
    }

    @Test
    void t22MissingEitherPreAttachedBoundaryActionSideIsRejected() {
        Scenario scenario = differentBindingScenario(List.of());
        ExecutionArtifactBoundary boundary = defaultBoundary(scenario);
        assertPreAttachedRejected(
                scenario, List.of(materializeAction(boundary, 0)), List.of(), boundary);
        assertPreAttachedRejected(
                scenario, List.of(), List.of(acquireAction(boundary, 0)), boundary);
    }

    @Test
    void t23EtgRederivationIsIdempotent() {
        Scenario scenario = sameBindingScenario();
        ExecutionArtifactBoundary boundary = defaultBoundary(scenario);
        ProviderBoundExecutableTaskGraph first = deriveSeparate(scenario, List.of(boundary));
        ProviderBoundExecutableTaskGraph second = ProviderBoundExecutableTaskGraph.derive(
                scenario.plan(), scenario.graph(), first.tasks(), List.of(boundary));

        assertEquals(first.digest(), second.digest());
        assertEquals(first.tasks().stream().map(ExecutableTask::id).toList(),
                second.tasks().stream().map(ExecutableTask::id).toList());
        assertEquals(first.tasks().stream().map(ExecutableTask::boundaryActions).toList(),
                second.tasks().stream().map(ExecutableTask::boundaryActions).toList());
    }

    @Test
    void t24GraphInputsAndDeclarationsArePermutationDeterministic() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        List<ProviderBoundaryCompatibilityDeclaration> declarations = List.of(
                declaration(pair, providerA, providerA, Declaration.INCOMPATIBLE),
                declaration(pair, providerA, providerB, Declaration.DIRECT_INTEROPERABILITY_ALLOWED),
                declaration(pair, providerB, providerA, Declaration.UNKNOWN_FAIL_CLOSED),
                declaration(pair, providerB, providerB, Declaration.ARTIFACT_MATERIALIZATION_REQUIRED));

        ProviderCompatibilityGraph first = ProviderCompatibilityGraph.build(
                plan,
                List.of(CompatibilityRequest.forUnit(pair.consumer()),
                        CompatibilityRequest.forUnit(pair.producer())),
                List.of(providerA, providerB),
                declarations);
        ProviderCompatibilityGraph permuted = ProviderCompatibilityGraph.build(
                plan,
                List.of(CompatibilityRequest.forUnit(pair.producer()),
                        CompatibilityRequest.forUnit(pair.consumer())),
                List.of(providerB, providerA),
                List.of(declarations.get(3), declarations.get(1),
                        declarations.get(0), declarations.get(2)));

        assertEquals(first, permuted);
        assertEquals(first.transitions(), permuted.transitions());
        assertArrayEquals(first.canonicalSerialization(), permuted.canonicalSerialization());
        assertEquals(first.digest(), permuted.digest());
    }

    @Test
    void t25CompatibilityProofRejectsSameStepWithModifiedSemantics() {
        UnitPair pair = dependentPair(false);
        ProviderCandidate provider = candidate("provider-a");
        ProviderCompatibilityGraph graph = graph(plan(pair), List.of(provider), List.of());
        PhysicalPlanUnit modified = unit(
                "unit-a", "encode", pair.producer().typedInputs(), pair.producer().typedOutputs(),
                pair.producer().typedDependencies());

        assertThrows(IllegalArgumentException.class,
                () -> graph.requireStaticallyFeasible(modified, provider));
    }

    @Test
    void t26CompatibilityProofRejectsDifferentProviderCandidate() {
        UnitPair pair = dependentPair(false);
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderCompatibilityGraph graph = graph(plan(pair), List.of(providerA), List.of());

        assertThrows(IllegalArgumentException.class,
                () -> graph.requireStaticallyFeasible(pair.producer(), providerB));
    }

    @Test
    void t27ManualCompatibleDecisionCannotBecomeKernelProven() {
        UnitPair pair = dependentPair(false);
        ProviderCandidate provider = candidate("provider-a");
        CompatibilityRequest request = CompatibilityRequest.forUnit(pair.producer());
        CompatibilityDecision manual = new CompatibilityDecision(
                CompatibilityDecision.Status.COMPATIBLE,
                request,
                provider,
                List.of(),
                List.of());

        assertFalse(manual.kernelProvenCompatible());
        assertTrue(manual.staticCompatibilityProof().isEmpty());
    }

    @Test
    void t28ManualAllowedSingleMemberCannotCreateExecutableTask() {
        UnitPair pair = dependentPair(false);
        assertManualAllowedTaskRejected(candidate("provider-a"), List.of(pair.producer()));
    }

    @Test
    void t29ManualAllowedMultiMemberCannotCreateExecutableTask() {
        UnitPair pair = dependentPair(false);
        assertManualAllowedTaskRejected(
                candidate("provider-a"), List.of(pair.producer(), pair.consumer()));
    }

    @Test
    void t30UpstreamMandatoryMaterializationStillBlocksProviderLocalComposition() {
        UnitPair pair = dependentPair(true);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate provider = candidate("provider-a");
        ProviderCompatibilityGraph graph = graph(plan, List.of(provider), List.of());

        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(
                compositionRequest(graph, provider, pair.producer(), pair.consumer()));

        assertEquals(CompositionDecision.Status.FORBIDDEN, decision.status());
        assertTrue(decision.blockers().contains(
                CompositionBlocker.MANDATORY_INTERMEDIATE_ARTIFACT));
    }

    @Test
    void t31GeneralizedBoundaryHasNoPreinventedOutputArtifactId() {
        Scenario scenario = sameBindingScenario();
        ExecutionArtifactBoundary boundary = defaultBoundary(scenario);

        assertTrue(Arrays.stream(ExecutionArtifactBoundary.class.getRecordComponents())
                .noneMatch(component -> component.getType().getSimpleName().equals("ArtifactId")));
        assertEquals(null, boundary.consumerInput().sourceArtifact());
        assertFalse(ExecutionArtifactBoundary.INDEPENDENTLY_SCHEDULABLE);
    }

    private static void assertRuntimeConstraintPairMaterializes(
            ProviderRuntimeClass producerRuntime,
            ProviderRuntimeClass consumerRuntime) {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate provider = candidate("provider-a");
        List<CompatibilityRequest> requests = List.of(
                new CompatibilityRequest(pair.producer(),
                        List.of(new StaticCompatibilityConstraint.ProviderRuntime(producerRuntime))),
                new CompatibilityRequest(pair.consumer(),
                        List.of(new StaticCompatibilityConstraint.ProviderRuntime(consumerRuntime))));
        ProviderCompatibilityGraph graph = ProviderCompatibilityGraph.build(
                plan, requests, List.of(provider), List.of());

        assertEquals(ProviderCompatibilityTransitionDecision.ARTIFACT_MATERIALIZATION_REQUIRED,
                transition(graph, pair, provider, provider).decision(),
                "SAME_BINDING_RUNTIME_CLASS_MISMATCH_DIRECT_ACCEPTANCE_COUNT=0");
    }

    private static void assertDirectDeclarationMissingSupportIsIncompatible(
            boolean producerSupports,
            boolean consumerSupports) {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate producer = candidate("provider-a", producerSupports);
        ProviderCandidate consumer = candidate("provider-b", consumerSupports);
        ProviderCompatibilityGraph graph = graph(
                plan,
                List.of(producer, consumer),
                List.of(declaration(pair, producer, consumer,
                        Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));

        assertEquals(ProviderCompatibilityTransitionDecision.INCOMPATIBLE,
                transition(graph, pair, producer, consumer).decision());
    }

    private static void assertExplicitFailClosed(Declaration declaration) {
        UnitPair pair = dependentPair(false);
        ProviderCandidate producer = candidate("provider-a");
        ProviderCandidate consumer = candidate("provider-b");
        Scenario scenario = differentBindingScenario(List.of(
                declaration(pair, producer, consumer, declaration)));
        assertThrows(IllegalArgumentException.class, () -> deriveSeparate(scenario, List.of()));
    }

    private static void assertPreAttachedRejected(
            Scenario scenario,
            List<BoundaryAction> producerActions,
            List<BoundaryAction> consumerActions,
            ExecutionArtifactBoundary boundary) {
        ExecutableTask producer = task(
                scenario.graph(), scenario.producer(), scenario.pair().producer());
        ExecutableTask consumer = task(
                scenario.graph(), scenario.consumer(), scenario.pair().consumer());
        assertThrows(IllegalArgumentException.class, () -> ProviderBoundExecutableTaskGraph.derive(
                scenario.plan(), scenario.graph(),
                List.of(
                        ExecutableTask.create(producer.compositionDecision(), producerActions),
                        ExecutableTask.create(consumer.compositionDecision(), consumerActions)),
                List.of(boundary)));
    }

    private static void assertManualAllowedTaskRejected(
            ProviderCandidate provider,
            List<PhysicalPlanUnit> units) {
        List<ExecutableTaskMembership> memberships =
                ExecutableTaskMembership.canonicalForUnits(units);
        CompositionDecision manual = new CompositionDecision(
                CompositionDecision.Status.ALLOWED,
                provider.bindingPin(),
                memberships,
                List.of(),
                memberships.stream()
                        .map(ExecutableTaskMembership::failureAttributionMapping)
                        .toList());
        assertThrows(IllegalArgumentException.class,
                () -> ExecutableTask.create(manual, List.of()));
    }

    private static Scenario sameBindingScenario() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate provider = candidate("provider-a");
        return new Scenario(
                plan, pair, provider, provider,
                graph(plan, List.of(provider), List.of()));
    }

    private static Scenario differentBindingScenario(
            List<ProviderBoundaryCompatibilityDeclaration> declarations) {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ProviderCandidate producer = candidate("provider-a");
        ProviderCandidate consumer = candidate("provider-b");
        return new Scenario(
                plan, pair, producer, consumer,
                graph(plan, List.of(producer, consumer), declarations));
    }

    private static Scenario directDifferentBindingScenario() {
        UnitPair pair = dependentPair(false);
        ProviderCandidate producer = candidate("provider-a");
        ProviderCandidate consumer = candidate("provider-b");
        return differentBindingScenario(List.of(declaration(
                pair, producer, consumer, Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));
    }

    private static ProviderBoundExecutableTaskGraph deriveSeparate(
            Scenario scenario,
            List<ExecutionArtifactBoundary> boundaries) {
        return ProviderBoundExecutableTaskGraph.derive(
                scenario.plan(),
                scenario.graph(),
                List.of(
                        task(scenario.graph(), scenario.producer(), scenario.pair().producer()),
                        task(scenario.graph(), scenario.consumer(), scenario.pair().consumer())),
                boundaries);
    }

    private static ProviderCompatibilityTransition selectedTransition(Scenario scenario) {
        return transition(
                scenario.graph(), scenario.pair(), scenario.producer(), scenario.consumer());
    }

    private static ProviderCompatibilityTransition transition(
            ProviderCompatibilityGraph graph,
            UnitPair pair,
            ProviderCandidate producer,
            ProviderCandidate consumer) {
        return graph.requireTransition(
                pair.edge(), pair.producer(), producer.bindingPin(),
                pair.consumer(), consumer.bindingPin());
    }

    private static ExecutionArtifactBoundary defaultBoundary(Scenario scenario) {
        boolean sameBinding = scenario.producer().bindingPin()
                .equals(scenario.consumer().bindingPin());
        return boundary(
                scenario.pair(), scenario.producer(), scenario.consumer(),
                sameBinding
                        ? ExecutionArtifactBoundary.MaterializationReason
                                .INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN
                        : ExecutionArtifactBoundary.MaterializationReason.PROVIDER_BINDING_CHANGE,
                Optional.empty());
    }

    private static ExecutionArtifactBoundary boundary(
            UnitPair pair,
            ProviderCandidate producer,
            ProviderCandidate consumer,
            ExecutionArtifactBoundary.MaterializationReason reason,
            Optional<BoundaryContractId> contract) {
        return new ExecutionArtifactBoundary(
                pair.edge(),
                pair.producer().stepId(),
                pair.consumer().stepId(),
                producer.bindingPin(),
                consumer.bindingPin(),
                pair.producer().typedOutputs().getFirst(),
                pair.consumer().typedInputs().getFirst(),
                ExecutionArtifactBoundary.MaterializationContract
                        .IMMUTABLE_ARTIFACT_AUTHORITY_V1,
                reason,
                contract);
    }

    private static BoundaryAction materializeAction(
            ExecutionArtifactBoundary boundary,
            int order) {
        return new BoundaryAction(
                BoundaryAction.Phase.POST_EXECUTION,
                order,
                new BoundaryAction.ExecutionArtifactMaterializeTarget(boundary));
    }

    private static BoundaryAction acquireAction(
            ExecutionArtifactBoundary boundary,
            int order) {
        return new BoundaryAction(
                BoundaryAction.Phase.PRE_EXECUTION,
                order,
                new BoundaryAction.ExecutionArtifactAcquireTarget(boundary));
    }

    private static ExecutableTask task(
            ProviderCompatibilityGraph graph,
            ProviderCandidate candidate,
            PhysicalPlanUnit... units) {
        return ExecutableTask.create(
                ProviderLocalCompositionEvaluator.evaluate(
                        compositionRequest(graph, candidate, units)),
                List.of());
    }

    private static ProviderLocalCompositionRequest compositionRequest(
            ProviderCompatibilityGraph graph,
            ProviderCandidate candidate,
            PhysicalPlanUnit... units) {
        return ProviderLocalCompositionRequest.of(
                ExecutableTaskMembership.canonicalForUnits(List.of(units)),
                graph,
                candidate,
                new ProviderCompositionDeclaration(
                        candidate.bindingPin(), NativePipelineSupport.SUPPORTED),
                List.of());
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

    private static ProviderCompatibilityGraph graph(
            PhysicalExecutionPlan plan,
            List<ProviderCandidate> candidates,
            List<ProviderBoundaryCompatibilityDeclaration> declarations) {
        return ProviderCompatibilityGraph.build(
                plan,
                plan.units().stream().map(CompatibilityRequest::forUnit).toList(),
                candidates,
                declarations);
    }

    private static ProviderCandidate candidate(String provider) {
        return candidate(provider, true);
    }

    private static ProviderCandidate candidate(String provider, boolean supportsContract) {
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
                List.of(ProviderStaticCompatibility.ArtifactRequirementKind
                        .MANDATORY_MATERIALIZATION),
                List.of(),
                List.of(),
                ALL_RUNTIME_CLASSES,
                List.of(),
                List.of(),
                supportsContract ? List.of(DIRECT_CONTRACT) : List.of(),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(binding, descriptor, contract, profile, staticCompatibility);
    }

    private static PhysicalExecutionPlan plan(UnitPair pair) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("correction-2-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("correction-2-fingerprint"),
                List.of(pair.consumer(), pair.producer()),
                null,
                new PhysicalExecutionPlanDigest("declared-digest"));
    }

    private static UnitPair dependentPair(boolean mandatoryMaterialization) {
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
                List.of(),
                mandatoryMaterialization
                        ? List.of(AudioProcessMaterializationRequirement.of(null, null, null))
                        : List.of(),
                List.of(),
                List.of());
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

    private record Scenario(
            PhysicalExecutionPlan plan,
            UnitPair pair,
            ProviderCandidate producer,
            ProviderCandidate consumer,
            ProviderCompatibilityGraph graph) {
    }
}
