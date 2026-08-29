package com.example.platform.execution.taskgraph;

import com.example.platform.execution.compatibility.CompatibilityDecision;
import com.example.platform.execution.compatibility.CompatibilityKernel;
import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.compatibility.StaticCompatibilityFailure;
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
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Correction1ProofChainAndCrossProviderEtgTest {

    private static final BoundaryContractId DIRECT_CONTRACT =
            BoundaryContractId.of("typed-direct-interop.v1");
    private static final CapabilityId REQUIRED_CAPABILITY =
            CapabilityId.of("media.required.missing");

    @Test
    void staticallyIncompatibleProviderCannotReachSingleOrMultiTaskConstruction() {
        UnitPair pair = dependentPair(true);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate incompatible = candidate("provider-a");
        CompatibilityDecision kernelDecision = CompatibilityKernel.evaluate(
                CompatibilityRequest.forUnit(pair.producer()), incompatible);
        ProviderFeasibilityView view = view(plan, List.of(incompatible), List.of());

        assertEquals(CompatibilityDecision.Status.INCOMPATIBLE, kernelDecision.status());
        assertEquals(List.of(StaticCompatibilityFailure.CAPABILITY_UNSUPPORTED),
                kernelDecision.reasons());
        assertThrows(IllegalArgumentException.class,
                () -> compositionRequest(view, incompatible, List.of(pair.producer())),
                "SINGLE_MEMBER_WITHOUT_STATIC_COMPATIBILITY_ACCEPTANCE_COUNT=0;"
                        + "STATIC_INCOMPATIBLE_PROVIDER_EXECUTABLE_TASK_ACCEPTANCE_COUNT=0");
        assertThrows(IllegalArgumentException.class,
                () -> compositionRequest(
                        view, incompatible, List.of(pair.producer(), pair.consumer())),
                "STATIC_INCOMPATIBLE_PROVIDER_ETG_ACCEPTANCE_COUNT=0");
    }

    @Test
    void requiredCrossProviderBoundaryPassesAndLowersToTaskOwnedActions() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView view = view(
                plan, List.of(providerA, providerB), List.of());
        ExecutableTask producer = task(view, providerA, pair.producer());
        ExecutableTask consumer = task(view, providerB, pair.consumer());
        ExecutionArtifactBoundary boundary = boundary(pair, providerA, providerB);

        ProviderBoundExecutableTaskGraph etg = ProviderBoundExecutableTaskGraph.derive(
                plan, view, List.of(consumer, producer), List.of(boundary));

        assertEquals(List.of(boundary), etg.executionArtifactBoundaries());
        assertEquals(1, etg.taskDependencies().size());
        assertEquals(1, etg.selectedProviderTransitions().size());
        assertFalse(ExecutionArtifactBoundary.INDEPENDENTLY_SCHEDULABLE);
        assertFalse(MandatoryArtifactBoundary.class.isAssignableFrom(
                ExecutionArtifactBoundary.class));
        assertTrue(List.of(ExecutionArtifactBoundary.class.getRecordComponents()).stream()
                .noneMatch(component -> component.getType().getSimpleName().equals("ArtifactId")),
                "ACTUAL_OUTPUT_ARTIFACT_PIN_IN_PREEXECUTION_ETG_COUNT=0");
        assertNotEquals(
                List.of(producer.id(), consumer.id()).stream().sorted().toList(),
                etg.tasks().stream().map(ExecutableTask::id).toList());
        List<BoundaryAction> crossActions = etg.tasks().stream()
                .flatMap(task -> task.boundaryActions().stream())
                .filter(action -> action.target()
                                instanceof BoundaryAction.ExecutionArtifactMaterializeTarget
                        || action.target() instanceof BoundaryAction.ExecutionArtifactAcquireTarget)
                .toList();
        assertEquals(2, crossActions.size());
        assertTrue(crossActions.stream().anyMatch(action ->
                action.phase() == BoundaryAction.Phase.POST_EXECUTION));
        assertTrue(crossActions.stream().anyMatch(action ->
                action.phase() == BoundaryAction.Phase.PRE_EXECUTION));
        crossActions.forEach(action -> {
            if (action.target() instanceof BoundaryAction.ExecutionArtifactMaterializeTarget target) {
                assertSame(boundary, target.boundary());
            } else if (action.target() instanceof BoundaryAction.ExecutionArtifactAcquireTarget target) {
                assertSame(boundary, target.boundary());
            }
        });
    }

    @Test
    void bareRequiredCrossProviderDependencyFailsClosed() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView view = view(
                plan, List.of(providerA, providerB), List.of());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        view,
                        List.of(
                                task(view, providerA, pair.producer()),
                                task(view, providerB, pair.consumer())),
                        List.of()),
                "BARE_CROSS_PROVIDER_DEPENDENCY_ACCEPTANCE_COUNT=0;"
                        + "CROSS_PROVIDER_REQUIRED_MATERIALIZATION_MISSING_COUNT=0");
        assertTrue(failure.getMessage().contains("requires explicit"));
    }

    @Test
    void incompatibleAndUnknownTransitionsRejectEtgEvenWithBoundary() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ExecutionArtifactBoundary boundary = boundary(pair, providerA, providerB);

        ProviderFeasibilityView incompatible = view(
                plan,
                List.of(providerA, providerB),
                List.of(declaration(pair, providerA, providerB, Declaration.INCOMPATIBLE)));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        incompatible,
                        List.of(
                                task(incompatible, providerA, pair.producer()),
                                task(incompatible, providerB, pair.consumer())),
                        List.of(boundary)),
                "INCOMPATIBLE_PROVIDER_TRANSITION_ETG_ACCEPTANCE_COUNT=0");

        ProviderFeasibilityView unknown = view(
                plan,
                List.of(providerA, providerB),
                List.of(declaration(pair, providerA, providerB, Declaration.UNKNOWN_FAIL_CLOSED)));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        unknown,
                        List.of(
                                task(unknown, providerA, pair.producer()),
                                task(unknown, providerB, pair.consumer())),
                        List.of(boundary)),
                "UNKNOWN_PROVIDER_TRANSITION_ETG_ACCEPTANCE_COUNT=0");
    }

    @Test
    void typedDirectCrossProviderTransitionAllowsDependencyWithoutMaterialization() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView view = view(
                plan,
                List.of(providerA, providerB),
                List.of(declaration(
                        pair, providerA, providerB,
                        Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));

        ProviderBoundExecutableTaskGraph etg = ProviderBoundExecutableTaskGraph.derive(
                plan,
                view,
                List.of(
                        task(view, providerA, pair.producer()),
                        task(view, providerB, pair.consumer())),
                List.of());

        assertEquals(1, etg.taskDependencies().size());
        assertTrue(etg.executionArtifactBoundaries().isEmpty());
    }

    @Test
    void crossProviderMaterializationSemanticsChangeEtgDigest() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView materializingView = view(
                plan, List.of(providerA, providerB), List.of());
        ProviderBoundExecutableTaskGraph materializing = ProviderBoundExecutableTaskGraph.derive(
                plan,
                materializingView,
                List.of(
                        task(materializingView, providerA, pair.producer()),
                        task(materializingView, providerB, pair.consumer())),
                List.of(boundary(pair, providerA, providerB)));
        ProviderFeasibilityView directView = view(
                plan,
                List.of(providerA, providerB),
                List.of(declaration(
                        pair, providerA, providerB,
                        Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));
        ProviderBoundExecutableTaskGraph direct = ProviderBoundExecutableTaskGraph.derive(
                plan,
                directView,
                List.of(
                        task(directView, providerA, pair.producer()),
                        task(directView, providerB, pair.consumer())),
                List.of());

        assertNotEquals(materializing.digest(), direct.digest());
    }

    @Test
    void foreignBindingCrossProviderActionIsRejected() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderCandidate foreignProvider = candidate("provider-foreign");
        ProviderFeasibilityView view = view(
                plan, List.of(providerA, providerB, foreignProvider), List.of());
        ExecutionArtifactBoundary selectedBoundary = boundary(pair, providerA, providerB);
        ExecutionArtifactBoundary foreignBoundary = boundary(
                pair, providerA, foreignProvider);
        ExecutableTask producer = task(view, providerA, pair.producer());
        ExecutableTask producerWithForeignAction = ExecutableTask.create(
                producer.compositionDecision(),
                List.of(materializeAction(foreignBoundary, 0)));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        view,
                        List.of(
                                producerWithForeignAction,
                                task(view, providerB, pair.consumer())),
                        List.of(selectedBoundary)));

        assertTrue(failure.getMessage().contains("no exact canonical boundary"));
    }

    @Test
    void unmanifestedCrossProviderActionsAreRejected() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView directView = view(
                plan,
                List.of(providerA, providerB),
                List.of(declaration(
                        pair,
                        providerA,
                        providerB,
                        Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));
        ExecutionArtifactBoundary unmanifested = boundary(pair, providerA, providerB);
        ExecutableTask producer = task(directView, providerA, pair.producer());
        ExecutableTask consumer = task(directView, providerB, pair.consumer());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        directView,
                        List.of(
                                ExecutableTask.create(
                                        producer.compositionDecision(),
                                        List.of(materializeAction(unmanifested, 0))),
                                ExecutableTask.create(
                                        consumer.compositionDecision(),
                                        List.of(acquireAction(unmanifested, 0)))),
                        List.of()));

        assertTrue(failure.getMessage().contains("no exact canonical boundary"));
    }

    @Test
    void duplicatePreAttachedCrossProviderActionsAreRejected() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView view = view(
                plan, List.of(providerA, providerB), List.of());
        ExecutionArtifactBoundary boundary = boundary(pair, providerA, providerB);
        ExecutableTask producer = task(view, providerA, pair.producer());
        ExecutableTask consumer = task(view, providerB, pair.consumer());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        view,
                        List.of(
                                ExecutableTask.create(
                                        producer.compositionDecision(),
                                        List.of(
                                                materializeAction(boundary, 0),
                                                materializeAction(boundary, 1))),
                                ExecutableTask.create(
                                        consumer.compositionDecision(),
                                        List.of(acquireAction(boundary, 0)))),
                        List.of(boundary)));

        assertTrue(failure.getMessage().contains("exactly one producer"));
    }

    @Test
    void reDerivationCanonicalizesCrossProviderActionsIdempotently() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ProviderCandidate providerA = candidate("provider-a");
        ProviderCandidate providerB = candidate("provider-b");
        ProviderFeasibilityView view = view(
                plan, List.of(providerA, providerB), List.of());
        ExecutionArtifactBoundary boundary = boundary(pair, providerA, providerB);
        ProviderBoundExecutableTaskGraph first = ProviderBoundExecutableTaskGraph.derive(
                plan,
                view,
                List.of(
                        task(view, providerA, pair.producer()),
                        task(view, providerB, pair.consumer())),
                List.of(boundary));

        ProviderBoundExecutableTaskGraph second = ProviderBoundExecutableTaskGraph.derive(
                plan, view, first.tasks(), List.of(boundary));

        assertEquals(first.digest(), second.digest());
        assertEquals(
                first.tasks().stream().map(ExecutableTask::id).toList(),
                second.tasks().stream().map(ExecutableTask::id).toList());
        assertEquals(
                first.tasks().stream().map(ExecutableTask::boundaryActions).toList(),
                second.tasks().stream().map(ExecutableTask::boundaryActions).toList());
    }

    private static ExecutableTask task(
            ProviderFeasibilityView view,
            ProviderCandidate candidate,
            PhysicalPlanUnit... units) {
        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(
                compositionRequest(view, candidate, List.of(units)));
        return ExecutableTask.create(decision, List.of());
    }

    private static ProviderLocalCompositionRequest compositionRequest(
            ProviderFeasibilityView view,
            ProviderCandidate candidate,
            List<PhysicalPlanUnit> units) {
        return ProviderLocalCompositionRequest.of(
                ExecutableTaskMembership.canonicalForUnits(units),
                view,
                candidate,
                new ProviderCompositionDeclaration(
                        candidate.bindingPin(), NativePipelineSupport.SUPPORTED),
                List.of());
    }

    private static ExecutionArtifactBoundary boundary(
            UnitPair pair,
            ProviderCandidate producer,
            ProviderCandidate consumer) {
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
                ExecutionArtifactBoundary.MaterializationReason.PROVIDER_BINDING_CHANGE,
                Optional.empty());
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
                new ExecutionPlanId("correction-etg-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("correction-etg-fingerprint"),
                List.of(units),
                null,
                new PhysicalExecutionPlanDigest("declared-digest"));
    }

    private static UnitPair dependentPair(boolean requiresMissingCapability) {
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
        List<CapabilityRequirementRef> capability = requiresMissingCapability
                ? List.of(new CapabilityRequirementRef(CapabilityRequirement.of(
                        REQUIRED_CAPABILITY,
                        ContractVersionRange.exactly(ContractVersion.of(1, 0)))))
                : List.of();
        PhysicalPlanUnit producer = unit(
                "unit-a", List.of(), List.of(output), List.of(edge), capability);
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
                "unit-b", List.of(input), List.of(), List.of(edge), capability);
        return new UnitPair(producer, consumer, edge);
    }

    private static PhysicalPlanUnit unit(
            String id,
            List<InputBinding> inputs,
            List<OutputDeclaration> outputs,
            List<LogicalDependencyEdge> dependencies,
            List<CapabilityRequirementRef> capabilities) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                new RenderNodeKind.Decode(),
                "decode",
                inputs,
                outputs,
                dependencies,
                null,
                null,
                capabilities,
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
