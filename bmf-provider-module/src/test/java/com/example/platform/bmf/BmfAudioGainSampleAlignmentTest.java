package com.example.platform.bmf;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.Arrays;
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
        var probe = copyProbe(python);
        var output = resultDirectory();
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
                var gain = RenderDependency.GainRamp.parseSamples(
                        FIRST.subtract(clipStart) + ";0|" + LAST.subtract(clipStart) + ";1000000");
                var pcm = render(python, probe, dir, clipStart, extentStart, Optional.of(gain), true);
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

    @Test
    void halfSamplePlacementIsIndependentOfTheGainCurve() throws Exception {
        var python = Path.of("/usr/bin/python3");
        var probe = copyProbe(python);
        var output = resultDirectory();
        var start = MediaTime.ofRational(48_001, 96_000);
        var unity = RenderDependency.GainRamp.parseSamples("1/4;1000000|1/2;1000000");
        var baseline = render(python, probe, Files.createDirectory(output.resolve("half-up-static")),
                start, MediaTime.ZERO, Optional.empty(), false);
        var keyed = render(python, probe, Files.createDirectory(output.resolve("half-up-unity")),
                start, MediaTime.ZERO, Optional.of(unity), false);

        // This half sample rounds the other way through the static decimal. The
        // initial unity hold makes any placement shift audible even with a real aeval.
        var earlierStart = MediaTime.ofRational(47_999, 96_000);
        var fall = MediaTime.ofRational(36_000, RATE);
        var half = RenderDependency.GainRamp.parseSamples(fall.subtract(earlierStart)
                + ";1000000|" + fall.add(MediaTime.ofRational(2, RATE)).subtract(earlierStart) + ";500000");
        var earlier = render(python, probe, Files.createDirectory(output.resolve("half-down-static")),
                earlierStart, MediaTime.ZERO, Optional.empty(), false);
        var changing = render(python, probe, Files.createDirectory(output.resolve("half-down-non-unity")),
                earlierStart, MediaTime.ZERO, Optional.of(half), true);

        assertAll(
                () -> assertEquals(24_001, onset(baseline), "static half-up onset"),
                () -> assertEquals(24_001, onset(keyed), "unity half-up onset"),
                () -> assertEquals(baseline.capacity(), keyed.capacity(), "static/unity PCM lengths"),
                () -> assertArrayEquals(baseline.array(), keyed.array(), "complete unshifted static/unity PCM"),
                () -> assertEquals(23_999, onset(earlier), "static half-down onset"),
                () -> assertEquals(23_999, onset(changing), "non-unity half-down onset"),
                () -> assertArrayEquals(Arrays.copyOf(earlier.array(), 4 * 36_001),
                        Arrays.copyOf(changing.array(), 4 * 36_001), "PCM through the initial unity hold"),
                () -> assertStereo(changing, 36_000, 1024, 512),
                () -> assertStereo(changing, 36_001, 768, 384),
                () -> assertStereo(changing, 36_002, 512, 256));
    }

    @Test
    void edgeFadesUseAuthoredTimesAndMultiplyTheManualGain() throws Exception {
        var python = Path.of("/usr/bin/python3");
        var probe = copyProbe(python);
        var output = resultDirectory();
        var start = MediaTime.ofRational(1, 1);
        var end = MediaTime.ofRational(5, 1);
        var extentEnd = MediaTime.ofRational(6, 1);
        var manual = Optional.of(RenderDependency.GainRamp.parseSamples("0;0|1/1;1000000"));
        var fade = new RenderDependency.AudioFades(start, MediaTime.ofRational(1, 2));
        var baseline = renderInterval(python, probe, Files.createDirectory(output.resolve("n24-manual")),
                start, end, MediaTime.ZERO, extentEnd, manual, RenderDependency.AudioFades.NONE, true);
        var cleared = renderInterval(python, probe, Files.createDirectory(output.resolve("n24-cleared")),
                start, end, MediaTime.ZERO, extentEnd, manual, RenderDependency.AudioFades.NONE, true);
        assertArrayEquals(baseline.array(), cleared.array());
        for (var kind : List.of("edges-full", "edges-crop", "product-full", "product-crop")) {
            boolean cropped = kind.endsWith("crop");
            boolean product = kind.startsWith("product");
            var dir = Files.createDirectory(output.resolve("n24-" + kind));
            var crop = cropped ? MediaTime.ofRational(5, 4) : MediaTime.ZERO;
            var pcm = renderInterval(python, probe, dir, start, end, crop, extentEnd,
                    product ? manual : Optional.empty(), fade, true);
            int shift = cropped ? 60_000 : 0;
            assertStereo(pcm, 72_000 - shift, product ? 256 : 512, product ? 128 : 256);
            assertStereo(pcm, 144_000 - shift, 1024, 512);
            assertStereo(pcm, 228_000 - shift, 512, 256);
            var precise = ByteBuffer.wrap(Files.readAllBytes(dir.resolve("before-s16.f64le")))
                    .order(ByteOrder.LITTLE_ENDIAN);
            assertEquals((1024 / 32768.0) / 24_000, precise.getDouble(16 * (239_999 - shift)), 1e-12);
            assertEquals((512 / 32768.0) / 24_000, precise.getDouble(16 * (239_999 - shift) + 8), 1e-12);
            assertEquals(0, precise.getDouble(16 * (240_000 - shift)));
            assertEquals(0, precise.getDouble(16 * (240_000 - shift) + 8));
        }
    }

    @Test
    void outgoingFadeKeepsTheLastSampleBeforeAFractionalEdge() throws Exception {
        var python = Path.of("/usr/bin/python3");
        var probe = copyProbe(python);
        var output = resultDirectory();
        var end = MediaTime.ofRational(48_009, 96_000);
        var fade = new RenderDependency.AudioFades(MediaTime.ZERO, MediaTime.ofRational(4, RATE));
        for (var extent : List.of("full", "crop")) {
            var crop = extent.equals("crop") ? MediaTime.ofRational(1, 4) : MediaTime.ZERO;
            var dir = Files.createDirectory(output.resolve("n25-fractional-" + extent));
            var pcm = renderInterval(python, probe, dir, MediaTime.ZERO, end, crop, END,
                    Optional.empty(), fade, true);
            int shift = extent.equals("crop") ? 12_000 : 0;
            assertStereo(pcm, 24_000 - shift, 1024, 512);
            assertStereo(pcm, 24_004 - shift, 128, 64);
            assertStereo(pcm, 24_005 - shift, 0, 0);
        }
    }

    @Test
    void independentCrossfadeAndAuthoredReleaseMultiplyOnTheSampleClock() throws Exception {
        var python = Path.of("/usr/bin/python3");
        var probe = copyProbe(python);
        var output = resultDirectory();
        var start = MediaTime.ofMillis(4000);
        var end = MediaTime.ofMillis(7000);
        var fade = new RenderDependency.AudioFades(MediaTime.ZERO, MediaTime.ofMillis(2500),
                MediaTime.ofMillis(1000), MediaTime.ZERO);
        for (var extent : List.of("full", "crop")) {
            var crop = extent.equals("crop") ? MediaTime.ofMillis(4500) : MediaTime.ZERO;
            long shift = extent.equals("crop") ? 216_000 : 0;
            for (boolean keyed : List.of(false, true)) {
                var manual = keyed ? Optional.of(RenderDependency.GainRamp.parseSamples("0;500000|1/1;1000000"))
                        : Optional.<RenderDependency.GainRamp>empty();
                var dir = Files.createDirectory(output.resolve("n25-f1-" + extent + (keyed ? "-manual" : "-unity")));
                renderInterval(python, probe, dir, start, end, crop, MediaTime.ofMillis(8000), manual, fade, true);
                var precise = ByteBuffer.wrap(Files.readAllBytes(dir.resolve("before-s16.f64le")))
                        .order(ByteOrder.LITTLE_ENDIAN);
                int[] samples = {216_000, 228_000, 234_000, 240_000};
                double[] expected = {.5, .675, .74375, .8};
                for (int i = 0; i < samples.length; i++) {
                    double weight = expected[i] * (keyed ? .5 + (samples[i] - 192_000) / 96_000.0 : 1);
                    int offset = Math.toIntExact(16 * (samples[i] - shift));
                    // FFmpeg's mix stage uses float before the precision capture.
                    double oneFloatUlp = Math.ulp((float) weight);
                    assertEquals(weight, precise.getDouble(offset) * 32, oneFloatUlp);
                    assertEquals(weight, precise.getDouble(offset + 8) * 64, oneFloatUlp);
                }
            }
        }
    }

    private static int onset(ByteBuffer pcm) {
        for (int i = 0; i < pcm.capacity() / 4; i++) {
            if (pcm.getShort(4 * i) != 0 || pcm.getShort(4 * i + 2) != 0) return i;
        }
        return -1;
    }

    private static void assertStereo(ByteBuffer pcm, int sample, int left, int right) {
        assertEquals(left, pcm.getShort(4 * sample), "sample " + sample + " left");
        assertEquals(right, pcm.getShort(4 * sample + 2), "sample " + sample + " right");
    }

    private Path copyProbe(Path python) throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(python) && Files.isExecutable(Path.of("/usr/bin/ffmpeg")),
                "Python and host libavfilter fixture runner are required");
        var probe = temp.resolve("probe.py");
        try (var source = getClass().getResourceAsStream("/bmf-gain-pcm-probe.py")) {
            Files.copy(java.util.Objects.requireNonNull(source), probe);
        }
        return probe;
    }

    private Path resultDirectory() throws Exception {
        String retained = System.getenv("BMF_GAIN_PROBE_DIR");
        var output = retained == null ? temp.resolve("result") : Path.of(retained);
        return Files.createDirectories(output);
    }

    private ByteBuffer render(Path python, Path probe, Path dir, MediaTime clipStart, MediaTime extentStart,
                              Optional<RenderDependency.GainRamp> gain, boolean usesEnvelope) throws Exception {
        return renderInterval(python, probe, dir, clipStart, END, extentStart, END,
                gain, RenderDependency.AudioFades.NONE, usesEnvelope);
    }

    private ByteBuffer renderInterval(Path python, Path probe, Path dir,
            MediaTime clipStart, MediaTime clipEnd, MediaTime extentStart, MediaTime extentEnd,
            Optional<RenderDependency.GainRamp> gain, RenderDependency.AudioFades fades, boolean usesEnvelope) throws Exception {
        boolean crop = extentStart.isGreaterThan(clipStart);
        var inputId = new ExecutionInputId("constant-audio");
        var input = new BmfCpuTranscodePlan.TimedInput(inputId,
                crop ? MediaTime.ZERO : clipStart.subtract(extentStart), clipEnd.subtract(extentStart),
                0, true, 1, false, 0, BmfCpuTranscodePlan.VideoPlacement.IDENTITY, VideoEffectSpec.IDENTITY,
                crop ? extentStart.subtract(clipStart) : MediaTime.ZERO, clipEnd.subtract(clipStart), MediaTime.ZERO,
                Optional.empty(), Optional.empty(), Optional.empty(), gain, fades);
        var plan = new BmfCpuTranscodePlan(TASK, BmfCpuProvider.BINDING, BmfOperation.MULTITRACK_COMPOSITE,
                inputId, new ExecutionOutputId("composite"), Optional.empty(), List.of(input),
                Optional.of(extentEnd.subtract(extentStart)), Optional.of(FrameRate.of(24, 1)),
                new RenderRaster(320, 240), List.of());
        var context = new RuntimeExecutionContext(TASK, BmfCpuProvider.BINDING,
                ExecutionAttemptId.of("gain-sample-alignment"), ExecutionOwnershipGeneration.first());
        var invocation = (ProcessInvocationSpec) new BmfCpuRuntimeAdapter(python).adapt(plan, context)
                .commands().getFirst().invocationSpec();
        var args = Files.writeString(dir.resolve("argv.txt"), String.join(Character.toString(0), invocation.arguments()));
        var log = dir.resolve("probe.log");
        var command = new ProcessBuilder(python.toString(), probe.toString(), args.toString(), dir.toString(), usesEnvelope ? "1" : "0", fades.active() ? "precision" : "legacy")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!command.waitFor(15, TimeUnit.SECONDS)) {
            command.destroyForcibly();
            throw new AssertionError("Audio sample probe timed out");
        }
        assertEquals(0, command.exitValue(), Files.readString(log));
        return ByteBuffer.wrap(Files.readAllBytes(dir.resolve("before-aac.s16le"))).order(ByteOrder.LITTLE_ENDIAN);
    }
}
