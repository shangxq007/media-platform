package com.example.platform.ffmpeg;

import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workerfabric.domain.providernative.PlanLowerer;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.util.Map;
import java.util.Objects;

/** Pure fail-closed lowering for the single admitted canonical CPU transcode shape. */
public final class FfmpegCpuTranscodeLowerer implements PlanLowerer<FfmpegCpuTranscodePlan> {

    @Override
    public FfmpegCpuTranscodePlan lower(
            ExecutableTask task, StaticProviderExecutionContext context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");
        if (!FfmpegCpuProvider.BINDING.equals(task.providerBindingPin())
                || !FfmpegCpuProvider.BINDING.equals(context.providerBindingPin())) {
            throw failure(ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH,
                    "FFmpeg CPU lowering requires its exact ProviderBindingPin");
        }
        if (task.memberships().size() != 1) {
            throw failure(ProviderNativeFailureCode.ILLEGAL_MULTI_MEMBERSHIP_LOWERING,
                    "FFmpeg CPU transcode requires exactly one PhysicalPlanUnit membership");
        }
        ExecutableTaskMembership membership = task.memberships().getFirst();
        var unit = membership.physicalPlanUnit();
        if (!"transcode".equals(unit.operationKey())) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                    "FFmpeg CPU provider supports only canonical operationKey transcode");
        }
        if (!unit.capabilityRequirementRefs().isEmpty()
                || !unit.executionIntentRefs().isEmpty()
                || unit.temporalWindow() != null
                || unit.propagatedExtent() != null
                || unit.executionCoverage() != null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "parameterized transcode semantics are outside the bounded slice");
        }
        if (unit.typedInputs().size() != 1
                || task.requiredRuntimeInputs().size() != 1) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "FFmpeg CPU transcode requires one exact materialized runtime input");
        }
        if (unit.typedOutputs().size() != 1
                || task.authoritativeOutputIds().size() != 1) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY,
                    "FFmpeg CPU transcode requires one authoritative output",
                    Map.of("authoritativeOutputCount",
                            Integer.toString(task.authoritativeOutputIds().size())));
        }
        return new FfmpegCpuTranscodePlan(
                task.id(),
                task.providerBindingPin(),
                task.requiredRuntimeInputs().getFirst().inputId(),
                task.authoritativeOutputIds().getFirst(),
                FfmpegCpuTranscodePlan.VideoCodec.H264_LIBX264,
                FfmpegCpuTranscodePlan.PixelFormat.YUV420P,
                FfmpegCpuTranscodePlan.ContainerFormat.FRAGMENTED_MP4_STDOUT);
    }

    private static ProviderNativeExecutionFailure failure(
            ProviderNativeFailureCode code, String message) {
        return new ProviderNativeExecutionFailure(code, message);
    }
}
