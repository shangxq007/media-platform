package com.example.platform.bmf;

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
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableInputProjection;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BmfCpuTranscodeSeamsTest {
    private static final ExecutableTaskId TASK_ID = new ExecutableTaskId("a".repeat(64));
    private static final ExecutionInputId INPUT_ID = new ExecutionInputId("source");
    private static final ExecutionOutputId OUTPUT_ID = new ExecutionOutputId("rendered");

    @Test
    void exact_single_input_transcode_lowers_to_the_bound_provider_plan() {
        ExecutableTask task = task("transcode");
        var plan = new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING));
        assertThat(plan).isEqualTo(new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING,
                INPUT_ID, OUTPUT_ID));
    }

    @Test
    void other_operations_and_wrong_bindings_fail_before_runtime() {
        var lowerer = new BmfCpuTranscodeLowerer();
        assertFailure(() -> lowerer.lower(task("raster"),
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        var wrongContext = mock(StaticProviderExecutionContext.class);
        when(wrongContext.providerBindingPin()).thenReturn(mock(ProviderBindingPin.class));
        assertFailure(() -> lowerer.lower(task("transcode"), wrongContext),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        assertThatThrownBy(() -> lowerer.lower(null,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void adapter_emits_the_versioned_bmf_graph_with_exact_input_and_output() {
        var plan = new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING, INPUT_ID, OUTPUT_ID);
        var adapter = new BmfCpuRuntimeAdapter(Path.of("/usr/bin/python3.9"));
        var bundle = adapter.adapt(plan, runtimeContext(TASK_ID));
        assertThat(bundle.commands()).hasSize(1);
        var invocation = (ProcessInvocationSpec) bundle.commands().getFirst().invocationSpec();
        assertThat(invocation.executable()).isEqualTo("/usr/bin/python3.9");
        assertThat(invocation.arguments()).containsExactly("-s", "-c",
                BmfCpuRuntimeAdapter.GRAPH_PROGRAM,
                BmfCpuRuntimeAdapter.materializedInputToken(INPUT_ID),
                "platform-provider-output.bin");
        assertFailure(() -> adapter.adapt(plan,
                runtimeContext(new ExecutableTaskId("b".repeat(64)))),
                ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH);
        assertThatThrownBy(() -> new BmfCpuRuntimeAdapter(Path.of("python3.9")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExecutableTask task(String operation) {
        var task = mock(ExecutableTask.class);
        var membership = mock(ExecutableTaskMembership.class);
        var unit = mock(PhysicalPlanUnit.class);
        var input = mock(ExecutableInputProjection.class);
        when(task.id()).thenReturn(TASK_ID);
        when(task.providerBindingPin()).thenReturn(BmfCpuProvider.BINDING);
        when(task.memberships()).thenReturn(List.of(membership));
        when(membership.physicalPlanUnit()).thenReturn(unit);
        when(unit.operationKey()).thenReturn(operation);
        when(unit.capabilityRequirementRefs()).thenReturn(List.of());
        when(unit.executionIntentRefs()).thenReturn(List.of());
        when(unit.typedInputs()).thenReturn(List.of(mock(InputBinding.class)));
        when(unit.typedOutputs()).thenReturn(List.of(mock(OutputDeclaration.class)));
        when(task.requiredRuntimeInputs()).thenReturn(List.of(input));
        when(input.inputId()).thenReturn(INPUT_ID);
        when(task.authoritativeOutputIds()).thenReturn(List.of(OUTPUT_ID));
        return task;
    }

    private static RuntimeExecutionContext runtimeContext(ExecutableTaskId taskId) {
        return new RuntimeExecutionContext(taskId, BmfCpuProvider.BINDING,
                ExecutionAttemptId.of("attempt-bmf"), ExecutionOwnershipGeneration.first());
    }

    private static void assertFailure(Runnable call, ProviderNativeFailureCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(
                ProviderNativeExecutionFailure.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
