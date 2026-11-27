package com.example.platform.bmf;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderRaster;
import com.example.platform.render.domain.renderplan.VideoEffectSpec;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class BmfAudioGainSampleAlignmentTest {
    private static final int RATE = 48_000;
    private static final MediaTime FIRST = MediaTime.ofRational(47_999, RATE);
    private static final MediaTime LAST = MediaTime.ofRational(1, 1);
    private static final MediaTime END = MediaTime.ofRational(2, 1);
    private static final ExecutableTaskId TASK = new ExecutableTaskId("b".repeat(64));

    @TempDir Path temp;

    @Test
    void gainEnvelopeUsesTheActualOutputSampleTimeAtAdjacentRationalKeys() throws Exception {
        var python = Path.of("/usr/bin/python3");
        Assumptions.assumeTrue(Files.isExecutable(python) && Files.isExecutable(Path.of("/usr/bin/ffmpeg")),
                "Python and host libavfilter fixture runner are required");
        var probe = temp.resolve("probe.py");
        try (var source = getClass().getResourceAsStream("/bmf-gain-pcm-probe.py")) {
            Files.copy(java.util.Objects.requireNonNull(source), probe);
        }
        String retained = System.getenv("BMF_GAIN_PROBE_DIR");
        var output = retained == null ? temp.resolve("result") : Path.of(retained);
        Files.createDirectories(output);
        var checks = new ArrayList<Executable>();
        var table = new StringBuilder("startCase\textent\toutputSample\ttimelineTime\texpectedGain\tleftGain\trightGain\n");

        for (var placement : List.of("round-up", "round-down", "on-grid")) {
            MediaTime clipStart = switch (placement) {
                case "round-up" -> MediaTime.ofRational(1001, 30_000);
                case "round-down" -> MediaTime.ofRational(999, 30_000);
                default -> MediaTime.ofRational(1001, RATE);
            };
            for (var extent : List.of("full", "cropped")) {
                MediaTime extentStart = extent.equals("full") ? MediaTime.ZERO : MediaTime.ofRational(1, 2);
                Path dir = Files.createDirectory(output.resolve(placement + "-" + extent));
                int pivot = extent.equals("full") ? RATE : RATE / 2;
                var pcm = render(python, probe, dir, clipStart, extentStart);
                for (int sample = pivot - 1; sample <= pivot + 1; sample++) {
                    MediaTime time = extentStart.add(MediaTime.ofRational(sample, RATE));
                    double expected = time.isLessThan(LAST) ? 0 : 1;
                    // Source amplitudes 1/8 and 1/16, neutral balance times .5,
                    // followed by the single-input mix factor .5. No AAC or shifts.
                    double left = pcm.getShort(4 * sample) / 1024.0;
                    double right = pcm.getShort(4 * sample + 2) / 512.0;
                    table.append(String.format(Locale.ROOT, "%s\t%s\t%d\t%s\t%.1f\t%.9f\t%.9f%n",
                            placement, extent, sample, time, expected, left, right));
                    String where = placement + " " + extent + " sample " + sample;
                    checks.add(() -> assertEquals(expected, left, 1e-12, where + " left"));
                    checks.add(() -> assertEquals(expected, right, 1e-12, where + " right"));
                }
            }
        }
        Files.writeString(output.resolve("sample-gains.tsv"), table);
        System.out.print(table);
        assertAll(checks);
    }

    private ByteBuffer render(Path python, Path probe, Path dir, MediaTime clipStart, MediaTime extentStart) throws Exception {
        var gain = RenderDependency.GainRamp.parseSamples(
                FIRST.subtract(clipStart) + ";0|" + LAST.subtract(clipStart) + ";1000000");
        boolean crop = extentStart.isGreaterThan(clipStart);
        var inputId = new ExecutionInputId("constant-audio");
        var input = new BmfCpuTranscodePlan.TimedInput(inputId,
                crop ? MediaTime.ZERO : clipStart.subtract(extentStart), END.subtract(extentStart),
                0, true, 1, false, 0, BmfCpuTranscodePlan.VideoPlacement.IDENTITY, VideoEffectSpec.IDENTITY,
                crop ? extentStart.subtract(clipStart) : MediaTime.ZERO, END.subtract(clipStart), MediaTime.ZERO,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(gain));
        var plan = new BmfCpuTranscodePlan(TASK, BmfCpuProvider.BINDING, BmfOperation.MULTITRACK_COMPOSITE,
                inputId, new ExecutionOutputId("composite"), Optional.empty(), List.of(input),
                Optional.of(END.subtract(extentStart)), Optional.of(FrameRate.of(24, 1)),
                new RenderRaster(320, 240), List.of());
        var context = new RuntimeExecutionContext(TASK, BmfCpuProvider.BINDING,
                ExecutionAttemptId.of("gain-sample-alignment"), ExecutionOwnershipGeneration.first());
        var invocation = (ProcessInvocationSpec) new BmfCpuRuntimeAdapter(python).adapt(plan, context)
                .commands().getFirst().invocationSpec();
        var args = Files.writeString(dir.resolve("argv.txt"), String.join(Character.toString(0), invocation.arguments()));
        var log = dir.resolve("probe.log");
        var command = new ProcessBuilder(python.toString(), probe.toString(), args.toString(), dir.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!command.waitFor(15, TimeUnit.SECONDS)) {
            command.destroyForcibly();
            throw new AssertionError("Audio sample probe timed out");
        }
        assertEquals(0, command.exitValue(), Files.readString(log));
        return ByteBuffer.wrap(Files.readAllBytes(dir.resolve("before-aac.s16le"))).order(ByteOrder.LITTLE_ENDIAN);
    }
}
