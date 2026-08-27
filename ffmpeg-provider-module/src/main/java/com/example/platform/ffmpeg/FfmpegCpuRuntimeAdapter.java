package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeAdapter;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Deterministic argv-only adapter; materialized input resolution remains worker-owned. */
public final class FfmpegCpuRuntimeAdapter implements RuntimeAdapter<FfmpegCpuTranscodePlan> {

    private static final String INPUT_TOKEN_PREFIX = "@platform-materialized-input:";
    private final String executable;

    public FfmpegCpuRuntimeAdapter(Path executable) {
        Objects.requireNonNull(executable, "executable");
        Path normalized = executable.toAbsolutePath().normalize();
        if (!normalized.equals(executable) || !normalized.isAbsolute()) {
            throw new IllegalArgumentException("FFmpeg executable must be an absolute normalized path");
        }
        this.executable = normalized.toString();
    }

    @Override
    public RuntimeExecutionBundle adapt(
            FfmpegCpuTranscodePlan plan, RuntimeExecutionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        if (!plan.executableTaskId().equals(context.executableTaskId())
                || !plan.providerBindingPin().equals(context.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "FFmpeg runtime adapter requires the exact task and provider binding");
        }
        ProcessInvocationSpec invocation = ProcessInvocationSpec.of(executable, List.of(
                "-hide_banner",
                "-nostdin",
                "-loglevel", "error",
                "-threads", "1",
                "-fflags", "+bitexact",
                "-i", materializedInputToken(plan.inputId()),
                "-map", "0:v:0",
                "-an",
                "-map_metadata", "-1",
                "-map_chapters", "-1",
                "-c:v", "libx264",
                "-preset", "medium",
                "-x264-params", "threads=1:lookahead_threads=1:sliced_threads=0",
                "-pix_fmt", "yuv420p",
                "-flags:v", "+bitexact",
                "-movflags", "frag_keyframe+empty_moov+default_base_moof",
                "-f", "mp4",
                "pipe:1"));
        ExecutionCommand command = new ExecutionCommand(
                context.executableTaskId(),
                context.providerBindingPin(),
                context.platformExecutionAttemptId(),
                context.platformOwnershipGeneration(),
                0,
                invocation);
        return new RuntimeExecutionBundle(
                context.executableTaskId(),
                context.providerBindingPin(),
                context.platformExecutionAttemptId(),
                context.platformOwnershipGeneration(),
                List.of(command));
    }

    public static String materializedInputToken(ExecutionInputId inputId) {
        Objects.requireNonNull(inputId, "inputId");
        return INPUT_TOKEN_PREFIX + inputId.value();
    }
}
