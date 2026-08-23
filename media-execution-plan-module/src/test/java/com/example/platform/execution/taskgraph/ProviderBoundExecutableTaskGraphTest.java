package com.example.platform.execution.taskgraph;

import com.example.platform.execution.composition.CompositionDecision;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.composition.FailureAttribution.MemberAttribution;
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
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderBoundExecutableTaskGraphTest {

    @Test
    void executableTaskIdIsDeterministicAcrossEquivalentMembershipPermutations() {
        UnitPair pair = dependentPair(false);

        ExecutableTask first = task(
                binding("provider-a"),
                List.of(pair.producer(), pair.consumer()),
                List.of());
        ExecutableTask permuted = task(
                binding("provider-a"),
                List.of(pair.consumer(), pair.producer()),
                List.of());

        assertEquals(first.id(), permuted.id());
        assertEquals(
                first.memberships().stream().map(ExecutableTaskMembership::physicalPlanUnitId).toList(),
                permuted.memberships().stream().map(ExecutableTaskMembership::physicalPlanUnitId).toList());
    }

    @Test
    void executableTaskIdIsDeterministicAcrossEquivalentInputPermutations() {
        PhysicalPlanUnit forward = sourcePinnedUnitWithInputPermutation(false);
        PhysicalPlanUnit reverse = sourcePinnedUnitWithInputPermutation(true);

        ExecutableTask first = task(binding("provider-a"), List.of(forward), List.of());
        ExecutableTask permuted = task(binding("provider-a"), List.of(reverse), List.of());

        assertEquals(forward, reverse);
        assertEquals(first.id(), permuted.id());
    }

    @Test
    void semanticallyDifferentMembershipsProduceDifferentCanonicalBytesAndTaskIds() {
        UnitPair pair = dependentPair(false);
        ExecutableTask producer = task(
                binding("provider-a"), List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(
                binding("provider-a"), List.of(pair.consumer()), List.of());

        assertNotEquals(producer.memberships(), consumer.memberships());
        assertNotEquals(
                ExecutableTaskCanonicalCodec.taskSemantics(
                        producer.compositionDecision(),
                        producer.boundaryActions(),
                        producer.requiredInputArtifactPins()),
                ExecutableTaskCanonicalCodec.taskSemantics(
                        consumer.compositionDecision(),
                        consumer.boundaryActions(),
                        consumer.requiredInputArtifactPins()));
        assertNotEquals(producer.id(), consumer.id());
    }

    @Test
    void nullAndEmptyMembershipFieldsRemainCanonicallyDistinct() {
        PhysicalPlanUnit absentProducer = sourcePinnedUnitWithProducerLogicalNodeId(null);
        PhysicalPlanUnit emptyProducer = sourcePinnedUnitWithProducerLogicalNodeId("");

        assertNotEquals(absentProducer, emptyProducer);
        assertNotEquals(
                task(binding("provider-a"), List.of(absentProducer), List.of()).id(),
                task(binding("provider-a"), List.of(emptyProducer), List.of()).id());
    }

    @Test
    void membershipOrProviderBindingChangeChangesExecutableTaskId() {
        UnitPair pair = dependentPair(false);
        ExecutableTask producerOnly = task(
                binding("provider-a"), List.of(pair.producer()), List.of());
        ExecutableTask both = task(
                binding("provider-a"), List.of(pair.producer(), pair.consumer()), List.of());
        ExecutableTask otherBinding = task(
                binding("provider-b"), List.of(pair.producer()), List.of());

        assertNotEquals(producerOnly.id(), both.id());
        assertNotEquals(producerOnly.id(), otherBinding.id());
        assertEquals(otherBinding.providerBindingPin(), otherBinding.compositionDecision().providerBindingPin());
    }

    @Test
    void graphDigestIsDeterministicAcrossTaskPermutationAndDistinctFromTaskIdentity() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ExecutableTask producer = task(binding("provider-a"), List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(binding("provider-b"), List.of(pair.consumer()), List.of());

        ProviderBoundExecutableTaskGraph forward = ProviderBoundExecutableTaskGraph.derive(
                plan, List.of(producer, consumer));
        ProviderBoundExecutableTaskGraph reverse = ProviderBoundExecutableTaskGraph.derive(
                plan, List.of(consumer, producer));

        assertEquals(forward.digest(), reverse.digest());
        assertEquals(List.of(producer.id(), consumer.id()).stream().sorted().toList(),
                forward.tasks().stream().map(ExecutableTask::id).toList());
        assertFalse(ExecutableTaskGraphDigest.class.isAssignableFrom(ExecutableTaskId.class));
        assertNotEquals(forward.digest().sha256Hex(), producer.id().sha256Hex());
    }

    @Test
    void separateAndCoalescedDependenciesAreBothPreservedWithoutLoss() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ExecutableTask producer = task(binding("provider-a"), List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(binding("provider-a"), List.of(pair.consumer()), List.of());
        ProviderBoundExecutableTaskGraph separate = ProviderBoundExecutableTaskGraph.derive(
                plan, List.of(producer, consumer));

        assertEquals(1, separate.taskDependencies().size());
        assertEquals(pair.edge(), separate.taskDependencies().getFirst().sourceDependency());
        assertEquals(0, separate.providerLocalDependencies().size());
        assertEquals(0, separate.dependencyLossCount());

        ExecutableTask coalesced = task(
                binding("provider-a"), List.of(pair.consumer(), pair.producer()), List.of());
        ProviderBoundExecutableTaskGraph internal = ProviderBoundExecutableTaskGraph.derive(
                plan, List.of(coalesced));

        assertEquals(0, internal.taskDependencies().size());
        assertEquals(1, internal.providerLocalDependencies().size());
        assertEquals(pair.producer().stepId(),
                internal.providerLocalDependencies().getFirst().producerUnitId());
        assertEquals(pair.consumer().stepId(),
                internal.providerLocalDependencies().getFirst().consumerUnitId());
        assertEquals(0, internal.dependencyLossCount());
    }

    @Test
    void providerBoundGraphRejectsUnprovenMultiUnitCompositionDecision() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        List<ExecutableTaskMembership> memberships =
                ExecutableTaskMembership.canonicalForUnits(
                        List.of(pair.producer(), pair.consumer()));
        CompositionDecision unproven = new CompositionDecision(
                CompositionDecision.Status.ALLOWED,
                binding("provider-a"),
                memberships,
                List.of(),
                memberships.stream()
                        .map(ExecutableTaskMembership::failureAttributionMapping)
                        .map(MemberAttribution.class::cast)
                        .toList());
        ExecutableTask task = ExecutableTask.create(unproven, List.of());

        assertFalse(unproven.evaluatorProvenAllowed());
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(plan, List.of(task)));
        assertTrue(failure.getMessage().contains("evaluator-proven ALLOWED"));
    }

    @Test
    void exactCoverageRejectsMissingAndDuplicateMemberships() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        ExecutableTask producer = task(binding("provider-a"), List.of(pair.producer()), List.of());

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(plan, List.of(producer)));
        assertTrue(missing.getMessage().contains("without membership"));

        ExecutableTask duplicateOnOtherBinding = task(
                binding("provider-b"), List.of(pair.producer()), List.of());
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan, List.of(producer, duplicateOnOtherBinding)));
        assertTrue(duplicate.getMessage().contains("duplicate physical plan unit membership"));
    }

    @Test
    void derivationDoesNotRewritePhysicalPlanOrCanonicalUnits() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        List<PhysicalPlanUnit> before = List.copyOf(plan.units());
        PhysicalExecutionPlanDigest digestBefore = plan.digest();
        ExecutableTask producer = task(binding("provider-a"), List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(binding("provider-a"), List.of(pair.consumer()), List.of());

        ProviderBoundExecutableTaskGraph graph = ProviderBoundExecutableTaskGraph.derive(
                plan, List.of(producer, consumer));

        assertSame(plan, graph.sourcePhysicalPlan());
        assertEquals(before, plan.units());
        assertSame(before.getFirst(), plan.units().getFirst());
        assertSame(before.getLast(), plan.units().getLast());
        assertEquals(digestBefore, plan.digest());
        assertEquals(2, graph.sourcePhysicalPlanUnitCount());
        assertEquals(2, graph.uniqueMembershipPhysicalUnitCount());
        assertEquals(0, graph.missingMembershipCount());
        assertEquals(0, graph.duplicateMembershipCount());
    }

    @Test
    void boundaryActionIsTaskOwnedDataAndNeverIndependentlySchedulable() {
        PhysicalPlanUnit unit = sourcePinnedUnit("unit-source");
        BoundaryAction preAction = preInputAction(unit, 0);
        BoundaryAction postAction = postIntermediateAction(unit, 0);
        ExecutableTask task = task(
                binding("provider-a"), List.of(unit), List.of(postAction, preAction));

        assertFalse(BoundaryAction.INDEPENDENTLY_SCHEDULABLE);
        assertTrue(BoundaryAction.OUTPUT_SUCCESS_REQUIRES_ARTIFACT_AUTHORITY_COMMIT);
        assertEquals(List.of(BoundaryAction.Phase.PRE_EXECUTION, BoundaryAction.Phase.POST_EXECUTION),
                task.boundaryActions().stream().map(BoundaryAction::phase).toList());
        assertEquals(1, task.requiredInputArtifactPins().size());
        assertEquals(unit.typedInputs().getFirst().sourceArtifact(),
                task.requiredInputArtifactPins().getFirst().artifactPin());

        List<String> componentNames = Arrays.stream(BoundaryAction.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of("phase", "deterministicOrder", "target"), componentNames);
    }

    @Test
    void mandatoryArtifactBoundaryCannotBeHiddenInsideOneTask() {
        UnitPair pair = dependentPair(true);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        CompositionDecision forbidden = compositionDecision(
                binding("provider-a"), List.of(pair.producer(), pair.consumer()));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ExecutableTask.create(forbidden, List.of()));
        assertTrue(failure.getMessage().contains("proven ALLOWED"));

        ExecutableTask producer = task(binding("provider-a"), List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(binding("provider-a"), List.of(pair.consumer()), List.of());
        ProviderBoundExecutableTaskGraph graph = ProviderBoundExecutableTaskGraph.derive(
                plan, List.of(producer, consumer));
        assertEquals(1, graph.mandatoryArtifactBoundaries().size());
        assertEquals(0, graph.mandatoryArtifactBoundaryViolationCount());
    }

    @Test
    void cyclicTaskTopologyIsRejectedByPlatformGraphMechanics() {
        LogicalDependencyEdge edgeAB = edge("a-b", "unit-a", "unit-b");
        LogicalDependencyEdge edgeBA = edge("b-a", "unit-b", "unit-a");
        PhysicalPlanUnit unitA = unit(
                "unit-a", List.of(), List.of(output("unit-a", false)), List.of(edgeAB, edgeBA));
        PhysicalPlanUnit unitB = unit(
                "unit-b", List.of(), List.of(output("unit-b", false)), List.of(edgeAB, edgeBA));
        PhysicalExecutionPlan plan = plan(unitA, unitB);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        List.of(
                                task(binding("provider-a"), List.of(unitA), List.of()),
                                task(binding("provider-a"), List.of(unitB), List.of()))));
        assertTrue(failure.getMessage().contains("acyclic"));
    }

    private static ExecutableTask task(
            ProviderBindingPin binding,
            List<PhysicalPlanUnit> units,
            List<BoundaryAction> actions) {
        return ExecutableTask.create(compositionDecision(binding, units), actions);
    }

    private static CompositionDecision compositionDecision(
            ProviderBindingPin binding,
            List<PhysicalPlanUnit> units) {
        List<ExecutableTaskMembership> memberships =
                ExecutableTaskMembership.canonicalForUnits(units);
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(
                binding.providerCapabilityProfileVersionOrDigest(), List.of());
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1),
                binding.providerExecutionContractVersion(),
                List.of());
        ProviderLocalCompositionRequest request = ProviderLocalCompositionRequest.of(
                memberships,
                binding,
                profile,
                contract,
                new ProviderCompositionDeclaration(binding, NativePipelineSupport.SUPPORTED),
                List.of());
        return ProviderLocalCompositionEvaluator.evaluate(request);
    }

    private static ProviderBindingPin binding(String provider) {
        ProviderCapabilityProfileVersionOrDigest profile =
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0));
        return new ProviderBindingPin(
                ProviderId.of(provider),
                ProviderImplementationId.of(provider + ".native"),
                ProviderVersion.of("1.0.0"),
                ProviderExecutionContractVersion.of(1, 0),
                profile,
                List.of());
    }

    private static BoundaryAction preInputAction(PhysicalPlanUnit unit, int order) {
        return new BoundaryAction(
                BoundaryAction.Phase.PRE_EXECUTION,
                order,
                new BoundaryAction.RequiredInputArtifactTarget(
                        unit.stepId(), unit.typedInputs().getFirst()));
    }

    private static BoundaryAction postIntermediateAction(PhysicalPlanUnit unit, int order) {
        OutputDeclaration output = unit.typedOutputs().getFirst();
        IntermediateArtifactExpectation expectation =
                output.intermediateArtifactExpectations().getFirst();
        return new BoundaryAction(
                BoundaryAction.Phase.POST_EXECUTION,
                order,
                new BoundaryAction.IntermediateArtifactTarget(
                        unit.stepId(), output, expectation));
    }

    private static PhysicalExecutionPlan plan(PhysicalPlanUnit... units) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("plan-business-id"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("plan-semantic-fingerprint"),
                List.of(units),
                null,
                new PhysicalExecutionPlanDigest("source-plan-digest"));
    }

    private static UnitPair dependentPair(boolean mandatoryMaterialization) {
        LogicalDependencyEdge edge = edge("a-b", "unit-a", "unit-b");
        PhysicalPlanUnit producer = unit(
                "unit-a",
                List.of(),
                List.of(output("unit-a", mandatoryMaterialization)),
                List.of(edge));
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
                "unit-b",
                List.of(input),
                List.of(output("unit-b", false)),
                List.of(edge));
        return new UnitPair(producer, consumer, edge);
    }

    private static PhysicalPlanUnit sourcePinnedUnit(String id) {
        SourceArtifact sourceArtifact = new SourceArtifact(
                new ArtifactId("artifact-" + id),
                ContentDigest.sha256("a".repeat(64)));
        InputBinding input = new InputBinding(
                new ExecutionInputId("input-" + id),
                "logical-" + id,
                new ExecutionStepId(id),
                new RenderNodeId("render-" + id),
                null,
                null,
                null,
                null,
                sourceArtifact,
                null);
        IntermediateArtifactExpectation expectation = new IntermediateArtifactExpectation(
                new LogicalArtifactId("logical-artifact-" + id), RenderOutputRole.RENDER_MASTER);
        OutputDeclaration output = new OutputDeclaration(
                new ExecutionOutputId("output-" + id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                List.of(),
                List.of(),
                List.of(expectation),
                List.of());
        return unit(id, List.of(input), List.of(output), List.of());
    }

    private static PhysicalPlanUnit sourcePinnedUnitWithProducerLogicalNodeId(
            String producerLogicalNodeId) {
        PhysicalPlanUnit original = sourcePinnedUnit("unit-nullable-membership");
        InputBinding originalInput = original.typedInputs().getFirst();
        InputBinding input = new InputBinding(
                originalInput.inputId(),
                originalInput.consumerLogicalNodeId(),
                originalInput.consumerStepId(),
                originalInput.consumerRenderNodeId(),
                producerLogicalNodeId,
                originalInput.producerStepId(),
                originalInput.producerRenderNodeId(),
                originalInput.dependencyVariant(),
                originalInput.sourceArtifact(),
                originalInput.requiredSampleWindow());
        return unit(
                original.stepId().value(),
                List.of(input),
                original.typedOutputs(),
                original.typedDependencies());
    }

    private static PhysicalPlanUnit sourcePinnedUnitWithInputPermutation(boolean reverse) {
        String id = "unit-source-permutation";
        InputBinding first = sourceInput(id, "a", "b".repeat(64));
        InputBinding second = sourceInput(id, "b", "c".repeat(64));
        List<InputBinding> inputs = reverse ? List.of(second, first) : List.of(first, second);
        return unit(id, inputs, List.of(output(id, false)), List.of());
    }

    private static InputBinding sourceInput(String unitId, String suffix, String digest) {
        SourceArtifact sourceArtifact = new SourceArtifact(
                new ArtifactId("artifact-" + suffix), ContentDigest.sha256(digest));
        return new InputBinding(
                new ExecutionInputId("input-" + suffix),
                "logical-" + unitId,
                new ExecutionStepId(unitId),
                new RenderNodeId("render-" + unitId),
                null,
                null,
                null,
                null,
                sourceArtifact,
                null);
    }

    private static LogicalDependencyEdge edge(String id, String producer, String consumer) {
        return new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-" + id),
                "logical-" + producer,
                "logical-" + consumer,
                new RenderNodeId("render-" + producer),
                new RenderNodeId("render-" + consumer),
                new RenderDependency.DecodedFrames());
    }

    private static PhysicalPlanUnit unit(
            String id,
            List<InputBinding> inputs,
            List<OutputDeclaration> outputs,
            List<LogicalDependencyEdge> dependencies) {
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
                List.of(),
                List.of(),
                null,
                true);
    }

    private static OutputDeclaration output(String id, boolean mandatoryMaterialization) {
        return new OutputDeclaration(
                new ExecutionOutputId("output-" + id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                List.of(),
                mandatoryMaterialization
                        ? List.of(AudioProcessMaterializationRequirement.of(null, null, null))
                        : List.of(),
                List.of(),
                List.of());
    }

    private record UnitPair(
            PhysicalPlanUnit producer,
            PhysicalPlanUnit consumer,
            LogicalDependencyEdge edge) {
    }
}
