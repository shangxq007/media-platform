package com.example.platform.ffmpeg;

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
import org.junit.jupiter.api.Test;

/**
 * Capability-aware lowering for the bounded CPU ffmpeg slice: the typed-chain
 * operations decode / composite / encode lower to their own typed plan and argv,
 * the capability-neutral transcode slice keeps working, and everything outside the
 * bounded shape fails closed.
 */
class FfmpegCapabilityAwareLoweringTest {

    private static final FfmpegCpuRenderLowerer LOWERER = new FfmpegCpuRenderLowerer();
    private static final StaticProviderExecutionContext CONTEXT =
            StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING);
    private static final RenderSampleWindow WINDOW = new RenderSampleWindow(
            MediaTime.ofMillis(1_500), MediaTime.ofMillis(3_500), FrameRate.of(30, 1));

    @Test
    void decodeLowersWithItsCapabilityAndHonoursTheAuthoredSourceWindow() {
        FfmpegCpuRenderPlan plan = LOWERER.lower(
                task("decode", List.of("video.decode"), WINDOW, 1), CONTEXT);

        assertThat(plan.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.DECODE);
        assertThat(plan.sourceWindow()).isPresent();
        assertThat(plan.sourceWindow().orElseThrow().start()).isEqualTo(MediaTime.ofMillis(1_500));
        assertThat(plan.sourceWindow().orElseThrow().duration()).isEqualTo(MediaTime.ofMillis(2_000));

        List<String> arguments = arguments(plan);
        assertThat(arguments).containsSequence("-ss", "1.5", "-t", "2");
        assertThat(arguments).contains("libx264", "yuv420p", "pipe:1");
        assertThat(arguments).containsOnlyOnce(
                FfmpegCpuRenderAdapter.materializedInputToken(plan.inputId()));
    }

    @Test
    void compositeAndEncodeLowerWithoutASourceWindow() {
        FfmpegCpuRenderPlan composite = LOWERER.lower(
                task("composite", List.of("render.composite"), null, 1), CONTEXT);
        FfmpegCpuRenderPlan encode = LOWERER.lower(
                task("encode", List.of("render.output"), null, 1), CONTEXT);

        assertThat(composite.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.COMPOSITE);
        assertThat(encode.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.ENCODE);
        assertThat(composite.sourceWindow()).isEmpty();
        assertThat(encode.sourceWindow()).isEmpty();
        assertThat(arguments(composite)).doesNotContain("-ss");
        assertThat(arguments(encode)).doesNotContain("-ss");
    }

    @Test
    void transcodeSliceKeepsLoweringWithoutAnyCapabilityRequirement() {
        FfmpegCpuRenderPlan plan = LOWERER.lower(task("transcode", List.of(), null, 1), CONTEXT);

        assertThat(plan.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.TRANSCODE);
        assertThat(arguments(plan)).containsSequence("-f", "mp4", "pipe:1");
    }

    @Test
    void everyTypedOperationEmitsTheSameCanonicalIntermediate() {
        List<String> decode = arguments(LOWERER.lower(
                task("decode", List.of("video.decode"), WINDOW, 1), CONTEXT));
        List<String> encode = arguments(LOWERER.lower(
                task("encode", List.of("render.output"), null, 1), CONTEXT));

        assertThat(decode.stream().filter("-ss"::equals).count()).isEqualTo(1);
        assertThat(encode).doesNotContain("-ss");
        assertThat(encode).contains("-c:v", "libx264", "-pix_fmt", "yuv420p");
        assertThat(encode).containsSequence("-f", "mp4", "pipe:1");
        assertThat(encode).contains("+bitexact", "frag_keyframe+empty_moov+default_base_moof");
    }

    @Test
    void loweringFailsClosedOutsideTheBoundedSlice() {
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), null, 1),
                StaticProviderExecutionContext.fromBinding(foreignBinding())),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        assertFailure(() -> LOWERER.lower(task("mux", List.of(), null, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        assertFailure(() -> LOWERER.lower(task("encode", List.of(), null, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        assertFailure(() -> LOWERER.lower(task("encode", List.of("audio.mix"), null, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), WINDOW, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        assertFailure(() -> LOWERER.lower(task("decode", List.of("video.decode"),
                        new RenderSampleWindow(MediaTime.ofRational(2, 1), MediaTime.ofRational(2, 1),
                                FrameRate.of(30, 1)), 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), null, 2), CONTEXT),
                ProviderNativeFailureCode.ILLEGAL_MULTI_MEMBERSHIP_LOWERING);
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), null, 1, 2), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY);
    }

    private static void assertFailure(ThrowingCall call, ProviderNativeFailureCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    /** A real but different binding, used to prove the exact-binding fail-closed check. */
    private static ProviderBindingPin foreignBinding() {
        return new ProviderBindingPin(
                com.example.platform.execution.domain.provider.ProviderId.of("not-ffmpeg"),
                com.example.platform.execution.domain.provider.ProviderImplementationId.of("not-ffmpeg.v1"),
                com.example.platform.execution.domain.provider.ProviderVersion.of("1.0.0"),
                com.example.platform.execution.domain.provider.ProviderExecutionContractVersion.of(1, 0),
                com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest
                        .version(com.example.platform.execution.domain.provider
                                .ProviderCapabilityProfileVersion.of(1, 0)),
                List.of());
    }

    private static List<String> arguments(FfmpegCpuRenderPlan plan) {
        RuntimeExecutionContext context = new RuntimeExecutionContext(
                plan.executableTaskId(), FfmpegCpuProvider.BINDING,
                ExecutionAttemptId.of("attempt-capability"), ExecutionOwnershipGeneration.first());
        ProcessInvocationSpec invocation =
                (ProcessInvocationSpec) new FfmpegCpuRenderAdapter(Path.of("/usr/bin/ffmpeg"))
                        .adapt(plan, context)
                        .commands().getFirst().invocationSpec();
        assertThat(invocation.executable()).isEqualTo("/usr/bin/ffmpeg");
        assertThat(invocation.arguments()).noneMatch(
                value -> value.contains(";") || value.contains("&&"));
        return invocation.arguments();
    }

    private static ExecutableTask task(
            String operation, List<String> capabilityIds, RenderSampleWindow window, int membershipCount) {
        return task(operation, capabilityIds, window, membershipCount, 1);
    }

    private static ExecutableTask task(
            String operation,
            List<String> capabilityIds,
            RenderSampleWindow window,
            int membershipCount,
            int outputCount) {
        ExecutableTask task = mock(ExecutableTask.class);
        List<ExecutableTaskMembership> memberships = java.util.stream.IntStream.range(0, membershipCount)
                .mapToObj(index -> membership(operation, capabilityIds, window))
                .toList();
        List<ExecutableInputProjection> runtimeInputs = List.of(inputProjection());
        List<ExecutionOutputId> outputs = java.util.stream.IntStream.range(0, outputCount)
                .mapToObj(index -> new ExecutionOutputId(index == 0 ? "output-media" : "output-" + index))
                .toList();
        when(task.id()).thenReturn(new ExecutableTaskId("c".repeat(64)));
        when(task.providerBindingPin()).thenReturn(FfmpegCpuProvider.BINDING);
        when(task.memberships()).thenReturn(memberships);
        when(task.requiredRuntimeInputs()).thenReturn(runtimeInputs);
        when(task.authoritativeOutputIds()).thenReturn(outputs);
        return task;
    }

    private static ExecutableTaskMembership membership(
            String operation, List<String> capabilityIds, RenderSampleWindow window) {
        ExecutableTaskMembership membership = mock(ExecutableTaskMembership.class);
        PhysicalPlanUnit unit = mock(PhysicalPlanUnit.class);
        when(unit.operationKey()).thenReturn(operation);
        when(unit.typedInputs()).thenReturn(List.of(mock(InputBinding.class)));
        when(unit.typedOutputs()).thenReturn(List.of(mock(OutputDeclaration.class)));
        when(unit.capabilityRequirementRefs()).thenReturn(capabilityIds.stream()
                .map(FfmpegCapabilityAwareLoweringTest::capabilityRef)
                .toList());
        when(unit.executionIntentRefs()).thenReturn(List.of());
        when(unit.temporalWindow()).thenReturn(window);
        when(unit.propagatedExtent()).thenReturn(null);
        when(unit.executionCoverage()).thenReturn(null);
        when(membership.physicalPlanUnit()).thenReturn(unit);
        return membership;
    }

    private static CapabilityRequirementRef capabilityRef(String capabilityId) {
        return new CapabilityRequirementRef(CapabilityRequirement.of(
                CapabilityId.of(capabilityId),
                ContractVersionRange.exactly(ContractVersion.of(1, 0))));
    }

    private static ExecutableInputProjection inputProjection() {
        ExecutableInputProjection projection = mock(ExecutableInputProjection.class);
        when(projection.inputId()).thenReturn(new ExecutionInputId("input-media"));
        return projection;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
