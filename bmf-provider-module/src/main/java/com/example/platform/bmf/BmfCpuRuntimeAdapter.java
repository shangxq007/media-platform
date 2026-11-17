package com.example.platform.bmf;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeOutputFiles;
import com.example.platform.workerfabric.domain.providernative.RuntimeAdapter;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Invokes the versioned BMF Graph program through the worker's typed process boundary.
 *
 * <p>The program is a decode → optional authored source trim → encode pipeline. It is kept with
 * the provider source so a deployed Worker runs exactly the graph verified by this build. The
 * Python runtime and BMF libraries are separately pinned worker dependencies.
 */
public final class BmfCpuRuntimeAdapter implements RuntimeAdapter<BmfCpuTranscodePlan> {
    private static final String INPUT_PREFIX = "@platform-materialized-input:";

    static final String GRAPH_PROGRAM = """
            import importlib
            import os
            import sys
            import bmf
            ff = importlib.import_module('bmf.builder.ff_filter')
            source, output, operation, start, duration = sys.argv[1:6]
            print('BMF_GRAPH_START ' + operation, flush=True)
            video = bmf.graph().decode({'input_path': source})['video']
            if float(duration) > 0:
                video = ff.setpts(ff.trim(video, start=start, duration=duration), 'PTS-STARTPTS')
            video.encode(None, {'output_path': output, 'format': 'mp4',
                                'video_params': {'codec': 'libx264', 'pix_fmt': 'yuv420p'}}).run()
            if not os.path.isfile(output) or os.path.getsize(output) == 0:
                raise RuntimeError('BMF graph produced no media output')
            print('BMF_GRAPH_DONE ' + operation + ' size=' + str(os.path.getsize(output)), flush=True)
            """;

    /**
     * Multi-input composite program: every input is decoded, reset to PTS 0, normalized to the
     * bounded output resolution/frame rate and concatenated. The lowerer guarantees contiguous,
     * fully covering inputs, so concatenation preserves every timeline position; a gap is rejected
     * before this program is ever produced.
     */
    static final String COMPOSITE_GRAPH_PROGRAM = """
            import importlib
            import os
            import sys
            import bmf
            ff = importlib.import_module('bmf.builder.ff_filter')
            count = int(sys.argv[1])
            inputs = sys.argv[2:2 + count]
            output = sys.argv[2 + count]
            duration = sys.argv[3 + count]
            rate = sys.argv[4 + count]
            print('BMF_GRAPH_START composite inputs=' + str(count), flush=True)
            graph = bmf.graph()
            clips = []
            for path in inputs:
                video = graph.decode({'input_path': path})['video']
                video = ff.setpts(video, 'PTS-STARTPTS')
                video = ff.scale(video, '320x240')
                video = ff.fps(video, rate)
                # BMF drops the final frame of every decoded/normalized segment; holding exactly one
                # frame restores the authored frame count without duplicating visible content.
                video = ff.ff_filter([video], 'tpad', stop='1', stop_mode='clone')
                clips.append(video)
            composed = clips[0] if len(clips) == 1 else ff.concat(*clips, n=len(clips), v=1, a=0)
            composed.encode(None, {'output_path': output, 'format': 'mp4',
                                   'video_params': {'codec': 'libx264', 'pix_fmt': 'yuv420p'}}).run()
            if not os.path.isfile(output) or os.path.getsize(output) == 0:
                raise RuntimeError('BMF composite graph produced no media output')
            print('BMF_GRAPH_DONE composite expected=' + duration + ' size=' + str(os.path.getsize(output)), flush=True)
            """;

    private final Path python;

    public BmfCpuRuntimeAdapter(Path python) {
        Objects.requireNonNull(python, "python");
        if (!python.isAbsolute() || !python.equals(python.normalize())) {
            throw new IllegalArgumentException("BMF Python must be an absolute normalized path");
        }
        this.python = python;
    }

    @Override
    public RuntimeExecutionBundle adapt(BmfCpuTranscodePlan plan, RuntimeExecutionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        if (!plan.executableTaskId().equals(context.executableTaskId())
                || !plan.providerBindingPin().equals(context.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "BMF runtime requires the exact task and provider binding");
        }
        var window = plan.sourceWindow();
        var invocation = plan.compositeInputs().isEmpty()
                ? ProcessInvocationSpec.of(python.toString(), List.of(
                        "-s", "-c", GRAPH_PROGRAM, materializedInputToken(plan.inputId()),
                        ProviderNativeOutputFiles.RENDER_OUTPUT,
                        plan.operation().operationKey(),
                        window.map(value -> seconds(value.start())).orElse("0"),
                        window.map(value -> seconds(value.duration())).orElse("0")))
                : compositeInvocation(plan);
        var command = new ExecutionCommand(context.executableTaskId(), context.providerBindingPin(),
                context.platformExecutionAttemptId(), context.platformOwnershipGeneration(), 0, invocation);
        return new RuntimeExecutionBundle(context.executableTaskId(), context.providerBindingPin(),
                context.platformExecutionAttemptId(), context.platformOwnershipGeneration(), List.of(command));
    }

    private ProcessInvocationSpec compositeInvocation(BmfCpuTranscodePlan plan) {
        var arguments = new java.util.ArrayList<String>();
        arguments.add("-s");
        arguments.add("-c");
        arguments.add(COMPOSITE_GRAPH_PROGRAM);
        arguments.add(Integer.toString(plan.compositeInputs().size()));
        for (var input : plan.compositeInputs()) {
            arguments.add(materializedInputToken(input.inputId()));
        }
        arguments.add(ProviderNativeOutputFiles.RENDER_OUTPUT);
        arguments.add(seconds(plan.compositeDuration().orElseThrow()));
        var rate = plan.compositeFrameRate().orElseThrow();
        arguments.add(rate.numerator() + "/" + rate.denominator());
        return ProcessInvocationSpec.of(python.toString(), List.copyOf(arguments));
    }

    public static String materializedInputToken(ExecutionInputId inputId) {
        return INPUT_PREFIX + Objects.requireNonNull(inputId, "inputId").value();
    }

    private static String seconds(MediaTime time) {
        Objects.requireNonNull(time, "time");
        return BigDecimal.valueOf(time.ticks())
                .divide(BigDecimal.valueOf(time.timeScale()), 9, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
