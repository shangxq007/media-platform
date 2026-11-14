package com.example.platform.ffmpeg;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.shared.time.FrameRate;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(FfmpegCpuRenderAdapter.class);

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
        plan.frameParameters().ifPresent(parameters -> {
            arguments.add("-ss");
            arguments.add(seconds(parameters.position()));
        });
        if (plan.operation() == FfmpegCpuRenderPlan.Operation.RASTER) {
            return rasterArguments(arguments, plan);
        }
        if (plan.compositeInputs().isEmpty()) {
            arguments.add("-i");
            arguments.add(materializedInputToken(plan.inputId()));
            arguments.add("-map");
            arguments.add("0:v:0");
        } else {
            for (var input : plan.compositeInputs()) {
                arguments.add("-i");
                arguments.add(materializedInputToken(input.inputId()));
            }
            MediaTime duration = plan.compositeDuration().orElseThrow();
            var canvasRate = plan.outputFrameRate().orElse(FrameRate.of(30, 1));
            StringBuilder filter = new StringBuilder("color=c=black:s=320x240:r=")
                    .append(canvasRate.numerator()).append('/').append(canvasRate.denominator())
                    .append(":d=").append(seconds(duration)).append("[canvas];");
            for (int i = 0; i < plan.compositeInputs().size(); i++) {
                if (i > 0) {
                    filter.append(';');
                }
                var input = plan.compositeInputs().get(i);
                filter.append('[').append(i).append(":v]setpts=PTS-STARTPTS");
                if (input.start().ticks() != 0) {
                    filter.append('+').append(seconds(input.start())).append("/TB");
                }
                filter.append("[clip").append(i).append(']');
            }
            filter.append(";[canvas][clip0]overlay=format=auto:enable='gte(t,")
                    .append(seconds(plan.compositeInputs().getFirst().start()))
                    .append(")*lt(t,").append(seconds(plan.compositeInputs().getFirst().end()))
                    .append(")'[base0]");
            for (int i = 1; i < plan.compositeInputs().size(); i++) {
                var input = plan.compositeInputs().get(i);
                filter.append(';').append("[base").append(i - 1).append("][clip").append(i)
                        .append("]overlay=format=auto:enable='gte(t,")
                        .append(seconds(input.start())).append(")*lt(t,")
                        .append(seconds(input.end())).append(")'[base").append(i).append(']');
            }
                arguments.addAll(List.of("-filter_complex", filter.toString(), "-map",
                    "[base" + (plan.compositeInputs().size() - 1) + "]", "-t", seconds(duration)));
        }
        if ((plan.operation() == FfmpegCpuRenderPlan.Operation.COMPOSITE
                        || plan.operation() == FfmpegCpuRenderPlan.Operation.ENCODE)
                && plan.outputFrameRate().isPresent()) {
            var rate = plan.outputFrameRate().orElseThrow();
            arguments.add("-r");
            arguments.add(rate.numerator() + "/" + rate.denominator());
        }
        arguments.add("-an");
        arguments.add("-map_metadata");
        arguments.add("-1");
        arguments.add("-map_chapters");
        arguments.add("-1");
        if (plan.frameParameters().isPresent()) {
            var parameters=plan.frameParameters().orElseThrow();
            arguments.addAll(List.of("-frames:v","1"));
            if (parameters.width() instanceof com.example.platform.shared.capability.FrameWidth.ExplicitPixels width) {
                arguments.addAll(List.of("-vf","scale="+width.pixels()+":-1"));
            }
            if (parameters.encoding() instanceof com.example.platform.shared.capability.RasterImageEncoding.Jpeg jpeg) {
                int quality=jpeg.quality().orElseThrow().value();
                int qscale=Math.max(1,Math.min(31,(int)Math.round(31.0-quality*0.3)));
                arguments.addAll(List.of("-c:v","mjpeg","-pix_fmt","yuvj420p","-q:v",Integer.toString(qscale)));
            } else {
                arguments.addAll(List.of("-c:v","png"));
            }
            arguments.addAll(List.of("-flags:v","+bitexact","-f","image2pipe","pipe:1"));
            return arguments;
        }
        arguments.add("-c:v");
        arguments.add("libx264");
        arguments.add("-preset");
        arguments.add("medium");
        arguments.add("-x264-params");
        arguments.add("threads=1:lookahead_threads=1:sliced_threads=0:bframes=0");
        arguments.add("-pix_fmt");
        arguments.add("yuv420p");
        arguments.add("-flags:v");
        arguments.add("+bitexact");
        arguments.add("-movflags");
        arguments.add("frag_keyframe+empty_moov+default_base_moof");
        arguments.add("-f");
        arguments.add("mp4");
        // File-based output: a large render must not be buffered in memory or truncated by the
        // sandbox stdout capture bound. The executor reads this file back as a stream.
        arguments.add(com.example.platform.workerfabric.domain.providernative
                .ProviderNativeOutputFiles.RENDER_OUTPUT);
        LOG.info("FFMPEG_ARGV task={} operation={} argv={}",
                plan.executableTaskId().sha256Hex(), plan.operation(), arguments);
        return arguments;
    }

    /**
     * Rasterize authored timed text into a transparent-background stream. The stream carries its
     * own visibility interval in the drawtext {@code enable} expression, so the composite overlay
     * can span the whole extent and the alpha channel gates the visible interval.
     */
    private static List<String> rasterArguments(List<String> arguments, FfmpegCpuRenderPlan plan) {
        var raster = plan.rasterParameters().orElseThrow();
        var rate = plan.outputFrameRate().orElse(FrameRate.of(30, 1));
        arguments.add("-f");
        arguments.add("lavfi");
        arguments.add("-i");
        arguments.add("color=c=0x00000000:s=320x240:r="
                + rate.numerator() + "/" + rate.denominator() + ",format=rgba");
        arguments.add("-vf");
        arguments.add("drawtext=fontfile=" + escapeDrawtextPath(raster.fontFile())
                + ":text='" + escapeDrawtextText(raster.text()) + "'"
                + ":fontcolor=white:fontsize=" + raster.fontSize()
                + ":x=" + raster.x() + ":y=" + raster.y()
                + ":enable='gte(t," + seconds(raster.start()) + ")*lt(t,"
                + seconds(raster.end()) + ")'");
        arguments.add("-c:v");
        arguments.add("qtrle");
        arguments.add("-t");
        arguments.add(seconds(raster.duration()));
        arguments.add("-pix_fmt");
        arguments.add("argb");
        arguments.add("-f");
        arguments.add("mov");
        arguments.add(com.example.platform.workerfabric.domain.providernative
                .ProviderNativeOutputFiles.RENDER_OUTPUT);
        LOG.info("FFMPEG_RASTER_ARGV task={} argv={}",
                plan.executableTaskId().sha256Hex(), arguments);
        return arguments;
    }

    /** Escape a filesystem path for an FFmpeg filter option value. */
    private static String escapeDrawtextPath(String path) {
        return path.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'");
    }

    /** Escape authored text for FFmpeg drawtext (single-quoted filter option). */
    private static String escapeDrawtextText(String text) {
        return text.replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("%", "\\%")
                .replace("\n", " ")
                .replace("\r", " ");
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
