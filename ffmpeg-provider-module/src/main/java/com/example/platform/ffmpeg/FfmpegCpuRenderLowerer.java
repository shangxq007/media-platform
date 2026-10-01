package com.example.platform.ffmpeg;

import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.workerfabric.domain.providernative.PlanLowerer;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure fail-closed capability-aware lowering for the bounded CPU ffmpeg slice.
 *
 * <p>Accepts the typed-chain node operations this provider genuinely serves
 * ({@code decode} / {@code composite} / {@code encode}) plus the capability-neutral
 * {@code transcode} slice, and rejects everything outside the bounded shape. A typed
 * operation must carry its own capability requirement, and every declared capability
 * requirement must be one this provider actually declares.</p>
 */
public final class FfmpegCpuRenderLowerer implements PlanLowerer<FfmpegCpuRenderPlan> {

    private static final Set<String> DECLARED_CAPABILITIES = Set.of(
            FfmpegCpuProvider.TRANSCODE_CAPABILITY.value(),
            FfmpegCpuProvider.VIDEO_DECODE_CAPABILITY.value(),
            FfmpegCpuProvider.COMPOSITE_CAPABILITY.value(),
            FfmpegCpuProvider.OUTPUT_CAPABILITY.value());

    @Override
    public FfmpegCpuRenderPlan lower(
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
                    "FFmpeg CPU lowering requires exactly one PhysicalPlanUnit membership");
        }
        ExecutableTaskMembership membership = task.memberships().getFirst();
        var unit = membership.physicalPlanUnit();
        FfmpegCpuRenderPlan.Operation operation = operationOf(unit.operationKey());
        requireDeclaredCapabilities(unit.capabilityRequirementRefs(), operation);
        if (unit.typedInputs().size() != 1 || task.requiredRuntimeInputs().size() != 1) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "FFmpeg CPU lowering requires one exact materialized runtime input");
        }
        if (unit.typedOutputs().size() != 1 || task.authoritativeOutputIds().size() != 1) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY,
                    "FFmpeg CPU lowering requires one authoritative output",
                    Map.of("authoritativeOutputCount",
                            Integer.toString(task.authoritativeOutputIds().size())));
        }
        Optional<FfmpegCpuRenderPlan.SourceWindow> window = sourceWindow(unit.temporalWindow(), operation);
        return new FfmpegCpuRenderPlan(
                task.id(),
                task.providerBindingPin(),
                operation,
                task.requiredRuntimeInputs().getFirst().inputId(),
                task.authoritativeOutputIds().getFirst(),
                window);
    }

    private static FfmpegCpuRenderPlan.Operation operationOf(String operationKey) {
        return switch (operationKey) {
            case "transcode" -> FfmpegCpuRenderPlan.Operation.TRANSCODE;
            case "decode" -> FfmpegCpuRenderPlan.Operation.DECODE;
            case "composite" -> FfmpegCpuRenderPlan.Operation.COMPOSITE;
            case "encode" -> FfmpegCpuRenderPlan.Operation.ENCODE;
            default -> throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                    "FFmpeg CPU provider does not lower operation: " + operationKey);
        };
    }

    private static void requireDeclaredCapabilities(
            java.util.List<CapabilityRequirementRef> references,
            FfmpegCpuRenderPlan.Operation operation) {
        for (CapabilityRequirementRef reference : references) {
            String capabilityId = reference.declaration().capabilityId().value();
            if (!DECLARED_CAPABILITIES.contains(capabilityId)) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                        "FFmpeg CPU provider does not declare capability: " + capabilityId);
            }
        }
        String required = switch (operation) {
            case DECODE -> FfmpegCpuProvider.VIDEO_DECODE_CAPABILITY.value();
            case COMPOSITE -> FfmpegCpuProvider.COMPOSITE_CAPABILITY.value();
            case ENCODE -> FfmpegCpuProvider.OUTPUT_CAPABILITY.value();
            case TRANSCODE -> null;
        };
        if (required != null && references.stream()
                .noneMatch(reference -> required.equals(
                        reference.declaration().capabilityId().value()))) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "FFmpeg CPU " + operation + " lowering requires its own capability requirement");
        }
    }

    private static Optional<FfmpegCpuRenderPlan.SourceWindow> sourceWindow(
            RenderSampleWindow window, FfmpegCpuRenderPlan.Operation operation) {
        if (window == null) {
            return Optional.empty();
        }
        if (operation != FfmpegCpuRenderPlan.Operation.DECODE) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "only the decode operation carries an authored source sample window");
        }
        if (window.isFreezePoint()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "bounded V1 decode does not serve zero-length freeze windows");
        }
        return Optional.of(new FfmpegCpuRenderPlan.SourceWindow(window.start(), window.end()));
    }

    private static ProviderNativeExecutionFailure failure(
            ProviderNativeFailureCode code, String message) {
        return new ProviderNativeExecutionFailure(code, message);
    }
}
