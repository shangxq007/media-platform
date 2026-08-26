package com.example.platform.workerfabric.domain.providernative;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
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
import com.example.platform.execution.taskgraph.BoundaryAction;
import com.example.platform.execution.taskgraph.ExecutableTask;
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
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.reuse.MaterializedArtifact;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderNativeLoweringRuntimeAdapterTest {

    private static final BoundaryContractId DIRECT_CONTRACT = BoundaryContractId.of("phase15-direct.v1");

    @Test
    void deterministicLoweringProducesStructurallyEqualPlans() {
        ExecutableTask task = task("provider-a", List.of(unit("unit-a")), List.of());
        StaticProviderExecutionContext context = StaticProviderExecutionContext.fromBinding(task.providerBindingPin());
        TestPlanLowerer lowerer = new TestPlanLowerer();

        TestNativePlan first = lowerer.lower(task, context);
        TestNativePlan second = lowerer.lower(task, context);

        assertEquals(first, second);
        assertEquals(first.invocations(), second.invocations());
    }

    @Test
    void providerBindingPinSurvivesLoweringExactly() {
        ExecutableTask task = task("provider-a", List.of(unit("unit-a")), List.of());

        TestNativePlan plan = new TestPlanLowerer().lower(
                task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin()));

        assertEquals(task.providerBindingPin(), plan.providerBindingPin());
        assertSame(task.providerBindingPin(), plan.providerBindingPin());
    }

    @Test
    void lowererFailsClosedOnProviderBindingMismatch() {
        ExecutableTask task = task("provider-a", List.of(unit("unit-a")), List.of());
        StaticProviderExecutionContext wrongContext = StaticProviderExecutionContext.fromBinding(binding("provider-b"));

        ProviderNativeExecutionFailure failure = assertThrows(
                ProviderNativeExecutionFailure.class,
                () -> new TestPlanLowerer().lower(task, wrongContext));

        assertEquals(ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH, failure.code());
    }

    @Test
    void loweringApiConsumesOnlyOneExecutableTask() throws NoSuchMethodException {
        Class<?>[] parameterTypes = PlanLowerer.class.getMethod(
                "lower", ExecutableTask.class, StaticProviderExecutionContext.class).getParameterTypes();
        assertEquals(2, parameterTypes.length);
        assertEquals(1, Arrays.stream(parameterTypes)
                .filter(ExecutableTask.class::equals)
                .count());
    }

    @Test
    void multiMembershipSameTaskLoweringIsSupportedWhenAlreadyAdmitted() {
        UnitPair pair = dependentPair(false);
        ExecutableTask task = task("provider-a", List.of(pair.producer(), pair.consumer()), List.of());

        TestNativePlan plan = new TestPlanLowerer().lower(
                task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin()));

        assertEquals(2, plan.memberUnitIds().size());
        assertEquals(1, plan.invocations().size(), "same executable task may reduce to one native invocation");
    }

    @Test
    void requiredMaterializationBoundaryFailsClosedForFixtureFlattening() {
        PhysicalPlanUnit unit = unit("unit-a", List.of(), List.of(output("unit-a", true)), List.of());
        BoundaryAction action = new BoundaryAction(
                BoundaryAction.Phase.POST_EXECUTION,
                0,
                new BoundaryAction.MandatoryMaterializationTarget(
                        unit.stepId(),
                        unit.typedOutputs().getFirst(),
                        unit.typedOutputs().getFirst().materializationRequirements().getFirst(),
                        Optional.empty()));
        ExecutableTask task = task("provider-a", List.of(unit), List.of(action));

        ProviderNativeExecutionFailure failure = assertThrows(
                ProviderNativeExecutionFailure.class,
                () -> new TestPlanLowerer().lower(
                        task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin())));

        assertEquals(ProviderNativeFailureCode.REQUIRED_MATERIALIZATION_BOUNDARY_VIOLATION, failure.code());
    }

    @Test
    void unsupportedSemanticsFailClosedWithTypedFailure() {
        ExecutableTask task = task("provider-a", List.of(unit("unit-a")), List.of());

        ProviderNativeExecutionFailure failure = assertThrows(
                ProviderNativeExecutionFailure.class,
                () -> new UnsupportedLowerer().lower(
                        task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin())));

        assertEquals(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS, failure.code());
    }

    @Test
    void runtimeAdapterPlanTypeMismatchFailsClosed() {
        RuntimeExecutionContext context = runtimeContext(task("provider-a", List.of(unit("unit-a")), List.of()));

        ProviderNativeExecutionFailure failure = assertThrows(
                ProviderNativeExecutionFailure.class,
                () -> new StrictTestRuntimeAdapter().adapt(new ForeignNativePlan(
                        context.executableTaskId(), context.providerBindingPin()), context));

        assertEquals(ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN, failure.code());
    }

    @Test
    void executionCommandsRetainPlatformAttemptScopeWithoutCreatingNewLifecycleIdentity() {
        ExecutableTask task = task("provider-a", List.of(unit("unit-a")), List.of());
        TestNativePlan plan = new TestPlanLowerer().lower(
                task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin()));
        RuntimeExecutionContext context = runtimeContext(task);

        RuntimeExecutionBundle bundle = new TestRuntimeAdapter().adapt(plan, context);
        ExecutionCommand command = bundle.commands().getFirst();

        assertEquals(context.platformExecutionAttemptId(), command.platformExecutionAttemptId());
        assertEquals(context.platformOwnershipGeneration(), command.platformOwnershipGeneration());
        assertEquals(context.executableTaskId(), command.executableTaskId());
        assertEquals(context.providerBindingPin(), command.providerBindingPin());
    }

    @Test
    void multipleNativeCommandsMayShareOnePlatformAttempt() {
        ExecutableTask task = task("provider-a", List.of(unit("unit-a")), List.of());
        TestNativePlan plan = new TestNativePlan(
                task.id(),
                task.providerBindingPin(),
                List.of("unit-a"),
                List.of(
                        ProcessInvocationSpec.of("phase15-tool", List.of("first")),
                        ProcessInvocationSpec.of("phase15-tool", List.of("second"))));
        RuntimeExecutionContext context = runtimeContext(task);

        RuntimeExecutionBundle bundle = new TestRuntimeAdapter().adapt(plan, context);

        assertEquals(2, bundle.commands().size());
        assertEquals(context.platformExecutionAttemptId(), bundle.commands().get(0).platformExecutionAttemptId());
        assertEquals(context.platformExecutionAttemptId(), bundle.commands().get(1).platformExecutionAttemptId());
        assertNotEquals(bundle.commands().get(0).sequence(), bundle.commands().get(1).sequence());
    }

    @Test
    void processInvocationDoesNotExposeShellStringAuthority() {
        ProcessInvocationSpec invocation = ProcessInvocationSpec.of("phase15-tool", List.of("--input", "artifact-ref"));

        assertEquals("phase15-tool", invocation.executable());
        assertEquals(List.of("--input", "artifact-ref"), invocation.arguments());
        assertFalse(Arrays.stream(ProcessInvocationSpec.class.getRecordComponents())
                .anyMatch(component -> component.getName().equals("shellCommand")
                        || component.getName().equals("commandLine")));
    }

    @Test
    void runtimeAdapterFailsClosedInsteadOfRebindingProvider() {
        ExecutableTask task = task("provider-a", List.of(unit("unit-a")), List.of());
        TestNativePlan plan = new TestPlanLowerer().lower(
                task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin()));
        RuntimeExecutionContext wrongContext = new RuntimeExecutionContext(
                task.id(), binding("provider-b"), ExecutionAttemptId.of("attempt-1"), ExecutionOwnershipGeneration.first());

        ProviderNativeExecutionFailure failure = assertThrows(
                ProviderNativeExecutionFailure.class,
                () -> new TestRuntimeAdapter().adapt(plan, wrongContext));

        assertEquals(ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH, failure.code());
    }

    @Test
    void runtimeBindingRejectsAbsentUnknownAndDuplicateLogicalInputIdsBeforeAdapterExecution(
            @TempDir Path tempDir) throws IOException {
        ContentDigest digest = ContentDigest.sha256("a".repeat(64));
        ArtifactPin pin = new ArtifactPin(new ArtifactId("source-runtime-input"), digest);
        InputBinding sourceInput = new InputBinding(
                new ExecutionInputId("input-source"),
                "logical-unit-source",
                new ExecutionStepId("unit-source"),
                new RenderNodeId("render-unit-source"),
                null, null, null, null,
                new SourceArtifact(pin.artifactId(), pin.contentDigest()),
                null);
        ExecutableTask task = task(
                "provider-a",
                List.of(unit(
                        "unit-source",
                        List.of(sourceInput),
                        List.of(output("unit-source", false)),
                        List.of())),
                List.of());
        AtomicInteger adapterExecutions = new AtomicInteger();
        RuntimeAdapter<TestNativePlan> adapter = new TestRuntimeAdapter();
        RuntimeCommandExecutor executor = (executionBundle, runtimeLocalInputs) -> {
            adapterExecutions.incrementAndGet();
            return new ProviderExecutionOutput(new java.io.ByteArrayInputStream(new byte[0]));
        };
        ProviderNativeRuntimeBinding<TestNativePlan> runtimeBinding =
                new ProviderNativeRuntimeBinding<>(new TestPlanLowerer(), adapter, executor);
        Path localPath = Files.write(tempDir.resolve("source-runtime-input.bin"), new byte[] {1, 2, 3});
        MaterializedArtifact local = new MaterializedArtifact(
                pin, localPath, Files.size(localPath));
        MaterializedExecutionInput expected = new MaterializedExecutionInput(
                sourceInput.inputId(), pin, local);
        MaterializedExecutionInput unknown = new MaterializedExecutionInput(
                new ExecutionInputId("input-unknown"), pin, local);

        assertThrows(IllegalArgumentException.class, () -> runtimeBinding.execute(
                task, runtimeContext(task), List.of()));
        assertThrows(IllegalArgumentException.class, () -> runtimeBinding.execute(
                task, runtimeContext(task), List.of(unknown)));
        assertThrows(IllegalArgumentException.class, () -> runtimeBinding.execute(
                task, runtimeContext(task), List.of(expected, expected)));
        assertEquals(0, adapterExecutions.get());
    }

    private static RuntimeExecutionContext runtimeContext(ExecutableTask task) {
        return new RuntimeExecutionContext(
                task.id(), task.providerBindingPin(), ExecutionAttemptId.of("attempt-1"), ExecutionOwnershipGeneration.first());
    }

    private static final class TestPlanLowerer implements PlanLowerer<TestNativePlan> {
        @Override
        public TestNativePlan lower(ExecutableTask task, StaticProviderExecutionContext context) {
            task.providerBindingPin().equals(context.providerBindingPin());
            if (!task.providerBindingPin().equals(context.providerBindingPin())) {
                throw new ProviderNativeExecutionFailure(
                        ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH,
                        "fixture lowerer refuses provider rebinding");
            }
            if (task.boundaryActions().stream()
                    .anyMatch(action -> action.target() instanceof BoundaryAction.MandatoryMaterializationTarget)) {
                throw new ProviderNativeExecutionFailure(
                        ProviderNativeFailureCode.REQUIRED_MATERIALIZATION_BOUNDARY_VIOLATION,
                        "fixture lowerer refuses to flatten required Artifact materialization boundary");
            }
            List<String> memberUnitIds = task.memberships().stream()
                    .map(member -> member.physicalPlanUnitId().value())
                    .toList();
            return new TestNativePlan(
                    task.id(),
                    task.providerBindingPin(),
                    memberUnitIds,
                    List.of(ProcessInvocationSpec.of("phase15-test-provider", memberUnitIds)));
        }
    }

    private static final class UnsupportedLowerer implements PlanLowerer<TestNativePlan> {
        @Override
        public TestNativePlan lower(ExecutableTask task, StaticProviderExecutionContext context) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "fixture lowerer does not support this task");
        }
    }

    private static class TestRuntimeAdapter implements RuntimeAdapter<TestNativePlan> {
        @Override
        public RuntimeExecutionBundle adapt(TestNativePlan nativePlan, RuntimeExecutionContext context) {
            nativePlan.requireTaskAndBinding(
                    context.executableTaskId(), context.providerBindingPin(), ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH);
            List<ExecutionCommand> commands = java.util.stream.IntStream.range(0, nativePlan.invocations().size())
                    .mapToObj(index -> new ExecutionCommand(
                            nativePlan.executableTaskId(),
                            nativePlan.providerBindingPin(),
                            context.platformExecutionAttemptId(),
                            context.platformOwnershipGeneration(),
                            index,
                            nativePlan.invocations().get(index)))
                    .toList();
            return new RuntimeExecutionBundle(
                    nativePlan.executableTaskId(),
                    nativePlan.providerBindingPin(),
                    context.platformExecutionAttemptId(),
                    context.platformOwnershipGeneration(),
                    commands);
        }
    }

    private static final class StrictTestRuntimeAdapter implements RuntimeAdapter<ProviderNativeExecutionPlan> {
        @Override
        public RuntimeExecutionBundle adapt(ProviderNativeExecutionPlan nativePlan, RuntimeExecutionContext context) {
            if (!(nativePlan instanceof TestNativePlan testNativePlan)) {
                throw new ProviderNativeExecutionFailure(
                        ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN,
                        "fixture runtime adapter supports only TestNativePlan");
            }
            return new TestRuntimeAdapter().adapt(testNativePlan, context);
        }
    }

    private record TestNativePlan(
            com.example.platform.execution.taskgraph.ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin,
            List<String> memberUnitIds,
            List<ProcessInvocationSpec> invocations) implements ProviderNativeExecutionPlan {
        private TestNativePlan {
            if (memberUnitIds == null || memberUnitIds.isEmpty()) {
                throw new ProviderNativeExecutionFailure(
                        ProviderNativeFailureCode.MALFORMED_NATIVE_PLAN,
                        "fixture native plan requires at least one member");
            }
            memberUnitIds = List.copyOf(memberUnitIds);
            invocations = List.copyOf(invocations);
        }
    }

    private record ForeignNativePlan(
            com.example.platform.execution.taskgraph.ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin) implements ProviderNativeExecutionPlan {
    }

    private static ExecutableTask task(String provider, List<PhysicalPlanUnit> units, List<BoundaryAction> actions) {
        PhysicalExecutionPlan plan = plan(units.toArray(PhysicalPlanUnit[]::new));
        TaskContext context = context(plan, provider);
        return ExecutableTask.create(compositionDecision(context, provider, units), actions);
    }

    private static CompositionDecision compositionDecision(
            TaskContext context, String provider, List<PhysicalPlanUnit> units) {
        ProviderCandidate candidate = context.candidate(provider);
        ProviderBindingPin binding = candidate.bindingPin();
        List<ExecutableTaskMembership> memberships = ExecutableTaskMembership.canonicalForUnits(units);
        ProviderLocalCompositionRequest request = ProviderLocalCompositionRequest.of(
                memberships,
                context.graph(),
                candidate,
                new ProviderCompositionDeclaration(binding, NativePipelineSupport.SUPPORTED),
                List.of());
        return ProviderLocalCompositionEvaluator.evaluate(request);
    }

    private static TaskContext context(PhysicalExecutionPlan plan, String... providers) {
        List<ProviderCandidate> candidates = Arrays.stream(providers)
                .map(ProviderNativeLoweringRuntimeAdapterTest::candidate)
                .toList();
        ProviderCompatibilityGraph graph = ProviderCompatibilityGraph.build(
                plan,
                plan.units().stream().map(CompatibilityRequest::forUnit).toList(),
                candidates,
                List.of());
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
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(DIRECT_CONTRACT),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(binding, descriptor, contract, profile, staticCompatibility);
    }

    private static ProviderBindingPin binding(String provider) {
        ProviderCapabilityProfileVersionOrDigest profile =
                ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));
        return new ProviderBindingPin(
                ProviderId.of(provider),
                ProviderImplementationId.of(provider + ".native"),
                ProviderVersion.of("1.0.0"),
                ProviderExecutionContractVersion.of(1, 0),
                profile,
                List.of());
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
        PhysicalPlanUnit producer = unit("unit-a", List.of(), List.of(output("unit-a", mandatoryMaterialization)), List.of(edge));
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
        PhysicalPlanUnit consumer = unit("unit-b", List.of(input), List.of(output("unit-b", false)), List.of(edge));
        return new UnitPair(producer, consumer, edge);
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

    private static PhysicalPlanUnit unit(String id) {
        return unit(id, List.of(), List.of(output(id, false)), List.of());
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
                mandatoryMaterialization ? List.of(AudioProcessMaterializationRequirement.of(null, null, null)) : List.of(),
                List.of(new IntermediateArtifactExpectation(
                        new LogicalArtifactId("logical-artifact-" + id), RenderOutputRole.RENDER_MASTER)),
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
