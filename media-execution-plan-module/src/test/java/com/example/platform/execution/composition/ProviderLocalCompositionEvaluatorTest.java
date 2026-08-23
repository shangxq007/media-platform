package com.example.platform.execution.composition;

import com.example.platform.execution.composition.CompositionDecision.Status;
import com.example.platform.execution.composition.FailureAttribution.UnknownMemberAttribution;
import com.example.platform.execution.composition.ProviderCompositionDeclaration.NativePipelineSupport;
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
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderLocalCompositionEvaluatorTest {

    @Test
    void oneUnitMembershipPreservesTypedCanonicalReferencesAndAttribution() {
        PhysicalPlanUnit unit = isolatedUnit("unit-a");
        ExecutableTaskMembership membership =
                ExecutableTaskMembership.canonicalForUnits(List.of(unit)).getFirst();

        assertEquals(PhysicalPlanUnitMembershipCardinality.EXACTLY_ONE,
                ExecutableTaskMembership.PHYSICAL_PLAN_UNIT_MEMBERSHIP_CARDINALITY);
        assertSame(unit, membership.physicalPlanUnit());
        assertSame(unit.typedInputs(), membership.inputMapping());
        assertSame(unit.typedOutputs(), membership.outputMapping());
        assertSame(unit.typedDependencies(), membership.dependencyMapping());
        assertSame(unit, membership.failureAttributionMapping().member());
        assertEquals(UnknownMemberAttribution.UNKNOWN_MEMBER_ATTRIBUTION,
                UnknownMemberAttribution.valueOf("UNKNOWN_MEMBER_ATTRIBUTION"));
    }

    @Test
    void legalMultiUnitCoalescingRequiresExplicitProviderNativeSupport() {
        UnitPair pair = dependentPair(false);
        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(
                request(pair, NativePipelineSupport.SUPPORTED, List.of()));

        assertEquals(Status.ALLOWED, decision.status());
        assertTrue(decision.allowed());
        assertTrue(decision.blockers().isEmpty());
        assertEquals(List.of("unit-a", "unit-b"), memberIds(decision));
    }

    @Test
    void illegalMultiUnitCoalescingReturnsTypedBlocker() {
        UnitPair pair = dependentPair(false);
        CompositionBoundaryConstraint constraint = new CompositionBoundaryConstraint(
                pair.producer().stepId(), pair.consumer().stepId(),
                CompositionBlocker.DEVICE_CONTEXT_INCOMPATIBLE);

        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(
                request(pair, NativePipelineSupport.SUPPORTED, List.of(constraint)));

        assertEquals(Status.FORBIDDEN, decision.status());
        assertFalse(decision.allowed());
        assertEquals(List.of(CompositionBlocker.DEVICE_CONTEXT_INCOMPATIBLE), decision.blockers());
    }

    @Test
    void mandatoryArtifactBoundaryAlwaysBlocksCoalescing() {
        UnitPair pair = dependentPair(true);
        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(
                request(pair, NativePipelineSupport.SUPPORTED, List.of()));

        assertTrue(ProviderLocalCompositionEvaluator.MANDATORY_ARTIFACT_BOUNDARY_BLOCKS_COALESCING);
        assertEquals(Status.FORBIDDEN, decision.status());
        assertEquals(List.of(CompositionBlocker.MANDATORY_INTERMEDIATE_ARTIFACT),
                decision.blockers());
    }

    @Test
    void membershipPermutationProducesDependencyPreservingCanonicalOrder() {
        UnitPair pair = dependentPair(false);
        List<ExecutableTaskMembership> forward = ExecutableTaskMembership.canonicalForUnits(
                List.of(pair.producer(), pair.consumer()));
        List<ExecutableTaskMembership> reverse = ExecutableTaskMembership.canonicalForUnits(
                List.of(pair.consumer(), pair.producer()));

        assertEquals(forward.stream().map(ExecutableTaskMembership::physicalPlanUnitId).toList(),
                reverse.stream().map(ExecutableTaskMembership::physicalPlanUnitId).toList());
        assertEquals(List.of(0, 1), forward.stream()
                .map(ExecutableTaskMembership::canonicalPosition).toList());
        assertEquals(pair.producer().stepId(), forward.getFirst().physicalPlanUnitId());
    }

    @Test
    void cyclicMembershipTopologyIsRejected() {
        LogicalDependencyEdge edgeAB = new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-a-b"),
                "logical-unit-a",
                "logical-unit-b",
                new RenderNodeId("render-unit-a"),
                new RenderNodeId("render-unit-b"),
                new RenderDependency.DecodedFrames());
        LogicalDependencyEdge edgeBA = new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-b-a"),
                "logical-unit-b",
                "logical-unit-a",
                new RenderNodeId("render-unit-b"),
                new RenderNodeId("render-unit-a"),
                new RenderDependency.DecodedFrames());
        PhysicalPlanUnit unitA = unit(
                "unit-a", List.of(), List.of(output("unit-a", false)), List.of(edgeAB, edgeBA));
        PhysicalPlanUnit unitB = unit(
                "unit-b", List.of(), List.of(output("unit-b", false)), List.of(edgeAB, edgeBA));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ExecutableTaskMembership.canonicalForUnits(List.of(unitA, unitB)));
        assertTrue(failure.getMessage().contains("acyclic"));
    }

    @Test
    void missingMembershipIsRejectedAgainstCanonicalPhysicalPlan() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        List<ExecutableTaskMembership> onlyProducer =
                ExecutableTaskMembership.canonicalForUnits(List.of(pair.producer()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ExecutableTaskMembership.validateExactCoverage(plan, onlyProducer));
        assertTrue(failure.getMessage().contains("without membership"));
    }

    @Test
    void duplicateMembershipIsRejectedAgainstCanonicalPhysicalPlan() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair);
        ExecutableTaskMembership producer =
                ExecutableTaskMembership.canonicalForUnits(List.of(pair.producer())).getFirst();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ExecutableTaskMembership.validateExactCoverage(plan, List.of(producer, producer)));
        assertTrue(failure.getMessage().contains("duplicate physical plan unit membership"));
    }

    @Test
    void membershipAndAllowedDecisionPreserveDependencyAndFailureAttribution() {
        UnitPair pair = dependentPair(false);
        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(
                request(pair, NativePipelineSupport.SUPPORTED, List.of()));
        ExecutableTaskMembership producer = decision.memberships().getFirst();
        ExecutableTaskMembership consumer = decision.memberships().getLast();

        assertSame(pair.edge(), producer.dependencyMapping().getFirst());
        assertSame(pair.edge(), consumer.dependencyMapping().getFirst());
        assertSame(pair.input(), consumer.inputMapping().getFirst());
        assertEquals(pair.producer().stepId(), consumer.inputMapping().getFirst().producerStepId());
        assertSame(pair.producer(), decision.memberFailureAttributions().getFirst().member());
        assertSame(pair.consumer(), decision.memberFailureAttributions().getLast().member());
    }

    @Test
    void unknownProviderCompositionSemanticsFailsClosed() {
        UnitPair pair = dependentPair(false);
        CompositionDecision decision = ProviderLocalCompositionEvaluator.evaluate(
                request(pair, NativePipelineSupport.UNKNOWN, List.of()));

        assertEquals(Status.UNKNOWN_FAIL_CLOSED, decision.status());
        assertFalse(decision.allowed());
        assertEquals(List.of(CompositionBlocker.UNKNOWN_PROVIDER_COMPOSITION_SEMANTICS),
                decision.blockers());
    }

    private static ProviderLocalCompositionRequest request(
            UnitPair pair,
            NativePipelineSupport support,
            List<CompositionBoundaryConstraint> constraints) {
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0));
        ProviderExecutionContractVersion contractVersion =
                ProviderExecutionContractVersion.of(1, 0);
        ProviderBindingPin binding = new ProviderBindingPin(
                ProviderId.of("provider"),
                ProviderImplementationId.of("provider.native"),
                ProviderVersion.of("1.0.0"),
                contractVersion,
                profileReference,
                List.of());
        ProviderCapabilityProfile profile =
                new ProviderCapabilityProfile(profileReference, List.of());
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of());
        return ProviderLocalCompositionRequest.of(
                ExecutableTaskMembership.canonicalForUnits(
                        List.of(pair.consumer(), pair.producer())),
                binding,
                profile,
                contract,
                new ProviderCompositionDeclaration(binding, support),
                constraints);
    }

    private static List<String> memberIds(CompositionDecision decision) {
        return decision.memberships().stream()
                .map(member -> member.physicalPlanUnitId().value())
                .toList();
    }

    private static PhysicalExecutionPlan plan(UnitPair pair) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("plan-1"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("fingerprint"),
                List.of(pair.consumer(), pair.producer()),
                null,
                new PhysicalExecutionPlanDigest("digest"));
    }

    private static PhysicalPlanUnit isolatedUnit(String id) {
        return unit(id, List.of(), List.of(output(id, false)), List.of());
    }

    private static UnitPair dependentPair(boolean mandatoryMaterialization) {
        LogicalDependencyEdge edge = new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-a-b"),
                "logical-unit-a",
                "logical-unit-b",
                new RenderNodeId("render-unit-a"),
                new RenderNodeId("render-unit-b"),
                new RenderDependency.DecodedFrames());
        PhysicalPlanUnit producer = unit(
                "unit-a", List.of(), List.of(output("unit-a", mandatoryMaterialization)),
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
                "unit-b", List.of(input), List.of(output("unit-b", false)), List.of(edge));
        return new UnitPair(producer, consumer, edge, input);
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
            LogicalDependencyEdge edge,
            InputBinding input) {
    }
}
