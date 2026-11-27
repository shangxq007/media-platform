package com.example.platform.bmf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableInputProjection;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderRaster;
import com.example.platform.sandbox.BubblewrapSandboxCapabilityDetector;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import com.example.platform.workerfabric.domain.providernative.SandboxRuntimeCommandExecutor;
import com.example.platform.workerfabric.reuse.MaterializedArtifact;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the actual provider binding, worker sandbox, BMF graph and output stream when a pinned runtime is supplied. */
class BmfCpuWorkerRuntimeIntegrationTest {
    private static final ExecutableTaskId TASK_ID = new ExecutableTaskId("a".repeat(64));
    private static final ExecutionInputId INPUT_ID = new ExecutionInputId("source");
    private static final ExecutionOutputId OUTPUT_ID = new ExecutionOutputId("rendered");

    @TempDir Path temp;

    @Test
    void real_graph_runs_under_the_native_worker_sandbox_and_returns_media_bytes() throws Exception {
        Path runtimeRoot = configuredRuntime();
        Path source = Files.copy(Path.of(System.getenv("BMF_TEST_SOURCE")), temp.resolve("source.mp4"));
        var binding = BmfCpuRuntimeBindingFactory.create(
                runtimeRoot.resolve("host-bundle/usr/bin/python3.9"), runtimeRoot,
                temp.resolve("workspace"), Duration.ofSeconds(30), 2_000_000,
                SandboxCancellation.never());
        byte[] bytes;
        try (var output = binding.execute(task(), context("success"), List.of(materialized(source)))) {
            bytes = output.content().readAllBytes();
        }
        assertThat(bytes.length).isGreaterThan(1000);
        assertThat(new String(bytes, 4, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("ftyp");
        String preserved = System.getenv("BMF_TEST_OUTPUT");
        if (preserved != null && !preserved.isBlank()) {
            Files.write(Path.of(preserved), bytes);
        }
    }

    @Test
    void corrupt_input_fails_through_the_native_process_error_path() throws Exception {
        Path runtimeRoot = configuredRuntime();
        Path source = Files.writeString(temp.resolve("corrupt.mp4"), "not media");
        var binding = BmfCpuRuntimeBindingFactory.create(
                runtimeRoot.resolve("host-bundle/usr/bin/python3.9"), runtimeRoot,
                temp.resolve("failure-workspace"), Duration.ofSeconds(30), 2_000_000,
                SandboxCancellation.never());
        assertThatThrownBy(() -> binding.execute(task(), context("corrupt"),
                List.of(materialized(source))))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(ProviderNativeFailureCode.PROCESS_NONZERO_EXIT));
    }

    @Test
    void native_rasters_share_one_scoped_mount_per_profile_and_leave_no_file_on_close() throws Exception {
        Path runtime = configuredRuntime();
        Path source = Files.copy(Path.of(System.getenv("BMF_TEST_SOURCE")), temp.resolve("native-subtitle.mp4"));
        Path python = runtime.resolve("host-bundle/usr/bin/python3.9");
        var launcher = BubblewrapSandboxCapabilityDetector.detect().launcher().orElseThrow();
        var resolver = new BmfSandboxExecutionPolicyResolver(python, runtime,
                BmfSandboxWorkspace.under(temp.resolve("subtitle-workspace")), Duration.ofSeconds(30),
                2_000_000, launcher.capabilities());
        var seconds = MediaTime.ofMillis(3500);
        String text = String.join("\n", java.util.Collections.nCopies(6, "鬱永🌻🦋🦚🍉"));
        var cue = new com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.CueRaster(
                0, 84, MediaTime.ZERO, MediaTime.ZERO, "0;100000;550000;1000000",
                new com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.BlockRasterRecipe(text, 304, 44, null));
        var serif = new com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.CueRaster(
                0, 84, MediaTime.ZERO, MediaTime.ZERO, cue.samples(),
                new com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.BlockRasterRecipe(
                        text, 304, 44, "noto-serif-sc-color-emoji-v1"));
        var plan = new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING, BmfOperation.MULTITRACK_COMPOSITE,
                INPUT_ID, OUTPUT_ID, Optional.empty(),
                List.of(new BmfCpuTranscodePlan.TimedInput(INPUT_ID, MediaTime.ZERO, seconds)),
                Optional.of(seconds), Optional.of(FrameRate.of(24, 1)), new RenderRaster(1600, 1200),
                List.of(cue, serif, cue));
        var command = new BmfCpuRuntimeAdapter(python).adapt(plan, context("native-subtitle")).commands().getFirst();
        Path attachment;
        Path alternate;
        try (var prepared = resolver.prepare(command, List.of(materialized(source)))) {
            var ready = (com.example.platform.sandbox.SandboxResolution.Resolved) prepared.resolution();
            var mounts = ready.specification().filesystem().readOnlyInputs().stream()
                    .filter(file -> file.getFileName().toString().matches("block-[0-9]+\\.png")).toList();
            assertThat(mounts).hasSize(2);
            attachment = mounts.stream().filter(p -> p.endsWith("block-0.png")).findFirst().orElseThrow();
            alternate = mounts.stream().filter(p -> p.endsWith("block-1.png")).findFirst().orElseThrow();
            assertThat(Files.mismatch(attachment, alternate)).isNotEqualTo(-1L);
            var args = ready.specification().process().arguments();
            assertThat(args.getLast()).isEqualTo(args.get(args.size() - 3));
            assertThat(args.getLast()).isNotEqualTo(args.get(args.size() - 2));
            assertThat(Files.size(attachment)).isGreaterThan(131072);
            assertThat(ready.specification().process().arguments().getLast()).contains(",/sandbox-inputs/input-");
            var image = javax.imageio.ImageIO.read(attachment.toFile());
            assertThat(image.getWidth()).isEqualTo(1520);
            assertThat(image.getHeight()).isEqualTo(1940);
        }
        assertThat(Files.exists(attachment)).isFalse();
        assertThat(Files.exists(alternate)).isFalse();
        assertThat(Files.exists(attachment.getParent())).isFalse();
    }

    @Test
    void repeated_materialized_source_keeps_each_input_and_sandbox_runtime_addressable() throws Exception {
        Path runtime = configuredRuntime();
        Path source = Files.copy(Path.of(System.getenv("BMF_TEST_SOURCE")), temp.resolve("a-shared.mp4"));
        Path followingSource = Files.copy(source, temp.resolve("z-following.mp4"));
        Path python = runtime.resolve("host-bundle/usr/bin/python3.9");
        var launcher = BubblewrapSandboxCapabilityDetector.detect().launcher().orElseThrow();
        var workspace = BmfSandboxWorkspace.under(temp.resolve("shared-workspace"));
        var resolver = new BmfSandboxExecutionPolicyResolver(python, runtime, workspace,
                Duration.ofSeconds(30), 2_000_000, launcher.capabilities());
        var executor = new SandboxRuntimeCommandExecutor(launcher, resolver, SandboxCancellation.never(),
                workspace.outputStagingRoot());
        var repeated = new ExecutionInputId("same-source-second-clip");
        var following = new ExecutionInputId("following-source-clip");
        var midpoint = MediaTime.ofMillis(2000);
        var thirdStart = MediaTime.ofMillis(4000);
        var end = MediaTime.ofMillis(6000);
        var plan = new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING, BmfOperation.COMPOSITE,
                INPUT_ID, OUTPUT_ID, Optional.empty(),
                List.of(new BmfCpuTranscodePlan.TimedInput(INPUT_ID, MediaTime.ZERO, midpoint),
                        new BmfCpuTranscodePlan.TimedInput(repeated, midpoint, thirdStart),
                        new BmfCpuTranscodePlan.TimedInput(following, thirdStart, end)),
                Optional.of(end), Optional.of(FrameRate.of(24, 1)), RenderRaster.DEFAULT);
        var command = new BmfCpuRuntimeAdapter(python).adapt(plan, context("repeated-source"));
        var first = materialized(source);
        var second = new MaterializedExecutionInput(repeated, first.artifactPin(), first.materializedArtifact());
        var third = new MaterializedExecutionInput(following, first.artifactPin(),
                new MaterializedArtifact(first.artifactPin(), followingSource, Files.size(followingSource)));
        try (var output = executor.execute(command, List.of(first, second, third))) {
            var bytes = output.content().readAllBytes();
            assertThat(bytes.length).isGreaterThan(1000);
            assertThat(new String(bytes, 4, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("ftyp");
        }
    }

    @Test
    void nonzero_placement_scale_curve_executes_in_the_pinned_native_graph() throws Exception {
        Path runtime = configuredRuntime();
        Path source = Files.copy(Path.of(System.getenv("BMF_TEST_SOURCE")), temp.resolve("scale-source.mp4"));
        Path python = runtime.resolve("host-bundle/usr/bin/python3.9");
        var launcher = BubblewrapSandboxCapabilityDetector.detect().launcher().orElseThrow();
        var workspace = BmfSandboxWorkspace.under(temp.resolve("scale-workspace"));
        var resolver = new BmfSandboxExecutionPolicyResolver(python, runtime, workspace,
                Duration.ofSeconds(45), 2_000_000, launcher.capabilities());
        var executor = new SandboxRuntimeCommandExecutor(launcher, resolver, SandboxCancellation.never(),
                workspace.outputStagingRoot());
        var duration = MediaTime.parse("3/1");
        var axes = new BmfCpuTranscodePlan.VideoPlacement(.0625, .05, .5, .4, 0, 0, 1, 0, 1, 1);
        var ramp = com.example.platform.render.domain.renderplan.RenderDependency.ScaleRamp.parseSamples(
                "1/2;1000000|49/24;1500000|5/2;1000000");
        var input = new BmfCpuTranscodePlan.TimedInput(INPUT_ID, MediaTime.parse("1/1"), MediaTime.parse("4/1"),
                0, false, 1, false, 0, axes, com.example.platform.render.domain.renderplan.VideoEffectSpec.IDENTITY,
                MediaTime.ZERO, duration, MediaTime.ZERO, Optional.empty(), Optional.empty(), Optional.of(ramp));
        var plan = new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING, BmfOperation.MULTITRACK_COMPOSITE,
                INPUT_ID, OUTPUT_ID, Optional.empty(), List.of(input), Optional.of(MediaTime.parse("4/1")),
                Optional.of(FrameRate.of(24, 1)), new RenderRaster(320, 240), List.of());
        var commands = new BmfCpuRuntimeAdapter(python).adapt(plan, context("animated-scale"));
        try (var output = executor.execute(commands, List.of(materialized(source)))) {
            byte[] bytes = output.content().readAllBytes();
            assertThat(bytes.length).isGreaterThan(1000);
            assertThat(new String(bytes, 4, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("ftyp");
        } catch (ProviderNativeExecutionFailure failure) {
            throw new AssertionError(failure.diagnostics().getOrDefault("boundedStderr", failure.getMessage()), failure);
        }
    }

    @Test
    void animated_effective_unity_retains_native_stripe_contrast() throws Exception {
        Path runtime = configuredRuntime();
        var source = scaleSource("stripes", List.of("-vf", "drawgrid=w=8:h=0:t=4:c=black"));
        var reference = renderScale(runtime, source, "reference", 1, null);
        var animated = renderScale(runtime, source, "effective-unity", .1, "1/2;1000000|3/2;10000000");

        // Full crop, zero rotation, and an integer-aligned anchor. Sample stripe centers
        // away from the codec edge, not the envelope of the clip.
        int dark = 60 * 320 * 240 + 80 * 320 + 10;
        int light = dark + 4;
        assertThat(Byte.toUnsignedInt(reference[light]) - Byte.toUnsignedInt(reference[dark]))
                .isGreaterThanOrEqualTo(235);
        for (int pixel : new int[]{dark, light}) {
            assertThat(Math.abs(Byte.toUnsignedInt(animated[pixel]) - Byte.toUnsignedInt(reference[pixel])))
                    .isLessThanOrEqualTo(12);
        }
    }

    @Test
    void animated_scale_keeps_the_authored_side_of_an_exact_output_frame() throws Exception {
        Path runtime = configuredRuntime();
        var source = scaleSource("solid", List.of());
        var equal = renderScale(runtime, source, "at-frame", .5,
                "99999999999999999/100000000000000000;1000000|1/1;1500000");
        var later = renderScale(runtime, source, "after-frame", .5,
                "1/1;1000000|100000000000000001/100000000000000000;1500000");

        assertThat(widthsAtCut(equal)).containsExactly(160, 240, 240);
        assertThat(widthsAtCut(later)).containsExactly(160, 160, 240);
    }

    private static List<Integer> widthsAtCut(byte[] frames) {
        return java.util.stream.IntStream.rangeClosed(23, 25).map(frame -> {
            int offset = frame * 320 * 240 + 20 * 320;
            int right = 0;
            while (right < 320 && Byte.toUnsignedInt(frames[offset + right]) >= 205) right++;
            return right;
        }).boxed().toList();
    }

    private Path scaleSource(String tag, List<String> filters) throws Exception {
        Path input = temp.resolve(tag + ".mkv");
        var args = new java.util.ArrayList<>(List.of("-f", "lavfi", "-i", "color=white:s=320x240:r=24:d=3"));
        args.addAll(filters);
        args.addAll(List.of("-c:v", "ffv1", input.toString()));
        runFfmpeg(args, temp.resolve(tag + "-source.log"));
        return input;
    }

    private byte[] renderScale(Path runtime, Path source, String tag, double axes, String samples) throws Exception {
        Path python = runtime.resolve("host-bundle/usr/bin/python3.9");
        var launcher = BubblewrapSandboxCapabilityDetector.detect().launcher().orElseThrow();
        var workspace = BmfSandboxWorkspace.under(temp.resolve(tag + "-workspace"));
        var resolver = new BmfSandboxExecutionPolicyResolver(python, runtime, workspace,
                Duration.ofSeconds(45), 2_000_000, launcher.capabilities());
        var executor = new SandboxRuntimeCommandExecutor(launcher, resolver, SandboxCancellation.never(),
                workspace.outputStagingRoot());
        var duration = MediaTime.parse("3/1");
        var transform = new BmfCpuTranscodePlan.VideoPlacement(0, 0, axes, axes, 0, 0, 1, 0, 1, 1);
        var ramp = Optional.ofNullable(samples)
                .map(com.example.platform.render.domain.renderplan.RenderDependency.ScaleRamp::parseSamples);
        var input = new BmfCpuTranscodePlan.TimedInput(INPUT_ID, MediaTime.ZERO, duration, 0, false, 1,
                false, 0, transform, com.example.platform.render.domain.renderplan.VideoEffectSpec.IDENTITY,
                MediaTime.ZERO, duration, MediaTime.ZERO, Optional.empty(), Optional.empty(), ramp);
        var plan = new BmfCpuTranscodePlan(TASK_ID, BmfCpuProvider.BINDING, BmfOperation.MULTITRACK_COMPOSITE,
                INPUT_ID, OUTPUT_ID, Optional.empty(), List.of(input), Optional.of(duration),
                Optional.of(FrameRate.of(24, 1)), new RenderRaster(320, 240), List.of());
        Path encoded = temp.resolve(tag + ".mp4");
        try (var output = executor.execute(new BmfCpuRuntimeAdapter(python).adapt(plan, context(tag)),
                List.of(materialized(source)))) {
            Files.copy(output.content(), encoded);
        }
        Path decoded = temp.resolve(tag + ".gray");
        runFfmpeg(List.of("-i", encoded.toString(), "-f", "rawvideo", "-pix_fmt", "gray", decoded.toString()),
                temp.resolve(tag + "-decode.log"));
        var frames = Files.readAllBytes(decoded);
        assertThat(frames.length).isEqualTo(72 * 320 * 240);
        return frames;
    }

    private static void runFfmpeg(List<String> args, Path log) throws Exception {
        Path ffmpeg = Path.of("/usr/bin/ffmpeg");
        Assumptions.assumeTrue(Files.isExecutable(ffmpeg), "host FFmpeg is required to generate/score pixel fixtures");
        var command = new java.util.ArrayList<>(List.of(ffmpeg.toString(), "-v", "error", "-nostdin"));
        command.addAll(args);
        var child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!child.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            child.destroyForcibly();
            throw new AssertionError("fixture FFmpeg timed out");
        }
        assertThat(child.exitValue()).as(Files.readString(log)).isZero();
    }

    private static Path configuredRuntime() {
        String root = System.getenv("BMF_TEST_RUNTIME_ROOT");
        String source = System.getenv("BMF_TEST_SOURCE");
        Assumptions.assumeTrue(root != null && source != null,
                "controlled BMF runtime and source are not configured");
        Path runtime = Path.of(root).toAbsolutePath().normalize();
        assertThat(BmfRuntimeBundleVerifier.verify(runtime)).isTrue();
        return runtime;
    }

    private static ExecutableTask task() {
        var task = mock(ExecutableTask.class);
        var membership = mock(ExecutableTaskMembership.class);
        var unit = mock(PhysicalPlanUnit.class);
        var input = mock(ExecutableInputProjection.class);
        when(task.id()).thenReturn(TASK_ID);
        when(task.providerBindingPin()).thenReturn(BmfCpuProvider.BINDING);
        when(task.memberships()).thenReturn(List.of(membership));
        when(membership.physicalPlanUnit()).thenReturn(unit);
        when(unit.operationKey()).thenReturn("transcode");
        when(unit.capabilityRequirementRefs()).thenReturn(List.of(
                new CapabilityRequirementRef(CapabilityRequirement.of(
                        CapabilityId.of("media.transcode"),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0))))));
        when(unit.executionIntentRefs()).thenReturn(List.of());
        when(unit.typedInputs()).thenReturn(List.of(mock(InputBinding.class)));
        when(unit.typedOutputs()).thenReturn(List.of(mock(OutputDeclaration.class)));
        when(task.requiredRuntimeInputs()).thenReturn(List.of(input));
        when(input.inputId()).thenReturn(INPUT_ID);
        when(task.authoritativeOutputIds()).thenReturn(List.of(OUTPUT_ID));
        return task;
    }

    private static MaterializedExecutionInput materialized(Path source) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source));
        var pin = new ArtifactPin(new ArtifactId("bmf-source"),
                ContentDigest.sha256(HexFormat.of().formatHex(digest)));
        return new MaterializedExecutionInput(INPUT_ID, pin,
                new MaterializedArtifact(pin, source, Files.size(source)));
    }

    private static RuntimeExecutionContext context(String attempt) {
        return new RuntimeExecutionContext(TASK_ID, BmfCpuProvider.BINDING,
                ExecutionAttemptId.of(attempt), ExecutionOwnershipGeneration.first());
    }
}
