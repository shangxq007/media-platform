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
            source, output, operation, start, duration, raster = sys.argv[1:7]
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
                if operation in ('encode', 'encode-multitrack'):
                    video = ff.scale(video, raster)
                    video = ff.ff_filter(video, 'setsar', '1')
                    if len(sys.argv) == 9:
                        rate, extent = sys.argv[7:9]
                        video = ff.ff_filter(video, 'tpad', stop_duration=extent, stop_mode='clone')
                        video = ff.fps(video, rate)
                        video = ff.trim(video, duration=extent)
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
            rate, raster = sys.argv[4 + count:6 + count]
            print('BMF_GRAPH_START composite inputs=' + str(count), flush=True)
            graph = bmf.graph()
            clips = []
            for path in inputs:
                video = graph.decode({'input_path': path})['video']
                video = ff.setpts(video, 'PTS-STARTPTS')
                video = ff.scale(video, raster)
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
            output, duration, rate, raster = sys.argv[2 + count:6 + count]
            canvas_width, canvas_height = map(int, raster.split('x'))
            placements = [value.split(',') for value in sys.argv[6 + count:6 + 2 * count]]
            subtitles = [value.split(',') for value in sys.argv[6 + 2 * count:]]
            graph = bmf.graph()
            print('BMF_GRAPH_START multitrack-composite inputs=' + str(count), flush=True)

            def transformed(video, placement, fx):
                # kind,start,end,gain,mute,pan,posx,posy,scalex,scaley,rot,cropl,cropr,cropt,cropb,opacity,fx
                px = round(float(placement[6]) * canvas_width)
                py = round(float(placement[7]) * canvas_height)
                sx, sy = float(placement[8]), float(placement[9])
                rot = float(placement[10])
                cl, cr = float(placement[11]), float(placement[12])
                ct, cb = float(placement[13]), float(placement[14])
                opacity = float(placement[15])
                brightness, contrast, saturation = float(fx[0]), float(fx[1]), float(fx[2])
                grayscale = fx[3] == '1'
                fade_in, fade_out = float(fx[4]), float(fx[5])
                effect_enabled = fx[6] == '1'
                authored_duration, dissolve_duration = float(fx[8]), float(fx[9])
                video = ff.ff_filter(video, 'format', 'rgba')
                if effect_enabled:
                    if brightness != 0 or contrast != 1:
                        value = '(val*' + str(1 + brightness) + '-127.5)*' + str(contrast) + '+127.5'
                        video = ff.ff_filter(video, 'lutrgb',
                                             'r=' + value + ':g=' + value + ':b=' + value)
                    saturation = 0 if grayscale else saturation
                    if saturation != 1:
                        weights = (0.299, 0.587, 0.114)
                        coefficients = []
                        for output_channel, out_name in enumerate('rgb'):
                            for input_channel, in_name in enumerate('rgb'):
                                coefficient = (1 - saturation) * weights[input_channel]
                                if input_channel == output_channel:
                                    coefficient += saturation
                                coefficients.append(out_name + in_name + '=' + str(coefficient))
                        video = ff.ff_filter(video, 'colorchannelmixer', ':'.join(coefficients))
                video = ff.ff_filter(video, 'crop', 'w=max(1,round(iw*' + str(cr - cl)
                                     + ')):h=max(1,round(ih*' + str(cb - ct) + ')):x=round(iw*' + str(cl)
                                     + '):y=round(ih*' + str(ct) + '):exact=1')
                width = max(1, int(round(canvas_width * sx)))
                height = max(1, int(round(canvas_height * sy)))
                video = ff.ff_filter(video, 'scale', str(width) + 'x' + str(height))
                video = ff.ff_filter(video, 'setsar', '1')
                if rot != 0:
                    angle = str(rot) + '*PI/180'
                    video = ff.ff_filter(video, 'rotate', 'angle=' + angle
                                         + ':ow=rotw(' + angle + '):oh=roth(' + angle
                                         + '):fillcolor=none')
                if len(fx) == 14:
                    t0, a0, span, a1 = fx[10:]
                    ramp = a0 + '+(' + a1 + '-' + a0 + ')*clip((T-(' + t0 + '))/(' + span + '),0,1)'
                    video = ff.ff_filter(video, 'geq', r='r(X,Y)', g='g(X,Y)', b='b(X,Y)',
                                         a='alpha(X,Y)*(' + ramp + ')', interpolation='nearest')
                elif opacity < 1:
                    video = ff.ff_filter(video, 'colorchannelmixer', 'aa=' + str(opacity))
                if effect_enabled and fade_in > 0:
                    video = ff.ff_filter(video, 'fade', 't=in:st=0'
                                         + ':d=' + str(fade_in) + ':alpha=1')
                if effect_enabled and fade_out > 0:
                    video = ff.ff_filter(video, 'fade', 't=out:st=' + str(authored_duration - fade_out)
                                         + ':d=' + str(fade_out) + ':alpha=1')
                if dissolve_duration > 0:
                    video = ff.ff_filter(video, 'fade', 't=in:st=0'
                                         + ':d=' + str(dissolve_duration) + ':alpha=1')
                return video, px, py

            def motion_expression(samples, axis, start, elapsed, clock):
                points = [sample.split(';') for sample in samples.split('|')]
                local = '(' + clock + '-(' + start + ')+(' + elapsed + '))'
                expression = points[0][axis]
                for left, right in zip(points, points[1:]):
                    delta = str(int(right[axis]) - int(left[axis]))
                    span = str(Fraction(right[0]) - Fraction(left[0]))
                    expression += '+(' + delta + ')*clip((' + local + '-(' + left[0] + '))/(' + span + '),0,1)'
                return expression

            def position_layer(layer, samples, start, elapsed):
                points = [sample.split(';') for sample in samples.split('|')]
                moving = any(point[1:3] != points[0][1:3] for point in points[1:])
                if not moving:
                    return (layer, (int(points[0][1]) * canvas_width + 500000) // 1000000,
                            (int(points[0][2]) * canvas_height + 500000) // 1000000)

                def coordinate(axis, pixels, clock):
                    # Both filters see the same output frame lattice. Keep the provider's
                    # placement precision at 1/256 output pixel and snap exact pixel holds.
                    grid = '(floor(' + clock + '*(' + rate + ')+0.5)/(' + rate + '))'
                    value = motion_expression(samples, axis, start, elapsed, grid)
                    return 'floor((' + value + ')*' + str(pixels) + '*256/1000000+0.5)/256'

                x = coordinate(1, canvas_width, 't')
                y = coordinate(2, canvas_height, 't')
                fx = 'mod(' + coordinate(1, canvas_width, 'T') + ',1)'
                fy = 'mod(' + coordinate(2, canvas_height, 'T') + ',1)'
                shifted = 'gt(mod(' + x + ',1)+mod(' + y + ',1),0)'

                # FFmpeg overlay truncates coordinates even in RGB. Split out the integer
                # portion and sample only the subject at the complementary fractional
                # phase. The one-pixel transparent halo preserves edge contribution
                # and off-canvas clipping without increasing the output canvas.
                layer = ff.ff_filter(layer, 'format', 'gbrap')
                layer = ff.ff_filter(layer, 'pad', 'w=iw+2:h=ih+2:x=1:y=1:color=black@0')
                layer = ff.ff_filter(layer, 'premultiply', inplace=1, enable=shifted)
                # geq evaluates each row from X=0, including when rows are slice-threaded.
                # Cache the phase in each expression's registers once per row, not per pixel.
                phase = 'if(eq(X,0),st(0,' + fx + ')+st(1,' + fy + '),0);'
                sample_at = '(X-ld(0),Y-ld(1))'
                layer = ff.ff_filter(layer, 'geq', r=phase + 'r' + sample_at,
                                     g=phase + 'g' + sample_at, b=phase + 'b' + sample_at,
                                     a=phase + 'alpha' + sample_at, interpolation='bilinear', enable=shifted)
                layer = ff.ff_filter(layer, 'unpremultiply', inplace=1, enable=shifted)
                return layer, 'floor(' + x + ')-1', 'floor(' + y + ')-1'

            video_inputs = [(path, placement) for path, placement in zip(paths, placements)
                            if placement[0] == 'V']
            if video_inputs:
                base = graph.decode({'input_path': video_inputs[0][0]})['video']
                canvas = ff.ff_filter(ff.scale(base, raster), 'fps', rate)
            else:
                waveform = graph.decode({'input_path': paths[0]})['audio']
                canvas = ff.ff_filter(waveform, 'showwaves', 's=' + raster + ':r=' + rate)
            canvas = ff.ff_filter(canvas, 'drawbox', color='black', t='fill')
            canvas = ff.ff_filter(canvas, 'tpad', stop_duration=duration, stop_mode='clone')
            canvas = ff.trim(canvas, duration=duration)
            for path, placement in video_inputs:
                start, end = placement[1], placement[2]
                clip = graph.decode({'input_path': path})['video']
                clip = ff.ff_filter(clip, 'fps', rate)
                clip = ff.ff_filter(clip, 'tpad', stop='1', stop_mode='clone')
                fx = placement[16].split(';')
                # Evaluate using timestamps relative to the authored clip, then place on output.
                clip = ff.setpts(clip, 'PTS-STARTPTS+' + fx[7] + '/TB')
                clip, px, py = transformed(clip, placement, fx)
                clip = ff.setpts(clip, 'PTS-STARTPTS+' + start + '/TB')
                visible = '(1+sgn(t-' + start + '+0.000000001))*(1-sgn(t-' + end + '+0.000000001))'
                if len(placement) > 17:
                    clip, px, py = position_layer(clip, placement[17], start, fx[7])
                canvas = ff.overlay(canvas, clip, x=str(px), y=str(py), enable=visible, format='rgb')

            def still_at_output_rate(path):
                block = graph.decode({'input_path': path})['video']
                # Duplicate pixels first, then assign one exact output-frame tick to each duplicate.
                block = ff.ff_filter(block, 'format', 'rgba')
                output_rate = Fraction(rate)
                frames = -(-Fraction(duration) * output_rate // 1)
                block = ff.loop(block, loop=frames - 1, size=1, start=0)
                block = ff.ff_filter(block, 'settb', 'expr=' + str(1 / output_rate))
                return ff.setpts(block, 'N')

            if subtitles:
                raster_paths = {fields[index] for fields in subtitles for index in (5, 8, 12) if len(fields) > index and fields[index]}
                print('BMF_SUBTITLE_RASTERS unique=' + str(len(raster_paths)) + ' layers=' + str(len(subtitles))
                      + ' bytes=' + str(sum(os.path.getsize(path) for path in raster_paths)), flush=True)
            def cue_opacity(layer, samples, start, elapsed):
                if not any(point.split(';')[3] != '1000000' for point in samples.split('|')):
                    return layer
                alpha = motion_expression(samples, 3, start, elapsed, 'T')
                return ff.ff_filter(layer, 'geq', r='r(X,Y)', g='g(X,Y)', b='b(X,Y)',
                                    a='alpha(X,Y)*(' + alpha + ')/1000000', interpolation='nearest')

            def frames_visible(onset, end):
                number = 'floor(t*(' + rate + ')+0.5)'
                return '(1+sgn(' + number + '-' + onset + '+0.5))*(1-sgn(' + number + '-' + end + '+0.5))'

            for onset, end, start, elapsed, samples, path, *style in subtitles:
                block = still_at_output_rate(path)
                if style and style[0]:
                    fade_start, fade_duration, selected_path = style[:3]
                    begin = Fraction(start) - Fraction(elapsed) + Fraction(fade_start)
                    local = 'clip((T-(' + str(begin) + '))/(' + fade_duration + '),0,1)'
                    selected = ff.ff_filter(still_at_output_rate(selected_path), 'geq',
                                            r='r(X,Y)', g='g(X,Y)', b='b(X,Y)',
                                            a='alpha(X,Y)*' + local, interpolation='nearest')
                    block = ff.overlay(block, selected, x='0', y='0', format='rgb')
                    block = ff.ff_filter(block, 'format', 'rgba')
                block, px, py = position_layer(cue_opacity(block, samples, start, elapsed), samples, start, elapsed)
                canvas = ff.overlay(canvas, block, x=str(px), y=str(py), enable=frames_visible(onset, end), format='rgb')
                if len(style) == 7:
                    first, last, offset, sprite_path = style[3:]
                    sprite = cue_opacity(still_at_output_rate(sprite_path), samples, start, elapsed)
                    sprite, sprite_x, sprite_y = position_layer(sprite, samples, start, elapsed)
                    canvas = ff.overlay(canvas, sprite, x='(' + str(sprite_x) + ')+' + offset, y=str(sprite_y),
                                        enable=frames_visible(first, last), format='rgb')

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

    private static String fxToken(BmfCpuTranscodePlan.TimedInput input) {
        var e = input.effect();
        return e.brightness() + ";" + e.contrast() + ";" + e.saturation() + ";"
                + (e.grayscale() ? "1" : "0") + ";" + e.fadeInSeconds() + ";"
                + e.fadeOutSeconds() + ";" + (e.enabled() ? "1" : "0") + ";"
                + seconds(input.elapsed()) + ";" + seconds(input.authoredDuration()) + ";"
                + seconds(input.dissolveDuration()) + input.opacityKeys().map(k -> ";" + k.firstTime().toString()
                        + ";" + k.firstValue() + ";" + k.lastTime().subtract(k.firstTime()).toString() + ";" + k.lastValue()).orElse("");
    }

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
                ? singleInvocation(plan, window)
                : compositeInvocation(plan);
        var command = new ExecutionCommand(context.executableTaskId(), context.providerBindingPin(),
                context.platformExecutionAttemptId(), context.platformOwnershipGeneration(), 0, invocation);
        return new RuntimeExecutionBundle(context.executableTaskId(), context.providerBindingPin(),
                context.platformExecutionAttemptId(), context.platformOwnershipGeneration(), List.of(command));
    }

    private ProcessInvocationSpec singleInvocation(BmfCpuTranscodePlan plan, java.util.Optional<BmfCpuTranscodePlan.SourceWindow> window) {
        var arguments = new java.util.ArrayList<>(List.of("-s", "-c", GRAPH_PROGRAM,
                materializedInputToken(plan.inputId()), ProviderNativeOutputFiles.RENDER_OUTPUT,
                plan.operation().operationKey(), window.map(value -> seconds(value.start())).orElse("0"),
                window.map(value -> seconds(value.duration())).orElse("0"), rasterToken(plan)));
        if (plan.compositeFrameRate().isPresent()) {
            var rate = plan.compositeFrameRate().orElseThrow();
            arguments.add(rate.numerator() + "/" + rate.denominator());
            arguments.add(seconds(plan.compositeDuration().orElseThrow()));
        }
        return ProcessInvocationSpec.of(python.toString(), arguments);
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
        arguments.add(rasterToken(plan));
        if (multitrack) {
            for (var input : plan.compositeInputs()) {
                var t = input.transform();
                arguments.add((input.audio() ? "A" : "V") + "," + seconds(input.start()) + ","
                        + seconds(input.end()) + "," + input.gain() + ","
                        + (input.mute() ? "1" : "0") + "," + input.balance() + ","
                        + t.x() + "," + t.y() + "," + t.scaleX() + ","
                        + t.scaleY() + "," + t.degrees() + "," + t.left() + ","
                        + t.right() + "," + t.top() + "," + t.bottom() + ","
                        + t.opacity() + "," + fxToken(input)
                        + input.positionKeys().map(p -> "," + p.canonicalSamples()).orElse(""));
            }
        }
        for (var cue : plan.subtitles()) {
            arguments.add(cue.firstFrame() + "," + cue.endFrame() + "," + cue.start() + "," + cue.elapsed() + ","
                    + cue.samples() + "," + cue.recipe().width() + "," + cue.recipe().fontSize() + ","
                    + java.util.Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(cue.recipe().text().getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "," + cue.recipe().fontProfile()
                            + cue.encodedStyleSuffix());
        }
        return ProcessInvocationSpec.of(python.toString(), List.copyOf(arguments));
    }

    private static String rasterToken(BmfCpuTranscodePlan plan) {
        return plan.raster().width() + "x" + plan.raster().height();
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
