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
            streams = bmf.graph().decode({'input_path': source})
            audio_params = {'codec': 'aac', 'sample_rate': 48000, 'channels': 2}
            options = {'output_path': output, 'format': 'mp4',
                       'video_params': {'codec': 'libx264', 'pix_fmt': 'yuv420p'},
                       'audio_params': audio_params}
            if operation == 'audio-decode':
                audio = ff.atrim(streams['audio'], start=start, duration=duration)
                audio = ff.asetpts(audio, 'PTS-STARTPTS')
                audio = ff.ff_filter(audio, 'aresample', '48000:ocl=stereo:clev=1')
                ff.encode(None, audio, options).run()
            else:
                video = streams['video']
                if float(duration) > 0:
                    video = ff.setpts(ff.trim(video, start=start, duration=duration), 'PTS-STARTPTS')
                audio = streams['audio'] if operation == 'encode-multitrack' else None
                video.encode(audio, options).run()
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

    /** Builds a native BMF graph from typed, extent-relative placements. */
    static final String MULTITRACK_GRAPH_PROGRAM = """
            from fractions import Fraction
            import importlib
            import os
            import sys
            import bmf

            ff = importlib.import_module('bmf.builder.ff_filter')
            count = int(sys.argv[1])
            paths = sys.argv[2:2 + count]
            output, duration, rate = sys.argv[2 + count:5 + count]
            placements = [value.split(',') for value in sys.argv[5 + count:]]
            graph = bmf.graph()
            print('BMF_GRAPH_START multitrack-composite inputs=' + str(count), flush=True)

            def transformed(video, placement):
                # kind,start,end,gain,mute,pan,posx,posy,scalex,scaley,rot,cropl,cropr,cropt,cropb,opacity
                px, py = float(placement[6]), float(placement[7])
                sx, sy = float(placement[8]), float(placement[9])
                rot = float(placement[10])
                cl, cr = float(placement[11]), float(placement[12])
                ct, cb = float(placement[13]), float(placement[14])
                opacity = float(placement[15])
                # Canonical order: crop -> scale -> rotate -> position -> opacity.
                video = ff.ff_filter(video, 'crop', 'w=iw*' + str(cr - cl)
                                     + ':h=ih*' + str(cb - ct) + ':x=iw*' + str(cl) + ':y=ih*' + str(ct))
                width = max(2, int(round(320 * sx)))
                height = max(2, int(round(240 * sy)))
                video = ff.ff_filter(video, 'scale', str(width) + 'x' + str(height))
                video = ff.ff_filter(video, 'format', 'rgba')
                if abs(rot) > 0.000001:
                    angle = str(rot) + '*PI/180'
                    video = ff.ff_filter(video, 'rotate', 'angle=' + angle
                                         + ':ow=rotw(' + angle + '):oh=roth(' + angle
                                         + '):fillcolor=none')
                if opacity < 0.999999:
                    video = ff.ff_filter(video, 'colorchannelmixer', 'aa=' + str(opacity))
                return video, px, py

            video_inputs = [(path, placement) for path, placement in zip(paths, placements)
                            if placement[0] == 'V']
            if video_inputs:
                base = graph.decode({'input_path': video_inputs[0][0]})['video']
                canvas = ff.ff_filter(ff.scale(base, '320x240'), 'fps', rate)
            else:
                waveform = graph.decode({'input_path': paths[0]})['audio']
                canvas = ff.ff_filter(waveform, 'showwaves', 's=320x240:r=' + rate)
            canvas = ff.ff_filter(canvas, 'drawbox', color='black', t='fill')
            canvas = ff.ff_filter(canvas, 'tpad', stop_duration=duration, stop_mode='clone')
            canvas = ff.trim(canvas, duration=duration)
            for path, placement in video_inputs:
                start, end = placement[1], placement[2]
                clip = graph.decode({'input_path': path})['video']
                clip = ff.ff_filter(clip, 'fps', rate)
                clip = ff.ff_filter(clip, 'tpad', stop='1', stop_mode='clone')
                clip = ff.setpts(clip, 'PTS-STARTPTS+' + start + '/TB')
                clip, px, py = transformed(clip, placement)
                visible = '(1+sgn(t-' + start + '+0.000000001))*(1-sgn(t-' + end + '+0.000000001))'
                canvas = ff.overlay(canvas, clip, x=str(px), y=str(py), enable=visible)

            audio_inputs = []
            for path, placement in zip(paths, placements):
                if placement[0] != 'A':
                    continue
                start, end = placement[1], placement[2]
                gain, mute, pan = placement[3], placement[4], placement[5]
                audio = graph.decode({'input_path': path})['audio']
                audio = ff.atrim(audio, duration=str(float(Fraction(end) - Fraction(start))))
                audio = ff.asetpts(audio, 'PTS-STARTPTS')
                delay = str(round(Fraction(start) * 48000)) + 'S'
                audio = ff.adelay(audio, delays=delay, all=1)
                level = 0.0 if mute == '1' else float(gain)
                audio = ff.ff_filter(audio, 'aformat', channel_layouts='stereo')
                audio = ff.ff_filter(audio, 'volume', str(level))
                left = (1.0 - float(pan)) / 2.0
                right = (1.0 + float(pan)) / 2.0
                audio = ff.ff_filter(audio, 'pan', 'stereo|c0=' + str(left) + '*c0|c1=' + str(right) + '*c1')
                audio_inputs.append(ff.atrim(ff.ff_filter(audio, 'apad'), duration=duration))

            mixed = None
            if audio_inputs:
                mixed = (ff.amix(audio_inputs, inputs=len(audio_inputs), duration='first', dropout_transition=0)
                         if len(audio_inputs) > 1 else audio_inputs[0])
                mixed = ff.ff_filter(mixed, 'volume', str(len(audio_inputs) / 2))
                mixed = ff.ff_filter(mixed, 'aformat', sample_fmts='s16')
            canvas.encode(mixed, {'output_path': output, 'format': 'mp4',
                                  'video_params': {'codec': 'libx264', 'pix_fmt': 'yuv420p'},
                                  'audio_params': {'codec': 'aac', 'sample_rate': 48000, 'channels': 2}}).run()
            if not os.path.isfile(output) or os.path.getsize(output) == 0:
                raise RuntimeError('BMF multitrack graph produced no media output')
            print('BMF_GRAPH_DONE multitrack-composite size=' + str(os.path.getsize(output)), flush=True)
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
        boolean multitrack = plan.operation() == BmfOperation.MULTITRACK_COMPOSITE;
        arguments.add(multitrack ? MULTITRACK_GRAPH_PROGRAM : COMPOSITE_GRAPH_PROGRAM);
        arguments.add(Integer.toString(plan.compositeInputs().size()));
        for (var input : plan.compositeInputs()) {
            arguments.add(materializedInputToken(input.inputId()));
        }
        arguments.add(ProviderNativeOutputFiles.RENDER_OUTPUT);
        arguments.add(seconds(plan.compositeDuration().orElseThrow()));
        var rate = plan.compositeFrameRate().orElseThrow();
        arguments.add(rate.numerator() + "/" + rate.denominator());
        if (multitrack) {
            for (var input : plan.compositeInputs()) {
                var t = input.transform();
                arguments.add((input.audio() ? "A" : "V") + "," + seconds(input.start()) + ","
                        + seconds(input.end()) + "," + input.gain() + ","
                        + (input.mute() ? "1" : "0") + "," + input.balance() + ","
                        + t.positionX() + "," + t.positionY() + "," + t.scaleX() + ","
                        + t.scaleY() + "," + t.rotationDegrees() + "," + t.cropLeft() + ","
                        + t.cropRight() + "," + t.cropTop() + "," + t.cropBottom() + ","
                        + t.opacity());
            }
        }
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
