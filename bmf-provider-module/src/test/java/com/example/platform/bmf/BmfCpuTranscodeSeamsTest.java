package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableInputProjection;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BmfCpuTranscodeSeamsTest {
    private static final ExecutableTaskId TASK_ID = new ExecutableTaskId("a".repeat(64));
    private static final ExecutionInputId INPUT_ID = new ExecutionInputId("source");
    private static final ExecutionOutputId OUTPUT_ID = new ExecutionOutputId("rendered");
    private static final ContractVersionRange RANGE =
            ContractVersionRange.exactly(ContractVersion.of(1, 0));

    @Test
    void exact_single_input_render_units_lower_to_the_bound_provider_plan() {
        var lowerer = new BmfCpuTranscodeLowerer();
        for (String[] unit : List.of(
                new String[]{"decode", "video.decode"},
                new String[]{"composite", "render.composite"},
                new String[]{"encode", "render.output"},
                new String[]{"transcode", "media.transcode"})) {
            var plan = lowerer.lower(task(unit[0], unit[1]),
                    StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING));
            assertThat(plan.operation()).isEqualTo(BmfOperation.ofOperationKey(unit[0]));
            assertThat(plan.executableTaskId()).isEqualTo(TASK_ID);
            assertThat(plan.inputId()).isEqualTo(INPUT_ID);
            assertThat(plan.outputId()).isEqualTo(OUTPUT_ID);
            assertThat(plan.sourceWindow()).isEmpty();
        }
    }

    @Test
    void decode_carries_the_authored_source_window_only() {
        var unit = task("decode", "video.decode");
        var unitMock = unit.memberships().getFirst().physicalPlanUnit();
        when(unitMock.temporalWindow()).thenReturn(new RenderSampleWindow(
                MediaTime.ofMillis(1500), MediaTime.ofMillis(4000), FrameRate.of(25, 1)));
        var plan = new BmfCpuTranscodeLowerer().lower(unit,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING));
        assertThat(plan.sourceWindow()).contains(new BmfCpuTranscodePlan.SourceWindow(
                MediaTime.ofMillis(1500), MediaTime.ofMillis(2500)));
    }

    @Test
    void non_decode_units_reject_authored_windows() {
        var unit = task("encode", "render.output");
        when(unit.memberships().getFirst().physicalPlanUnit().temporalWindow())
                .thenReturn(new RenderSampleWindow(
                        MediaTime.ZERO, MediaTime.ofMillis(1000), FrameRate.of(25, 1)));
        assertFailure(() -> new BmfCpuTranscodeLowerer().lower(unit,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    @Test
    void composite_rejects_timed_text_inputs() {
        var unit = task("composite", "render.composite");
        var input = mock(InputBinding.class);
        when(input.dependencyVariant()).thenReturn(mock(RenderDependency.CompositeInput.class));
        when(unit.memberships().getFirst().physicalPlanUnit().typedInputs())
                .thenReturn(List.of(input));
        assertFailure(() -> new BmfCpuTranscodeLowerer().lower(unit,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    @Test
    void missing_capability_or_unknown_operation_fails_before_runtime() {
        var lowerer = new BmfCpuTranscodeLowerer();
        assertFailure(() -> lowerer.lower(task("raster", "subtitle.rasterize"),
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        assertFailure(() -> lowerer.lower(task("encode", "media.transcode"),
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        var wrongContext = mock(StaticProviderExecutionContext.class);
        when(wrongContext.providerBindingPin()).thenReturn(mock(ProviderBindingPin.class));
        assertFailure(() -> lowerer.lower(task("transcode", "media.transcode"), wrongContext),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        assertThatThrownBy(() -> lowerer.lower(null,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void adapter_emits_the_versioned_bmf_graph_with_operation_and_source_window() {
        var adapter = new BmfCpuRuntimeAdapter(Path.of("/usr/bin/python3.9"));
        var plan = new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING, BmfOperation.DECODE,
                INPUT_ID, OUTPUT_ID, Optional.of(new BmfCpuTranscodePlan.SourceWindow(
                        MediaTime.ofMillis(1500), MediaTime.ofMillis(2500))));
        var bundle = adapter.adapt(plan, runtimeContext(TASK_ID));
        assertThat(bundle.commands()).hasSize(1);
        var invocation = (ProcessInvocationSpec) bundle.commands().getFirst().invocationSpec();
        assertThat(invocation.executable()).isEqualTo("/usr/bin/python3.9");
        assertThat(invocation.arguments()).containsExactly("-s", "-c",
                BmfCpuRuntimeAdapter.GRAPH_PROGRAM,
                BmfCpuRuntimeAdapter.materializedInputToken(INPUT_ID),
                "platform-provider-output.bin", "decode", "1.5", "2.5");
        assertFailure(() -> adapter.adapt(plan,
                runtimeContext(new ExecutableTaskId("b".repeat(64)))),
                ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH);
        assertThatThrownBy(() -> new BmfCpuRuntimeAdapter(Path.of("python3.9")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExecutableTask task(String operation, String capabilityId) {
        var task = mock(ExecutableTask.class);
        var membership = mock(ExecutableTaskMembership.class);
        var unit = mock(PhysicalPlanUnit.class);
        var input = mock(ExecutableInputProjection.class);
        when(task.id()).thenReturn(TASK_ID);
        when(task.providerBindingPin()).thenReturn(BmfCpuProvider.BINDING);
        when(task.memberships()).thenReturn(List.of(membership));
        when(membership.physicalPlanUnit()).thenReturn(unit);
        when(unit.operationKey()).thenReturn(operation);
        when(unit.capabilityRequirementRefs()).thenReturn(List.of(
                new CapabilityRequirementRef(CapabilityRequirement.of(
                        CapabilityId.of(capabilityId), RANGE))));
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
