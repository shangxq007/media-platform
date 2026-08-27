package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableInputProjection;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FfmpegProviderContractTest {

    @Test
    void stable_provider_and_implementation_identities_do_not_collapse_into_runtime_identity() {
        assertThat(FfmpegCpuProvider.PROVIDER_ID.value()).isEqualTo("ffmpeg");
        assertThat(FfmpegCpuProvider.IMPLEMENTATION_ID.value())
                .isEqualTo("ffmpeg.cpu.native-pull.v1")
                .isNotEqualTo(FfmpegCpuProvider.PROVIDER_ID.value())
                .isNotEqualTo(WorkerRuntimeId.of("runtime-ffmpeg-cpu").value())
                .isNotEqualTo(ExecutionBackend.NATIVE_PULL_WORKER.name())
                .doesNotContain("device", "worker", "backend");
        assertThat(FfmpegCpuProvider.DESCRIPTOR.providerId()).isEqualTo(FfmpegCpuProvider.PROVIDER_ID);
        assertThat(FfmpegCpuProvider.DESCRIPTOR.providerImplementationId())
                .isEqualTo(FfmpegCpuProvider.IMPLEMENTATION_ID);
        assertThat(FfmpegCpuProvider.RUNTIME_SUPPORT_REQUIREMENT.providerBindingPin())
                .isEqualTo(FfmpegCpuProvider.BINDING);
        assertThat(FfmpegCpuProvider.RUNTIME_SUPPORT_REQUIREMENT.requiredRuntimeKind())
                .isEqualTo(RuntimeLifecycleKind.EPHEMERAL_TASK);
        assertThat(FfmpegCpuProvider.CAPABILITY_PROFILE.supportDeclarations())
                .singleElement()
                .satisfies(support -> assertThat(support.capabilityId().value())
                        .isEqualTo("media.transcode"));
    }

    @Test
    void lowering_is_deterministic_for_equivalent_exact_cpu_transcode_tasks() {
        ExecutableTask firstTask = exactTask(FfmpegCpuProvider.BINDING, "transcode", 1, 1, 1);
        ExecutableTask equivalentTask = exactTask(FfmpegCpuProvider.BINDING, "transcode", 1, 1, 1);
        FfmpegCpuTranscodeLowerer lowerer = new FfmpegCpuTranscodeLowerer();

        FfmpegCpuTranscodePlan first = lowerer.lower(
                firstTask, StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING));
        FfmpegCpuTranscodePlan second = lowerer.lower(
                equivalentTask, StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING));

        assertThat(first).isEqualTo(second);
        assertThat(first.inputId()).isEqualTo(new ExecutionInputId("input-media"));
        assertThat(first.outputId()).isEqualTo(new ExecutionOutputId("output-media"));
    }

    @Test
    void lowering_rejects_binding_operation_membership_input_and_output_mismatch_fail_closed() {
        FfmpegCpuTranscodeLowerer lowerer = new FfmpegCpuTranscodeLowerer();
        assertFailure(
                () -> lowerer.lower(
                        exactTask(mock(ProviderBindingPin.class), "transcode", 1, 1, 1),
                        StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING)),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        assertFailure(
                () -> lowerer.lower(
                        exactTask(FfmpegCpuProvider.BINDING, "decode", 1, 1, 1),
                        StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        assertFailure(
                () -> lowerer.lower(
                        exactTask(FfmpegCpuProvider.BINDING, "transcode", 2, 1, 1),
                        StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING)),
                ProviderNativeFailureCode.ILLEGAL_MULTI_MEMBERSHIP_LOWERING);
        assertFailure(
                () -> lowerer.lower(
                        exactTask(FfmpegCpuProvider.BINDING, "transcode", 1, 2, 1),
                        StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        assertFailure(
                () -> lowerer.lower(
                        exactTask(FfmpegCpuProvider.BINDING, "transcode", 1, 1, 2),
                        StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY);
        ExecutableTask parameterized = exactTask(
                FfmpegCpuProvider.BINDING, "transcode", 1, 1, 1);
        when(parameterized.memberships().getFirst().physicalPlanUnit()
                .capabilityRequirementRefs())
                .thenReturn(List.of(mock(CapabilityRequirementRef.class)));
        assertFailure(
                () -> lowerer.lower(
                        parameterized,
                        StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    @Test
    void adapter_emits_fixed_argv_with_materialized_input_token_and_stdout_only() {
        ExecutableTask task = exactTask(FfmpegCpuProvider.BINDING, "transcode", 1, 1, 1);
        FfmpegCpuTranscodePlan plan = new FfmpegCpuTranscodeLowerer().lower(
                task, StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING));
        RuntimeExecutionContext context = new RuntimeExecutionContext(
                task.id(), FfmpegCpuProvider.BINDING,
                ExecutionAttemptId.of("attempt-ffmpeg"), ExecutionOwnershipGeneration.first());

        ProcessInvocationSpec invocation = (ProcessInvocationSpec) new FfmpegCpuRuntimeAdapter(
                Path.of("/usr/bin/ffmpeg")).adapt(plan, context)
                .commands().getFirst().invocationSpec();

        assertThat(invocation.executable()).isEqualTo("/usr/bin/ffmpeg");
        assertThat(invocation.arguments())
                .contains("libx264", "yuv420p", "pipe:1")
                .containsOnlyOnce(FfmpegCpuRuntimeAdapter.materializedInputToken(plan.inputId()));
        assertThat(invocation.arguments()).noneMatch(value -> value.contains(";") || value.contains("&&"));
        assertThat(Arrays.stream(ProcessInvocationSpec.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("shellCommand", "commandLine");
    }

    private static void assertFailure(ThrowingCall call, ProviderNativeFailureCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static ExecutableTask exactTask(
            ProviderBindingPin binding,
            String operation,
            int membershipCount,
            int inputCount,
            int outputCount) {
        ExecutableTask task = mock(ExecutableTask.class);
        when(task.id()).thenReturn(new ExecutableTaskId("a".repeat(64)));
        when(task.providerBindingPin()).thenReturn(binding);
        List<ExecutableTaskMembership> memberships = java.util.stream.IntStream.range(0, membershipCount)
                .mapToObj(index -> membership(operation, inputCount))
                .toList();
        List<ExecutableInputProjection> runtimeInputs = java.util.stream.IntStream.range(0, inputCount)
                .mapToObj(FfmpegProviderContractTest::inputProjection)
                .toList();
        when(task.memberships()).thenReturn(memberships);
        when(task.requiredRuntimeInputs()).thenReturn(runtimeInputs);
        when(task.authoritativeOutputIds()).thenReturn(java.util.stream.IntStream.range(0, outputCount)
                .mapToObj(index -> new ExecutionOutputId(index == 0 ? "output-media" : "output-media-" + index))
                .toList());
        return task;
    }

    private static ExecutableTaskMembership membership(String operation, int inputCount) {
        ExecutableTaskMembership membership = mock(ExecutableTaskMembership.class);
        PhysicalPlanUnit unit = mock(PhysicalPlanUnit.class);
        List<InputBinding> inputs = java.util.stream.IntStream.range(0, inputCount)
                .mapToObj(index -> mock(InputBinding.class)).toList();
        List<OutputDeclaration> outputs = List.of(mock(OutputDeclaration.class));
        when(unit.operationKey()).thenReturn(operation);
        when(unit.typedInputs()).thenReturn(inputs);
        when(unit.typedOutputs()).thenReturn(outputs);
        when(unit.capabilityRequirementRefs()).thenReturn(List.of());
        when(unit.executionIntentRefs()).thenReturn(List.of());
        when(unit.temporalWindow()).thenReturn(null);
        when(unit.propagatedExtent()).thenReturn(null);
        when(unit.executionCoverage()).thenReturn(null);
        when(membership.physicalPlanUnit()).thenReturn(unit);
        return membership;
    }

    private static ExecutableInputProjection inputProjection(int index) {
        ExecutableInputProjection projection = mock(ExecutableInputProjection.class);
        when(projection.inputId()).thenReturn(new ExecutionInputId(
                index == 0 ? "input-media" : "input-media-" + index));
        return projection;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
