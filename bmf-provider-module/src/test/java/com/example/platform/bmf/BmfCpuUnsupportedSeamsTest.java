package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BmfCpuUnsupportedSeamsTest {

    private static final ExecutableTaskId TASK_ID = new ExecutableTaskId("a".repeat(64));
    private static final ExecutableTaskId OTHER_TASK_ID = new ExecutableTaskId("b".repeat(64));

    @Test
    void native_plan_is_opaque_and_carries_only_canonical_task_and_binding() {
        BmfCpuNativePlan plan = new BmfCpuNativePlan(TASK_ID, BmfCpuProvider.BINDING);

        assertThat(plan.executableTaskId()).isEqualTo(TASK_ID);
        assertThat(plan.providerBindingPin()).isEqualTo(BmfCpuProvider.BINDING);
        assertThat(Arrays.stream(BmfCpuNativePlan.class.getRecordComponents())
                        .map(RecordComponent::getName))
                .containsExactly("executableTaskId", "providerBindingPin");
        assertThatNullPointerException()
                .isThrownBy(() -> new BmfCpuNativePlan(null, BmfCpuProvider.BINDING));
        assertThatNullPointerException()
                .isThrownBy(() -> new BmfCpuNativePlan(TASK_ID, null));
    }

    @Test
    void lowerer_requires_exact_bmf_binding_then_remains_typed_unsupported() {
        BmfCpuUnsupportedLowerer lowerer = new BmfCpuUnsupportedLowerer();
        ExecutableTask exactTask = task(TASK_ID, BmfCpuProvider.BINDING);
        StaticProviderExecutionContext exactContext =
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING);
        ProviderBindingPin otherBinding = otherBinding();

        assertFailure(
                () -> lowerer.lower(task(TASK_ID, otherBinding), exactContext),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        assertFailure(
                () -> lowerer.lower(
                        exactTask, StaticProviderExecutionContext.fromBinding(otherBinding)),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        assertFailure(
                () -> lowerer.lower(exactTask, exactContext),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        assertThatNullPointerException().isThrownBy(() -> lowerer.lower(null, exactContext));
        assertThatNullPointerException().isThrownBy(() -> lowerer.lower(exactTask, null));
    }

    @Test
    void runtime_adapter_rejects_every_mismatch_and_never_emits_commands() {
        BmfCpuUnsupportedRuntimeAdapter adapter = new BmfCpuUnsupportedRuntimeAdapter();
        BmfCpuNativePlan exactPlan = new BmfCpuNativePlan(TASK_ID, BmfCpuProvider.BINDING);
        RuntimeExecutionContext exactContext = runtimeContext(TASK_ID, BmfCpuProvider.BINDING);
        ProviderBindingPin otherBinding = otherBinding();

        assertFailure(
                () -> adapter.adapt(
                        exactPlan, runtimeContext(OTHER_TASK_ID, BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH);
        assertFailure(
                () -> adapter.adapt(exactPlan, runtimeContext(TASK_ID, otherBinding)),
                ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH);
        assertFailure(
                () -> adapter.adapt(
                        new BmfCpuNativePlan(TASK_ID, otherBinding),
                        runtimeContext(TASK_ID, otherBinding)),
                ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH);
        assertFailure(
                () -> adapter.adapt(exactPlan, exactContext),
                ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN);
        assertThatNullPointerException().isThrownBy(() -> adapter.adapt(null, exactContext));
        assertThatNullPointerException().isThrownBy(() -> adapter.adapt(exactPlan, null));
    }

    private static ExecutableTask task(ExecutableTaskId taskId, ProviderBindingPin binding) {
        ExecutableTask task = mock(ExecutableTask.class);
        when(task.id()).thenReturn(taskId);
        when(task.providerBindingPin()).thenReturn(binding);
        return task;
    }

    private static RuntimeExecutionContext runtimeContext(
            ExecutableTaskId taskId, ProviderBindingPin binding) {
        return new RuntimeExecutionContext(
                taskId,
                binding,
                ExecutionAttemptId.of("attempt-bmf"),
                ExecutionOwnershipGeneration.first());
    }

    private static ProviderBindingPin otherBinding() {
        return new ProviderBindingPin(
                ProviderId.of("other"),
                ProviderImplementationId.of("other.cpu.v1"),
                ProviderVersion.of("1.0.0"),
                ProviderExecutionContractVersion.of(1, 0),
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0)),
                List.of());
    }

    private static void assertFailure(ThrowingCall call, ProviderNativeFailureCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
