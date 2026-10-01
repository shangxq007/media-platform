package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeAdapter;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic argv-only adapter for the bounded CPU ffmpeg slice.
 *
 * <p>All operations emit the same canonical single-stream normalization
 * (H.264 / yuv420p, bitexact, single-threaded, fragmented mp4 on stdout) so the
 * downstream artifact stage is identical regardless of the typed stage that
 * produced it; {@code DECODE} additionally applies the exact authored source
 * sample window with input-side seeking. No shell interpretation, no media bytes,
 * no credentials — materialized input resolution stays worker-owned.</p>
 */
public final class FfmpegCpuRenderAdapter implements RuntimeAdapter<FfmpegCpuRenderPlan> {

    private static final String MATERIALIZED_INPUT_PREFIX = "@platform-materialized-input:";
    private static final int SECONDS_SCALE = 9;

    private final String executable;

    public FfmpegCpuRenderAdapter(Path executable) {
        Objects.requireNonNull(executable, "executable");
        Path normalized = executable.toAbsolutePath().normalize();
        if (!normalized.equals(executable) || !normalized.isAbsolute()) {
            throw new IllegalArgumentException("FFmpeg executable must be an absolute normalized path");
        }
        this.executable = normalized.toString();
    }

    @Override
    public RuntimeExecutionBundle adapt(
            FfmpegCpuRenderPlan plan, RuntimeExecutionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        if (!plan.executableTaskId().equals(context.executableTaskId())
                || !plan.providerBindingPin().equals(context.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "FFmpeg runtime adapter requires the exact task and provider binding");
        }
        ProcessInvocationSpec invocation =
                ProcessInvocationSpec.of(executable, arguments(plan));
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

    private static List<String> arguments(FfmpegCpuRenderPlan plan) {
        List<String> arguments = new ArrayList<>(32);
        arguments.add("-hide_banner");
        arguments.add("-nostdin");
        arguments.add("-loglevel");
        arguments.add("error");
        arguments.add("-threads");
        arguments.add("1");
        arguments.add("-fflags");
        arguments.add("+bitexact");
        plan.sourceWindow().ifPresent(window -> {
            arguments.add("-ss");
            arguments.add(seconds(window.start()));
            arguments.add("-t");
            arguments.add(seconds(window.duration()));
        });
        arguments.add("-i");
        arguments.add(materializedInputToken(plan.inputId()));
        arguments.add("-map");
        arguments.add("0:v:0");
        arguments.add("-an");
        arguments.add("-map_metadata");
        arguments.add("-1");
        arguments.add("-map_chapters");
        arguments.add("-1");
        arguments.add("-c:v");
        arguments.add("libx264");
        arguments.add("-preset");
        arguments.add("medium");
        arguments.add("-x264-params");
        arguments.add("threads=1:lookahead_threads=1:sliced_threads=0");
        arguments.add("-pix_fmt");
        arguments.add("yuv420p");
        arguments.add("-flags:v");
        arguments.add("+bitexact");
        arguments.add("-movflags");
        arguments.add("frag_keyframe+empty_moov+default_base_moof");
        arguments.add("-f");
        arguments.add("mp4");
        arguments.add("pipe:1");
        return arguments;
    }

    /** Exact rational seconds, deterministic plain-decimal rendering. */
    private static String seconds(MediaTime time) {
        return BigDecimal.valueOf(time.ticks())
                .divide(BigDecimal.valueOf(time.timeScale()), SECONDS_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** The exact token the sandbox policy resolver substitutes for a materialized input. */
    public static String materializedInputToken(ExecutionInputId inputId) {
        Objects.requireNonNull(inputId, "inputId");
        return MATERIALIZED_INPUT_PREFIX + inputId.value();
    }
}
