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
import com.example.platform.render.domain.renderplan.RenderExtent;
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

    @Test
    void contiguous_multi_input_composite_lowers_to_a_concatenated_plan() {
        var task = compositeTask(new int[][]{{0, 2000}, {2000, 3000}}, 0, 3000);
        var plan = new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING));
        assertThat(plan.operation()).isEqualTo(BmfOperation.COMPOSITE);
        assertThat(plan.compositeInputs()).hasSize(2);
        assertThat(plan.compositeDuration()).contains(MediaTime.ofMillis(3000));
        assertThat(plan.compositeFrameRate()).contains(FrameRate.of(24, 1));
        assertThat(plan.compositeInputs().getFirst().start()).isEqualTo(MediaTime.ZERO);
        assertThat(plan.compositeInputs().getLast().end()).isEqualTo(MediaTime.ofMillis(3000));
    }

    @Test
    void single_timed_composite_input_keeps_the_single_input_graph() {
        var task = compositeTask(new int[][]{{0, 2000}}, 0, 2000);
        var plan = new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING));
        assertThat(plan.compositeInputs()).isEmpty();
        assertThat(plan.compositeDuration()).isEmpty();
        assertThat(plan.inputId()).isEqualTo(new ExecutionInputId("in-0"));
    }

    @Test
    void composite_with_a_timeline_gap_is_rejected() {
        var task = compositeTask(new int[][]{{0, 2000}, {2500, 3000}}, 0, 3000);
        assertFailure(() -> new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    @Test
    void composite_with_overlapping_inputs_is_rejected() {
        var task = compositeTask(new int[][]{{0, 2000}, {1500, 3000}}, 0, 3000);
        assertFailure(() -> new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    @Test
    void composite_with_a_timed_text_input_is_rejected() {
        var task = compositeTask(new int[][]{{0, 2000}, {2000, 3000}}, 0, 3000);
        var unit = task.memberships().getFirst().physicalPlanUnit();
        var text = mock(InputBinding.class);
        when(text.inputId()).thenReturn(new ExecutionInputId("text-0"));
        when(text.dependencyVariant()).thenReturn(mock(RenderDependency.CompositeInput.class));
        var original = unit.typedInputs();
        when(unit.typedInputs()).thenReturn(List.of(original.getFirst(), text));
        assertFailure(() -> new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    @Test
    void adapter_emits_the_multi_input_concat_graph() {
        var adapter = new BmfCpuRuntimeAdapter(Path.of("/usr/bin/" + "python3.9"));
        var second = new ExecutionInputId("in-1");
        var plan = new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING, BmfOperation.COMPOSITE,
                INPUT_ID, OUTPUT_ID, Optional.empty(),
                List.of(new BmfCpuTranscodePlan.TimedInput(INPUT_ID, MediaTime.ZERO, MediaTime.ofMillis(2000)),
                        new BmfCpuTranscodePlan.TimedInput(second, MediaTime.ofMillis(2000), MediaTime.ofMillis(3000))),
                Optional.of(MediaTime.ofMillis(3000)), Optional.of(FrameRate.of(24, 1)));
        var bundle = adapter.adapt(plan, runtimeContext(TASK_ID));
        var invocation = (ProcessInvocationSpec) bundle.commands().getFirst().invocationSpec();
        assertThat(invocation.arguments()).containsExactly("-s", "-c",
                BmfCpuRuntimeAdapter.COMPOSITE_GRAPH_PROGRAM, "2",
                BmfCpuRuntimeAdapter.materializedInputToken(INPUT_ID),
                BmfCpuRuntimeAdapter.materializedInputToken(second),
                "platform-provider-output.bin", "3", "24/1");
    }

    @Test
    void multitrack_lowers_overlapping_video_and_audio_with_extent_relative_placement() {
        var task = compositeTask(new int[][]{{1500, 3500}, {1000, 3000}, {1000, 2500}, {2000, 3000}},
                1000, 4000);
        var unit = task.memberships().getFirst().physicalPlanUnit();
        when(unit.operationKey()).thenReturn("multitrack-composite");
        when(unit.capabilityRequirementRefs()).thenReturn(List.of(new CapabilityRequirementRef(
                CapabilityRequirement.of(CapabilityId.of("render.multitrack-composite"), RANGE))));
        var tracks = unit.typedInputs();
        var timeline = List.of(
                new RenderDependency.TimedTrackInput(MediaTime.ofMillis(1500), MediaTime.ofMillis(3500), 1, false),
                new RenderDependency.TimedTrackInput(MediaTime.ofMillis(1000), MediaTime.ofMillis(3000), 0, false),
                new RenderDependency.TimedTrackInput(MediaTime.ofMillis(1000), MediaTime.ofMillis(2500), 2, true),
                new RenderDependency.TimedTrackInput(MediaTime.ofMillis(2000), MediaTime.ofMillis(3000), 3, true));
        for (int index = 0; index < tracks.size(); index++) {
            when(tracks.get(index).dependencyVariant()).thenReturn(timeline.get(index));
        }
        var plan = new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING));
        var adapter = new BmfCpuRuntimeAdapter(Path.of("/usr/bin/python3.9"));
        var invocation = (ProcessInvocationSpec) adapter.adapt(plan, runtimeContext(TASK_ID))
                .commands().getFirst().invocationSpec();
        assertThat(invocation.arguments()).containsExactly("-s", "-c", BmfCpuRuntimeAdapter.MULTITRACK_GRAPH_PROGRAM,
                "4", "@platform-materialized-input:in-1", "@platform-materialized-input:in-0",
                "@platform-materialized-input:in-2", "@platform-materialized-input:in-3",
                "platform-provider-output.bin", "3", "24/1",
                "V,0,2,1.0,0,0.0,0.0,0.0,1.0,1.0,0.0,0.0,1.0,0.0,1.0,1.0",
                "V,0.5,2.5,1.0,0,0.0,0.0,0.0,1.0,1.0,0.0,0.0,1.0,0.0,1.0,1.0",
                "A,0,1.5,1.0,0,0.0,0.0,0.0,1.0,1.0,0.0,0.0,1.0,0.0,1.0,1.0",
                "A,1,2,1.0,0,0.0,0.0,0.0,1.0,1.0,0.0,0.0,1.0,0.0,1.0,1.0");
        when(tracks.get(0).dependencyVariant()).thenReturn(new RenderDependency.TimedTrackInput(
                MediaTime.ofMillis(1500), MediaTime.ofMillis(3500), 0, false));
        assertFailure(() -> new BmfCpuTranscodeLowerer().lower(task,
                StaticProviderExecutionContext.fromBinding(BmfCpuProvider.BINDING)),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    private static ExecutableTask compositeTask(int[][] intervals, int extentStartMillis,
            int extentEndMillis) {
        var task = mock(ExecutableTask.class);
        var membership = mock(ExecutableTaskMembership.class);
        var unit = mock(PhysicalPlanUnit.class);
        when(task.id()).thenReturn(TASK_ID);
        when(task.providerBindingPin()).thenReturn(BmfCpuProvider.BINDING);
        when(task.memberships()).thenReturn(List.of(membership));
        when(membership.physicalPlanUnit()).thenReturn(unit);
        when(unit.operationKey()).thenReturn("composite");
        when(unit.propagatedExtent()).thenReturn(new RenderExtent(
                MediaTime.ofMillis(extentStartMillis), MediaTime.ofMillis(extentEndMillis), FrameRate.of(24, 1)));
        when(unit.capabilityRequirementRefs()).thenReturn(List.of(
                new CapabilityRequirementRef(CapabilityRequirement.of(
                        CapabilityId.of("render.composite"), RANGE))));
        when(unit.executionIntentRefs()).thenReturn(List.of());
        var inputs = new java.util.ArrayList<InputBinding>();
        var runtimeInputs = new java.util.ArrayList<com.example.platform.execution.taskgraph.ExecutableInputProjection>();
        for (int index = 0; index < intervals.length; index++) {
            var id = new ExecutionInputId("in-" + index);
            var binding = mock(InputBinding.class);
            when(binding.inputId()).thenReturn(id);
            when(binding.dependencyVariant()).thenReturn(new RenderDependency.TimedCompositeInput(
                    MediaTime.ofMillis(intervals[index][0]), MediaTime.ofMillis(intervals[index][1])));
            inputs.add(binding);
            var runtime = mock(com.example.platform.execution.taskgraph.ExecutableInputProjection.class);
            when(runtime.inputId()).thenReturn(id);
            runtimeInputs.add(runtime);
        }
        when(unit.typedInputs()).thenReturn(List.copyOf(inputs));
        when(unit.typedOutputs()).thenReturn(List.of(mock(OutputDeclaration.class)));
        when(task.requiredRuntimeInputs()).thenReturn(List.copyOf(runtimeInputs));
        when(task.authoritativeOutputIds()).thenReturn(List.of(OUTPUT_ID));
        return task;
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
