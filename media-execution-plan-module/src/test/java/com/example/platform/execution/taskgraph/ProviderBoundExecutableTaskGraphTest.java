package com.example.platform.execution.taskgraph;

import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderCompatibilityGraph;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderBoundExecutableTaskGraphTest {

    private static final BoundaryContractId DIRECT_CONTRACT =
            BoundaryContractId.of("taskgraph-test-direct.v1");

    @Test
    void executionReuseKeyIsVersionedCanonicalAndStableForEquivalentGraphs() {
        ProviderBoundExecutableTaskGraph first = separateGraph(dependentPair(false));
        ProviderBoundExecutableTaskGraph second = separateGraph(dependentPair(false));

        Map<ExecutableTaskId, ExecutionReuseKey> firstKeys =
                ExecutionReuseKeyDeriver.derive(first);
        Map<ExecutableTaskId, ExecutionReuseKey> secondKeys =
                ExecutionReuseKeyDeriver.derive(second);

        assertEquals(firstKeys, secondKeys);
        assertEquals(2, firstKeys.size());
        firstKeys.values().forEach(key -> {
            assertEquals("execution-reuse-key.v1", key.version());
            assertEquals(64, key.stableDigest().length());
            assertTrue(key.canonicalSerialization().startsWith(
                    "roadmap22.execution-reuse-key.v1"));
        });
    }

    @Test
    void executionReuseKeySurfaceCannotAcceptMutableRuntimeOrLocationState() {
        List<String> components = Arrays.stream(ExecutionReuseKey.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertEquals(List.of("version", "canonicalSerialization", "stableDigest"), components);
        ExecutionReuseKey key = ExecutionReuseKeyDeriver.derive(
                sourceGraph(sourcePinnedUnit("immutable-key-surface")))
                .values().iterator().next();
        for (String mutableOrLocationValue : List.of(
                "attempt-123", "generation-9", "lease-4", "worker-runtime-2",
                "host-5", "device-7", "trace-8", "request-9", "s3://bucket/key",
                "/tmp/materialized", "2026-08-25T12:00:00Z")) {
            assertFalse(key.canonicalSerialization().contains(mutableOrLocationValue));
        }
    }

    @Test
    void computedDependencyUsesPredecessorMerkleIdentityWithoutFutureArtifactPin() {
        ProviderBoundExecutableTaskGraph graph = separateGraph(dependentPair(false));
        Map<ExecutableTaskId, ExecutionReuseKey> keys = ExecutionReuseKeyDeriver.derive(graph);
        ExecutableTaskDependency dependency = graph.taskDependencies().getFirst();
        ExecutionReuseKey producer = keys.get(dependency.producerTaskId());
        ExecutionReuseKey consumer = keys.get(dependency.consumerTaskId());

        assertTrue(consumer.canonicalSerialization().contains(producer.stableDigest()));
        assertTrue(consumer.canonicalSerialization().contains("output-unit-a"));
        assertTrue(consumer.canonicalSerialization().contains("DECODED_FRAMES"));
        assertFalse(consumer.canonicalSerialization().contains("futureArtifact"));
    }

    @Test
    void sourceArtifactPinParticipatesAndProducerChangePropagatesToDependentKey() {
        ProviderBoundExecutableTaskGraph first = separateGraph(
                dependentPairWithSourceDigest("a".repeat(64)));
        ProviderBoundExecutableTaskGraph second = separateGraph(
                dependentPairWithSourceDigest("b".repeat(64)));
        Map<ExecutableTaskId, ExecutionReuseKey> firstKeys = ExecutionReuseKeyDeriver.derive(first);
        Map<ExecutableTaskId, ExecutionReuseKey> secondKeys = ExecutionReuseKeyDeriver.derive(second);
        ExecutableTaskDependency firstDependency = first.taskDependencies().getFirst();
        ExecutableTaskDependency secondDependency = second.taskDependencies().getFirst();
        ExecutionReuseKey firstProducer = firstKeys.get(firstDependency.producerTaskId());
        ExecutionReuseKey secondProducer = secondKeys.get(secondDependency.producerTaskId());
        ExecutionReuseKey firstConsumer = firstKeys.get(firstDependency.consumerTaskId());
        ExecutionReuseKey secondConsumer = secondKeys.get(secondDependency.consumerTaskId());

        assertNotEquals(firstProducer, secondProducer);
        assertNotEquals(firstConsumer, secondConsumer);
        assertTrue(firstProducer.canonicalSerialization().contains("artifact-unit-a"));
        assertTrue(firstProducer.canonicalSerialization().contains("a".repeat(64)));
    }

    @Test
    void dependencyPreservingPruningStopsAtValidatedReuseAndKeepsRequiredClosure() {
        ProviderBoundExecutableTaskGraph graph = separateGraph(dependentPair(false));
        ExecutableTaskDependency dependency = graph.taskDependencies().getFirst();
        ExecutableTaskId producer = dependency.producerTaskId();
        ExecutableTaskId consumer = dependency.consumerTaskId();

        ReusePruningResult miss = DependencyPreservingReusePruner.prune(
                graph, Set.of(consumer), Set.of());
        assertEquals(Set.of(producer, consumer), miss.tasksToExecute());
        assertEquals(Set.of(), miss.reusedTasks());

        ReusePruningResult producerHit = DependencyPreservingReusePruner.prune(
                graph, Set.of(consumer), Set.of(producer));
        assertEquals(Set.of(consumer), producerHit.tasksToExecute());
        assertEquals(Set.of(producer), producerHit.reusedTasks());

        ReusePruningResult consumerHit = DependencyPreservingReusePruner.prune(
                graph, Set.of(consumer), Set.of(consumer));
        assertEquals(Set.of(), consumerHit.tasksToExecute());
        assertEquals(Set.of(consumer), consumerHit.reusedTasks());
    }

    @Test
    void pruningFailsClosedForUnknownTargetsOrUnvalidatedReuseClaims() {
        ProviderBoundExecutableTaskGraph graph = separateGraph(dependentPair(false));
        ExecutableTaskId unknown = new ExecutableTaskId("f".repeat(64));

        assertThrows(IllegalArgumentException.class,
                () -> DependencyPreservingReusePruner.prune(graph, Set.of(unknown), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> DependencyPreservingReusePruner.prune(graph, Set.of(graph.tasks().getFirst().id()),
                        Set.of(unknown)));
    }

    @Test
    void executableTaskIdIsDeterministicAcrossEquivalentMembershipPermutations() {
        UnitPair pair = dependentPair(false);
        TaskContext context = context(plan(pair.producer(), pair.consumer()), "provider-a");

        ExecutableTask first = task(
                context, "provider-a",
                List.of(pair.producer(), pair.consumer()),
                List.of());
        ExecutableTask permuted = task(
                context, "provider-a",
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
        TaskContext context = context(plan(forward), "provider-a");

        ExecutableTask first = task(context, "provider-a", List.of(forward), List.of());
        ExecutableTask permuted = task(context, "provider-a", List.of(reverse), List.of());

        assertEquals(forward, reverse);
        assertEquals(first.id(), permuted.id());
    }

    @Test
    void executableInputProjectionRetainsRuntimeIdentityWithoutRenderTypes() {
        PhysicalPlanUnit sourceUnit = sourcePinnedUnit("unit-source-projection");
        TaskContext sourceContext = context(plan(sourceUnit), "provider-a");
        ExecutableTask sourceTask = task(
                sourceContext, "provider-a", List.of(sourceUnit), List.of());
        ExecutableInputProjection source = sourceTask.executionInputs().getFirst();

        UnitPair pair = dependentPair(false);
        TaskContext computedContext = context(plan(pair.producer(), pair.consumer()), "provider-a");
        ExecutableTask computedTask = task(
                computedContext, "provider-a", List.of(pair.consumer()), List.of());
        ExecutableInputProjection computed = computedTask.executionInputs().getFirst();
        ExecutableTask coalescedTask = task(
                computedContext,
                "provider-a",
                List.of(pair.producer(), pair.consumer()),
                List.of());

        assertEquals(sourceUnit.typedInputs().getFirst().inputId(), source.inputId());
        assertEquals(sourceUnit.stepId(), source.consumerStepId());
        assertEquals(Optional.empty(), source.producerStepId());
        assertEquals(ExecutableInputProjection.SourceArtifactPresence.PRESENT,
                source.sourceArtifactPresence());
        assertEquals(pair.consumer().typedInputs().getFirst().inputId(), computed.inputId());
        assertEquals(pair.consumer().stepId(), computed.consumerStepId());
        assertEquals(Optional.of(pair.producer().stepId()), computed.producerStepId());
        assertEquals(ExecutableInputProjection.SourceArtifactPresence.ABSENT,
                computed.sourceArtifactPresence());
        assertEquals(List.of(source), sourceTask.requiredRuntimeInputs());
        assertEquals(List.of(computed), computedTask.requiredRuntimeInputs());
        assertEquals(List.of(), coalescedTask.requiredRuntimeInputs());
        assertEquals(computed, computedTask.requireExactRuntimeInput(computed));
        assertTrue(Arrays.stream(ExecutableInputProjection.class.getRecordComponents())
                .map(component -> component.getType().getName())
                .noneMatch(type -> type.contains(".render.")));
    }

    @Test
    void semanticallyDifferentMembershipsProduceDifferentCanonicalBytesAndTaskIds() {
        UnitPair pair = dependentPair(false);
        TaskContext context = context(plan(pair.producer(), pair.consumer()), "provider-a");
        ExecutableTask producer = task(
                context, "provider-a", List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(
                context, "provider-a", List.of(pair.consumer()), List.of());

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
        TaskContext absentContext = context(plan(absentProducer), "provider-a");
        TaskContext emptyContext = context(plan(emptyProducer), "provider-a");

        assertNotEquals(absentProducer, emptyProducer);
        assertNotEquals(
                task(absentContext, "provider-a", List.of(absentProducer), List.of()).id(),
                task(emptyContext, "provider-a", List.of(emptyProducer), List.of()).id());
    }

    @Test
    void membershipOrProviderBindingChangeChangesExecutableTaskId() {
        UnitPair pair = dependentPair(false);
        TaskContext context = context(
                plan(pair.producer(), pair.consumer()), "provider-a", "provider-b");
        ExecutableTask producerOnly = task(
                context, "provider-a", List.of(pair.producer()), List.of());
        ExecutableTask both = task(
                context, "provider-a", List.of(pair.producer(), pair.consumer()), List.of());
        ExecutableTask otherBinding = task(
                context, "provider-b", List.of(pair.producer()), List.of());

        assertNotEquals(producerOnly.id(), both.id());
        assertNotEquals(producerOnly.id(), otherBinding.id());
        assertEquals(otherBinding.providerBindingPin(), otherBinding.compositionDecision().providerBindingPin());
    }

    @Test
    void graphDigestIsDeterministicAcrossTaskPermutationAndDistinctFromTaskIdentity() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        TaskContext context = context(plan, "provider-a");
        ExecutableTask producer = task(
                context, "provider-a", List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(
                context, "provider-a", List.of(pair.consumer()), List.of());
        ExecutionArtifactBoundary boundary = executionBoundary(
                pair, context.candidate("provider-a"), context.candidate("provider-a"));

        ProviderBoundExecutableTaskGraph forward = ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(producer, consumer), List.of(boundary));
        ProviderBoundExecutableTaskGraph reverse = ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(consumer, producer), List.of(boundary));

        assertEquals(forward.digest(), reverse.digest());
        assertEquals(forward.tasks().stream().map(ExecutableTask::id).toList(),
                reverse.tasks().stream().map(ExecutableTask::id).toList());
        assertFalse(ExecutableTaskGraphDigest.class.isAssignableFrom(ExecutableTaskId.class));
        assertNotEquals(forward.digest().sha256Hex(), producer.id().sha256Hex());
    }

    @Test
    void separateAndCoalescedDependenciesAreBothPreservedWithoutLoss() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        TaskContext context = context(plan, "provider-a");
        ExecutableTask producer = task(
                context, "provider-a", List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(
                context, "provider-a", List.of(pair.consumer()), List.of());
        ExecutionArtifactBoundary boundary = executionBoundary(
                pair, context.candidate("provider-a"), context.candidate("provider-a"));
        ProviderBoundExecutableTaskGraph separate = ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(producer, consumer), List.of(boundary));

        assertEquals(1, separate.taskDependencies().size());
        assertEquals(pair.edge(), separate.taskDependencies().getFirst().sourceDependency());
        assertEquals(
                ExecutableInputProjection.from(pair.consumer().typedInputs().getFirst()),
                separate.taskDependencies().getFirst().consumerInput());
        assertTrue(Arrays.stream(ExecutableTaskDependency.class.getRecordComponents())
                .map(component -> component.getType().getName())
                .noneMatch(type -> type.contains("ExecutionIoProjection$InputBinding")));
        assertEquals(0, separate.providerLocalDependencies().size());
        assertEquals(0, separate.dependencyLossCount());

        ExecutableTask coalesced = task(
                context, "provider-a", List.of(pair.consumer(), pair.producer()), List.of());
        ProviderBoundExecutableTaskGraph internal = ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(coalesced), List.of());

        assertEquals(0, internal.taskDependencies().size());
        assertEquals(1, internal.providerLocalDependencies().size());
        assertEquals(pair.producer().stepId(),
                internal.providerLocalDependencies().getFirst().producerUnitId());
        assertEquals(pair.consumer().stepId(),
                internal.providerLocalDependencies().getFirst().consumerUnitId());
        assertEquals(0, internal.dependencyLossCount());
    }

    @Test
    void executableTaskRejectsUnprovenSingleAndMultiUnitCompositionDecisions() {
        UnitPair pair = dependentPair(false);
        List<ExecutableTaskMembership> singleMembership =
                ExecutableTaskMembership.canonicalForUnits(List.of(pair.producer()));
        CompositionDecision unprovenSingle = new CompositionDecision(
                CompositionDecision.Status.ALLOWED,
                binding("provider-a"),
                singleMembership,
                List.of(),
                singleMembership.stream()
                        .map(ExecutableTaskMembership::failureAttributionMapping)
                        .map(MemberAttribution.class::cast)
                        .toList());
        List<ExecutableTaskMembership> multiMembership =
                ExecutableTaskMembership.canonicalForUnits(
                        List.of(pair.producer(), pair.consumer()));
        CompositionDecision unprovenMulti = new CompositionDecision(
                CompositionDecision.Status.ALLOWED,
                binding("provider-a"),
                multiMembership,
                List.of(),
                multiMembership.stream()
                        .map(ExecutableTaskMembership::failureAttributionMapping)
                        .map(MemberAttribution.class::cast)
                        .toList());

        assertFalse(unprovenSingle.evaluatorProvenAllowed());
        assertFalse(unprovenMulti.evaluatorProvenAllowed());
        assertThrows(IllegalArgumentException.class,
                () -> ExecutableTask.create(unprovenSingle, List.of()),
                "UNPROVEN_SINGLE_MEMBER_EXECUTABLE_TASK_ACCEPTANCE_COUNT=0");
        assertThrows(IllegalArgumentException.class,
                () -> ExecutableTask.create(unprovenMulti, List.of()),
                "UNPROVEN_MULTI_MEMBER_EXECUTABLE_TASK_ACCEPTANCE_COUNT=0");
    }

    @Test
    void exactCoverageRejectsMissingAndDuplicateMemberships() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        TaskContext context = context(plan, "provider-a", "provider-b");
        ExecutableTask producer = task(
                context, "provider-a", List.of(pair.producer()), List.of());

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan, context.graph(), List.of(producer), List.of()));
        assertTrue(missing.getMessage().contains("without membership"));

        ExecutableTask duplicateOnOtherBinding = task(
                context, "provider-b", List.of(pair.producer()), List.of());
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        context.graph(),
                        List.of(producer, duplicateOnOtherBinding),
                        List.of()));
        assertTrue(duplicate.getMessage().contains("duplicate physical plan unit membership"));
    }

    @Test
    void derivationDoesNotRewritePhysicalPlanOrCanonicalUnits() {
        UnitPair pair = dependentPair(false);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        List<PhysicalPlanUnit> before = List.copyOf(plan.units());
        PhysicalExecutionPlanDigest digestBefore = plan.digest();
        TaskContext context = context(plan, "provider-a");
        ExecutableTask producer = task(
                context, "provider-a", List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(
                context, "provider-a", List.of(pair.consumer()), List.of());
        ExecutionArtifactBoundary boundary = executionBoundary(
                pair, context.candidate("provider-a"), context.candidate("provider-a"));

        ProviderBoundExecutableTaskGraph graph = ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(producer, consumer), List.of(boundary));

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
        TaskContext context = context(plan(unit), "provider-a");
        BoundaryAction preAction = preInputAction(unit, 0);
        BoundaryAction postAction = postIntermediateAction(unit, 0);
        ExecutableTask task = task(
                context, "provider-a", List.of(unit), List.of(postAction, preAction));

        assertFalse(BoundaryAction.INDEPENDENTLY_SCHEDULABLE);
        assertTrue(BoundaryAction.OUTPUT_SUCCESS_REQUIRES_ARTIFACT_AUTHORITY_COMMIT);
        assertEquals(List.of(BoundaryAction.Phase.PRE_EXECUTION, BoundaryAction.Phase.POST_EXECUTION),
                task.boundaryActions().stream().map(BoundaryAction::phase).toList());
        assertEquals(1, task.requiredInputArtifactPins().size());
        assertEquals(unit.typedInputs().getFirst().sourceArtifact(),
                task.requiredInputArtifactPins().getFirst().artifactPin());
        assertEquals(List.of(unit.typedOutputs().getFirst().outputId()),
                task.authoritativeOutputIds());

        ExecutableTask rawDeclarationsOnly = task(
                context, "provider-a", List.of(unit), List.of(preAction));
        assertEquals(List.of(), rawDeclarationsOnly.authoritativeOutputIds());

        BoundaryAction secondActionForSameOutput = new BoundaryAction(
                BoundaryAction.Phase.POST_EXECUTION, 1, postAction.target());
        ExecutableTask repeatedAuthorityForOneOutput = task(
                context,
                "provider-a",
                List.of(unit),
                List.of(postAction, secondActionForSameOutput));
        assertEquals(List.of(unit.typedOutputs().getFirst().outputId()),
                repeatedAuthorityForOneOutput.authoritativeOutputIds());

        List<String> componentNames = Arrays.stream(BoundaryAction.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertEquals(List.of("phase", "deterministicOrder", "target"), componentNames);
    }

    @Test
    void mandatoryArtifactBoundaryCannotBeHiddenInsideOneTask() {
        UnitPair pair = dependentPair(true);
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        TaskContext context = context(plan, "provider-a");
        CompositionDecision forbidden = compositionDecision(
                context, "provider-a", List.of(pair.producer(), pair.consumer()));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ExecutableTask.create(forbidden, List.of()));
        assertTrue(failure.getMessage().contains("proven ALLOWED"));

        ExecutableTask producer = task(
                context, "provider-a", List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(
                context, "provider-a", List.of(pair.consumer()), List.of());
        ExecutionArtifactBoundary boundary = executionBoundary(
                pair, context.candidate("provider-a"), context.candidate("provider-a"));
        ProviderBoundExecutableTaskGraph graph = ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(producer, consumer), List.of(boundary));
        assertEquals(1, graph.mandatoryArtifactBoundaries().size());
        assertEquals(0, graph.mandatoryArtifactBoundaryViolationCount());
    }

    @Test
    void cyclicTaskTopologyIsRejectedByPlatformGraphMechanics() {
        LogicalDependencyEdge edgeAB = edge("a-b", "unit-a", "unit-b");
        LogicalDependencyEdge edgeBA = edge("b-a", "unit-b", "unit-a");
        InputBinding inputBA = new InputBinding(
                new ExecutionInputId("input-b-a"),
                edgeBA.consumerLogicalNodeId(),
                new ExecutionStepId("unit-a"),
                edgeBA.consumerRenderNodeId(),
                edgeBA.producerLogicalNodeId(),
                new ExecutionStepId("unit-b"),
                edgeBA.producerRenderNodeId(),
                edgeBA.dependencyVariant(),
                null,
                null);
        InputBinding inputAB = new InputBinding(
                new ExecutionInputId("input-a-b"),
                edgeAB.consumerLogicalNodeId(),
                new ExecutionStepId("unit-b"),
                edgeAB.consumerRenderNodeId(),
                edgeAB.producerLogicalNodeId(),
                new ExecutionStepId("unit-a"),
                edgeAB.producerRenderNodeId(),
                edgeAB.dependencyVariant(),
                null,
                null);
        PhysicalPlanUnit unitA = unit(
                "unit-a", List.of(inputBA), List.of(output("unit-a", false)), List.of(edgeAB, edgeBA));
        PhysicalPlanUnit unitB = unit(
                "unit-b", List.of(inputAB), List.of(output("unit-b", false)), List.of(edgeAB, edgeBA));
        PhysicalExecutionPlan plan = plan(unitA, unitB);
        ProviderBindingPin binding = binding("provider-a");
        TaskContext context = context(
                plan,
                List.of(
                        directDeclaration(edgeAB, binding, binding),
                        directDeclaration(edgeBA, binding, binding)),
                "provider-a");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ProviderBoundExecutableTaskGraph.derive(
                        plan,
                        context.graph(),
                        List.of(
                                task(context, "provider-a", List.of(unitA), List.of()),
                                task(context, "provider-a", List.of(unitB), List.of())),
                        List.of()));
        assertTrue(failure.getMessage().contains("acyclic"));
    }

    private static ExecutableTask task(
            TaskContext context,
            String provider,
            List<PhysicalPlanUnit> units,
            List<BoundaryAction> actions) {
        return ExecutableTask.create(compositionDecision(context, provider, units), actions);
    }

    private static CompositionDecision compositionDecision(
            TaskContext context,
            String provider,
            List<PhysicalPlanUnit> units) {
        ProviderCandidate candidate = context.candidate(provider);
        ProviderBindingPin binding = candidate.bindingPin();
        List<ExecutableTaskMembership> memberships =
                ExecutableTaskMembership.canonicalForUnits(units);
        ProviderLocalCompositionRequest request = ProviderLocalCompositionRequest.of(
                memberships,
                context.graph(),
                candidate,
                new ProviderCompositionDeclaration(binding, NativePipelineSupport.SUPPORTED),
                List.of());
        return ProviderLocalCompositionEvaluator.evaluate(request);
    }

    private static TaskContext context(PhysicalExecutionPlan plan, String... providers) {
        return context(plan, List.of(), providers);
    }

    private static TaskContext context(
            PhysicalExecutionPlan plan,
            List<ProviderBoundaryCompatibilityDeclaration> declarations,
            String... providers) {
        List<ProviderCandidate> candidates = Arrays.stream(providers)
                .map(ProviderBoundExecutableTaskGraphTest::candidate)
                .toList();
        ProviderCompatibilityGraph graph = ProviderCompatibilityGraph.build(
                plan,
                plan.units().stream().map(CompatibilityRequest::forUnit).toList(),
                candidates,
                declarations);
        return new TaskContext(plan, graph, candidates);
    }

    private static ProviderCandidate candidate(String provider) {
        ProviderBindingPin binding = binding(provider);
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(
                binding.providerCapabilityProfileVersionOrDigest(), List.of());
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1),
                binding.providerExecutionContractVersion(),
                List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                binding.providerId(),
                binding.providerImplementationId(),
                binding.providerVersion(),
                binding.providerExecutionContractVersion(),
                binding.providerCapabilityProfileVersionOrDigest());
        ProviderStaticCompatibility staticCompatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(
                        ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.MANDATORY_MATERIALIZATION,
                        ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(DIRECT_CONTRACT),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(
                binding, descriptor, contract, profile, staticCompatibility);
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

    private static ProviderBoundaryCompatibilityDeclaration directDeclaration(
            LogicalDependencyEdge edge,
            ProviderBindingPin producer,
            ProviderBindingPin consumer) {
        return new ProviderBoundaryCompatibilityDeclaration(
                edge,
                producer,
                consumer,
                DIRECT_CONTRACT,
                Declaration.DIRECT_INTEROPERABILITY_ALLOWED);
    }

    private static ExecutionArtifactBoundary executionBoundary(
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
                producer.bindingPin().equals(consumer.bindingPin())
                        ? ExecutionArtifactBoundary.MaterializationReason
                                .INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN
                        : ExecutionArtifactBoundary.MaterializationReason.PROVIDER_BINDING_CHANGE,
                Optional.empty());
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

    private static UnitPair dependentPairWithSourceDigest(String digest) {
        UnitPair original = dependentPair(false);
        InputBinding sourceInput = sourceInput("unit-a", "unit-a", digest);
        PhysicalPlanUnit producer = unit(
                "unit-a",
                List.of(sourceInput),
                original.producer().typedOutputs(),
                original.producer().typedDependencies());
        return new UnitPair(producer, original.consumer(), original.edge());
    }

    private static PhysicalPlanUnit sourcePinnedUnit(String id) {
        return sourcePinnedUnitWithDigest(id, "a".repeat(64));
    }

    private static PhysicalPlanUnit sourcePinnedUnitWithDigest(String id, String digest) {
        SourceArtifact sourceArtifact = new SourceArtifact(
                new ArtifactId("artifact-" + id),
                ContentDigest.sha256(digest));
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

    private static ProviderBoundExecutableTaskGraph sourceGraph(PhysicalPlanUnit unit) {
        PhysicalExecutionPlan plan = plan(unit);
        TaskContext context = context(plan, "provider-a");
        return ProviderBoundExecutableTaskGraph.derive(
                plan,
                context.graph(),
                List.of(task(context, "provider-a", List.of(unit), List.of())),
                List.of());
    }

    private static ProviderBoundExecutableTaskGraph separateGraph(UnitPair pair) {
        PhysicalExecutionPlan plan = plan(pair.producer(), pair.consumer());
        TaskContext context = context(plan, "provider-a");
        ExecutableTask producer = task(
                context, "provider-a", List.of(pair.producer()), List.of());
        ExecutableTask consumer = task(
                context, "provider-a", List.of(pair.consumer()), List.of());
        ExecutionArtifactBoundary boundary = executionBoundary(
                pair, context.candidate("provider-a"), context.candidate("provider-a"));
        return ProviderBoundExecutableTaskGraph.derive(
                plan, context.graph(), List.of(producer, consumer), List.of(boundary));
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

    private record TaskContext(
            PhysicalExecutionPlan plan,
            ProviderCompatibilityGraph graph,
            List<ProviderCandidate> candidates) {

        private ProviderCandidate candidate(String provider) {
            return candidates.stream()
                    .filter(value -> value.bindingPin().providerId().equals(ProviderId.of(provider)))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
