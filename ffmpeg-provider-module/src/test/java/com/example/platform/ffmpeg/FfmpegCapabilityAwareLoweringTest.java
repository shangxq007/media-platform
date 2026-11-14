package com.example.platform.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableInputProjection;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextRange;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.FontSelectionIntent;
import com.example.platform.fonttext.typography.FontSize;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.OpticalSizingIntent;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.TextStyleRun;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeOutputFiles;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import com.example.platform.sandbox.SandboxCancellation;

/**
 * Capability-aware lowering for the bounded CPU ffmpeg slice: the typed-chain
 * operations decode / composite / encode lower to their own typed plan and argv,
 * the capability-neutral transcode slice keeps working, and everything outside the
 * bounded shape fails closed.
 */
class FfmpegCapabilityAwareLoweringTest {
    @TempDir Path mediaWorkspace;

    private static final FfmpegCpuRenderLowerer LOWERER = new FfmpegCpuRenderLowerer();
    private static final StaticProviderExecutionContext CONTEXT =
            StaticProviderExecutionContext.fromBinding(FfmpegCpuProvider.BINDING);
    private static final RenderSampleWindow WINDOW = new RenderSampleWindow(
            MediaTime.ofMillis(1_500), MediaTime.ofMillis(3_500), FrameRate.of(30, 1));

    @Test
    void decodeLowersWithItsCapabilityAndHonoursTheAuthoredSourceWindow() {
        FfmpegCpuRenderPlan plan = LOWERER.lower(
                task("decode", List.of("video.decode"), WINDOW, 1), CONTEXT);

        assertThat(plan.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.DECODE);
        assertThat(plan.sourceWindow()).isPresent();
        assertThat(plan.sourceWindow().orElseThrow().start()).isEqualTo(MediaTime.ofMillis(1_500));
        assertThat(plan.sourceWindow().orElseThrow().duration()).isEqualTo(MediaTime.ofMillis(2_000));

        List<String> arguments = arguments(plan);
        assertThat(arguments).containsSequence("-ss", "1.5", "-t", "2");
        assertThat(arguments).contains("libx264", "yuv420p",
                ProviderNativeOutputFiles.RENDER_OUTPUT);
        assertThat(arguments).containsOnlyOnce(
                FfmpegCpuRenderAdapter.materializedInputToken(plan.inputId()));
    }

    @Test
    void compositeAndEncodeLowerWithoutASourceWindow() {
        FfmpegCpuRenderPlan composite = LOWERER.lower(compositeTaskWithoutSourceWindow(), CONTEXT);
        FfmpegCpuRenderPlan encode = LOWERER.lower(
                task("encode", List.of("render.output"), null, 1), CONTEXT);

        assertThat(composite.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.COMPOSITE);
        assertThat(encode.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.ENCODE);
        assertThat(composite.sourceWindow()).isEmpty();
        assertThat(encode.sourceWindow()).isEmpty();
        assertThat(arguments(composite)).doesNotContain("-ss");
        assertThat(arguments(encode)).doesNotContain("-ss");
    }

    @Test
    void timedCompositionPreservesInputIdentityAndPlacement() {
        ExecutableTask task = task("composite", List.of("render.composite"), null, 1);
        var unit = task.memberships().getFirst().physicalPlanUnit();
        var firstId = new ExecutionInputId("first");
        var secondId = new ExecutionInputId("second");
        var first = mock(InputBinding.class);
        var second = mock(InputBinding.class);
        when(first.inputId()).thenReturn(firstId);
        when(second.inputId()).thenReturn(secondId);
        when(first.dependencyVariant()).thenReturn(new com.example.platform.render.domain.renderplan.RenderDependency.TimedCompositeInput(
                MediaTime.ZERO, MediaTime.ofRational(1, 2)));
        when(second.dependencyVariant()).thenReturn(new com.example.platform.render.domain.renderplan.RenderDependency.TimedCompositeInput(
                MediaTime.ofRational(1, 1), MediaTime.ofRational(2, 1)));
        when(unit.typedInputs()).thenReturn(List.of(second, first));
        when(unit.propagatedExtent()).thenReturn(new com.example.platform.render.domain.renderplan.RenderExtent(
                MediaTime.ZERO, MediaTime.ofRational(2, 1), FrameRate.of(30, 1)));
        var firstRuntime = mock(ExecutableInputProjection.class);
        var secondRuntime = mock(ExecutableInputProjection.class);
        when(firstRuntime.inputId()).thenReturn(firstId);
        when(secondRuntime.inputId()).thenReturn(secondId);
        when(task.requiredRuntimeInputs()).thenReturn(List.of(firstRuntime, secondRuntime));
        var plan = LOWERER.lower(task, CONTEXT);
        assertThat(plan.compositeInputs()).extracting(FfmpegCpuRenderPlan.TimedInput::inputId)
                .containsExactly(firstId, secondId);
        var context = new RuntimeExecutionContext(plan.executableTaskId(), FfmpegCpuProvider.BINDING,
                ExecutionAttemptId.of("attempt-capability"), ExecutionOwnershipGeneration.first());
        var invocation = (ProcessInvocationSpec) new FfmpegCpuRenderAdapter(Path.of("/usr/bin/ffmpeg"))
                .adapt(plan, context).commands().getFirst().invocationSpec();
        assertThat(invocation.arguments()).containsOnlyOnce(FfmpegCpuRenderAdapter.materializedInputToken(firstId))
                .containsOnlyOnce(FfmpegCpuRenderAdapter.materializedInputToken(secondId))
                .contains("-filter_complex", "-t", "2");
        String filter = invocation.arguments().get(invocation.arguments().indexOf("-filter_complex") + 1);
        assertThat(filter).contains("color=c=black:s=320x240:r=30/1:d=2")
                .contains("overlay=format=auto:enable='gte(t,0)*lt(t,0.5)'")
                .contains("overlay=format=auto:enable='gte(t,1)*lt(t,2)'")
                .doesNotContain("tpad=");
    }

    @Test
    void textOverlayMayOverlapBaseVideoAndIsCompositedLast() {
        ExecutableTask task = task("composite", List.of("render.composite"), null, 1);
        var unit = task.memberships().getFirst().physicalPlanUnit();
        var textId = new ExecutionInputId("aaa-text");
        var videoId = new ExecutionInputId("zzz-video");
        var text = mock(InputBinding.class);
        var video = mock(InputBinding.class);
        when(text.inputId()).thenReturn(textId);
        when(video.inputId()).thenReturn(videoId);
        when(text.dependencyVariant()).thenReturn(
                new com.example.platform.render.domain.renderplan.RenderDependency.CompositeInput());
        when(video.dependencyVariant()).thenReturn(
                new com.example.platform.render.domain.renderplan.RenderDependency.TimedCompositeInput(
                        MediaTime.ZERO, MediaTime.ofRational(2, 1)));
        when(unit.typedInputs()).thenReturn(List.of(text, video));
        when(unit.propagatedExtent()).thenReturn(new RenderExtent(
                MediaTime.ZERO, MediaTime.ofRational(2, 1), FrameRate.of(30, 1)));
        var textRuntime = mock(ExecutableInputProjection.class);
        var videoRuntime = mock(ExecutableInputProjection.class);
        when(textRuntime.inputId()).thenReturn(textId);
        when(videoRuntime.inputId()).thenReturn(videoId);
        when(task.requiredRuntimeInputs()).thenReturn(List.of(textRuntime, videoRuntime));

        var plan = LOWERER.lower(task, CONTEXT);
        assertThat(plan.compositeInputs()).extracting(FfmpegCpuRenderPlan.TimedInput::inputId)
                .containsExactly(videoId, textId);
        assertThat(plan.compositeInputs().getLast().overlay()).isTrue();
    }

    @Test
    void transcodeSliceKeepsLoweringWithoutAnyCapabilityRequirement() {
        FfmpegCpuRenderPlan plan = LOWERER.lower(task("transcode", List.of(), null, 1), CONTEXT);

        assertThat(plan.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.TRANSCODE);
        assertThat(arguments(plan)).containsSequence("-f", "mp4",
                ProviderNativeOutputFiles.RENDER_OUTPUT);
    }

    @Test
    void everyTypedOperationEmitsTheSameCanonicalIntermediate() {
        List<String> decode = arguments(LOWERER.lower(
                task("decode", List.of("video.decode"), WINDOW, 1), CONTEXT));
        List<String> encode = arguments(LOWERER.lower(
                task("encode", List.of("render.output"), null, 1), CONTEXT));

        assertThat(decode.stream().filter("-ss"::equals).count()).isEqualTo(1);
        assertThat(encode).doesNotContain("-ss");
        assertThat(encode).contains("-c:v", "libx264", "-pix_fmt", "yuv420p");
        assertThat(encode).containsSequence("-f", "mp4", ProviderNativeOutputFiles.RENDER_OUTPUT);
        assertThat(encode).contains("+bitexact", "frag_keyframe+empty_moov+default_base_moof");
    }

    @Test
    void loweringFailsClosedOutsideTheBoundedSlice() {
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), null, 1),
                StaticProviderExecutionContext.fromBinding(foreignBinding())),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        assertFailure(() -> LOWERER.lower(task("mux", List.of(), null, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        assertFailure(() -> LOWERER.lower(task("encode", List.of(), null, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        assertFailure(() -> LOWERER.lower(task("encode", List.of("audio.mix"), null, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING);
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), WINDOW, 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        assertFailure(() -> LOWERER.lower(task("decode", List.of("video.decode"),
                        new RenderSampleWindow(MediaTime.ofRational(2, 1), MediaTime.ofRational(2, 1),
                                FrameRate.of(30, 1)), 1), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), null, 2), CONTEXT),
                ProviderNativeFailureCode.ILLEGAL_MULTI_MEMBERSHIP_LOWERING);
        assertFailure(() -> LOWERER.lower(task("transcode", List.of(), null, 1, 2), CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY);
    }

    private static void assertFailure(ThrowingCall call, ProviderNativeFailureCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    /** A real but different binding, used to prove the exact-binding fail-closed check. */
    private static ProviderBindingPin foreignBinding() {
        return new ProviderBindingPin(
                com.example.platform.execution.domain.provider.ProviderId.of("not-ffmpeg"),
                com.example.platform.execution.domain.provider.ProviderImplementationId.of("not-ffmpeg.v1"),
                com.example.platform.execution.domain.provider.ProviderVersion.of("1.0.0"),
                com.example.platform.execution.domain.provider.ProviderExecutionContractVersion.of(1, 0),
                com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest
                        .version(com.example.platform.execution.domain.provider
                                .ProviderCapabilityProfileVersion.of(1, 0)),
                List.of());
    }

    private static List<String> arguments(FfmpegCpuRenderPlan plan) {
        RuntimeExecutionContext context = new RuntimeExecutionContext(
                plan.executableTaskId(), FfmpegCpuProvider.BINDING,
                ExecutionAttemptId.of("attempt-capability"), ExecutionOwnershipGeneration.first());
        ProcessInvocationSpec invocation =
                (ProcessInvocationSpec) new FfmpegCpuRenderAdapter(Path.of("/usr/bin/ffmpeg"))
                        .adapt(plan, context)
                        .commands().getFirst().invocationSpec();
        assertThat(invocation.executable()).isEqualTo("/usr/bin/ffmpeg");
        int filterIndex = invocation.arguments().indexOf("-filter_complex");
        for (int i = 0; i < invocation.arguments().size(); i++) {
            String value = invocation.arguments().get(i);
            // `-filter_complex` legitimately uses ';' as a graph separator; every other argv
            // element must stay free of shell metacharacters.
            if (i != filterIndex + 1) {
                assertThat(value).doesNotContain(";");
            }
            assertThat(value).doesNotContain("&&");
        }
        return invocation.arguments();
    }

    private static ExecutableTask task(
            String operation, List<String> capabilityIds, RenderSampleWindow window, int membershipCount) {
        return task(operation, capabilityIds, window, membershipCount, 1);
    }

    private static ExecutableTask task(
            String operation,
            List<String> capabilityIds,
            RenderSampleWindow window,
            int membershipCount,
            int outputCount) {
        ExecutableTask task = mock(ExecutableTask.class);
        List<ExecutableTaskMembership> memberships = java.util.stream.IntStream.range(0, membershipCount)
                .mapToObj(index -> membership(operation, capabilityIds, window))
                .toList();
        List<ExecutableInputProjection> runtimeInputs = List.of(inputProjection());
        List<ExecutionOutputId> outputs = java.util.stream.IntStream.range(0, outputCount)
                .mapToObj(index -> new ExecutionOutputId(index == 0 ? "output-media" : "output-" + index))
                .toList();
        when(task.id()).thenReturn(new ExecutableTaskId("c".repeat(64)));
        when(task.providerBindingPin()).thenReturn(FfmpegCpuProvider.BINDING);
        when(task.memberships()).thenReturn(memberships);
        when(task.requiredRuntimeInputs()).thenReturn(runtimeInputs);
        when(task.authoritativeOutputIds()).thenReturn(outputs);
        return task;
    }

    private static ExecutableTaskMembership membership(
            String operation, List<String> capabilityIds, RenderSampleWindow window) {
        ExecutableTaskMembership membership = mock(ExecutableTaskMembership.class);
        PhysicalPlanUnit unit = mock(PhysicalPlanUnit.class);
        when(unit.operationKey()).thenReturn(operation);
        when(unit.typedInputs()).thenReturn(List.of(mock(InputBinding.class)));
        when(unit.typedOutputs()).thenReturn(List.of(mock(OutputDeclaration.class)));
        when(unit.capabilityRequirementRefs()).thenReturn(capabilityIds.stream()
                .map(FfmpegCapabilityAwareLoweringTest::capabilityRef)
                .toList());
        when(unit.executionIntentRefs()).thenReturn(List.of());
        when(unit.temporalWindow()).thenReturn(window);
        when(unit.propagatedExtent()).thenReturn(null);
        when(unit.executionCoverage()).thenReturn(null);
        when(membership.physicalPlanUnit()).thenReturn(unit);
        return membership;
    }

    private static CapabilityRequirementRef capabilityRef(String capabilityId) {
        return new CapabilityRequirementRef(CapabilityRequirement.of(
                CapabilityId.of(capabilityId),
                ContractVersionRange.exactly(ContractVersion.of(1, 0))));
    }

    private static ExecutableInputProjection inputProjection() {
        ExecutableInputProjection projection = mock(ExecutableInputProjection.class);
        when(projection.inputId()).thenReturn(new ExecutionInputId("input-media"));
        return projection;
    }

    @Test
    void rasterLowersTimedTextWithItsCapabilityAndDeclaredFont() {
        var requirement = timedTextRequirement("你好世界", FontRational.whole(1), FontRational.whole(2));
        ExecutableTask task = rasterTask(requirement);
        var lowerer = new FfmpegCpuRenderLowerer("/usr/share/fonts/truetype/NotoSansCJKsc-VF.otf");

        FfmpegCpuRenderPlan plan = lowerer.lower(task, CONTEXT);

        assertThat(plan.operation()).isEqualTo(FfmpegCpuRenderPlan.Operation.RASTER);
        assertThat(plan.rasterParameters()).isPresent();
        var raster = plan.rasterParameters().orElseThrow();
        assertThat(raster.text()).isEqualTo("你好世界");
        assertThat(raster.start()).isEqualTo(MediaTime.ofRational(1, 1));
        assertThat(raster.end()).isEqualTo(MediaTime.ofRational(3, 1));
        assertThat(raster.fontSize()).isEqualTo(24);
        assertThat(raster.fontFile()).isEqualTo("/usr/share/fonts/truetype/NotoSansCJKsc-VF.otf");

        List<String> arguments = arguments(plan);
        assertThat(arguments).contains("-vf", "-c:v", "qtrle", "-pix_fmt", "argb", "-t", "5", "-f", "mov",
                ProviderNativeOutputFiles.RENDER_OUTPUT);
        assertThat(arguments.get(arguments.indexOf("-vf") + 1))
                .contains("drawtext=fontfile=/usr/share/fonts/truetype/NotoSansCJKsc-VF.otf")
                .contains("text='你好世界'")
                .contains("fontsize=24")
                .contains("x=(w-text_w)/2")
                .contains("y=h-text_h-20")
                .contains("enable='gte(t,1)*lt(t,3)'");
    }

    @Test
    void realProviderRasterHasVisiblePixelsOnlyInsideAuthoredInterval() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("media.platform.ffmpeg.real-subtitle-e2e"),
                "requires an explicitly declared drawtext-capable Worker FFmpeg");
        Path binaryDirectory = Path.of(System.getProperty("media.platform.ffmpeg.binary-dir"));
        Path ffmpeg = binaryDirectory.resolve("ffmpeg").toAbsolutePath().normalize();
        Path font = Path.of("/usr/share/fonts/truetype/NotoSansCJKsc-VF.otf");
        ExecutableTask task = rasterTask(timedTextRequirement(
                "你好 Hello", FontRational.whole(1), FontRational.whole(2)));
        var context = new RuntimeExecutionContext(task.id(), FfmpegCpuProvider.BINDING,
                ExecutionAttemptId.of("attempt-real-subtitle"), ExecutionOwnershipGeneration.first());
        var binding = FfmpegCpuRuntimeBindingFactory.createRender(ffmpeg,
                FfmpegSandboxWorkspace.under(mediaWorkspace.resolve("worker")),
                Duration.ofSeconds(30), 1024 * 1024, SandboxCancellation.never(), font);
        Path movie = mediaWorkspace.resolve("provider-raster.mov");
        try (var output = binding.execute(task, context, List.of())) {
            Files.copy(output.content(), movie);
        }
        assertThat(Files.size(movie)).isGreaterThan(1024L);
        assertThat(alphaPixels(ffmpeg, movie, "0.5")).isZero();
        assertThat(alphaPixels(ffmpeg, movie, "2")).isGreaterThan(100);
        assertThat(alphaPixels(ffmpeg, movie, "4")).isZero();
    }

    private static long alphaPixels(Path ffmpeg, Path movie, String seconds) throws Exception {
        var process = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-loglevel", "error",
                "-ss", seconds, "-i", movie.toString(), "-frames:v", "1", "-f", "rawvideo",
                "-pix_fmt", "rgba", "pipe:1").redirectErrorStream(true).start();
        byte[] frame = process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).isZero();
        assertThat(frame).hasSize(320 * 240 * 4);
        long pixels = 0;
        for (int i = 3; i < frame.length; i += 4) if ((frame[i] & 255) != 0) pixels++;
        return pixels;
    }

    @Test
    void rasterFailsClosedWithoutADeclaredWorkerFont() {
        var requirement = timedTextRequirement("你好", FontRational.whole(0), FontRational.whole(2));
        ExecutableTask task = rasterTask(requirement);
        assertFailure(() -> LOWERER.lower(task, CONTEXT),
                ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS);
    }

    private static TimedTextMaterializationRequirement timedTextRequirement(
            String text, FontRational start, FontRational duration) {
        TextContent content = new TextContent(text);
        StyledText styled = new StyledText(
                content,
                List.of(),
                List.of(new TextStyleRun(TextRange.of(0, content.scalarCount()), sampleStyle())),
                new ParagraphStyle(ParagraphStyle.Alignment.START, ParagraphStyle.Justification.NONE,
                        LineHeight.ratio(FontRational.of(12, 10)),
                        ParagraphStyle.WrapPolicy.WRAP, ParagraphBaseDirection.AUTO,
                        ParagraphStyle.LineBreakPolicy.STANDARD));
        return new TimedTextMaterializationRequirement(
                new TextElementId("text-raster-test"),
                start, duration, styled,
                new TextFrame(FontRational.of(640, 1), null,
                        TextFrame.HorizontalAlignment.CENTER, TextFrame.VerticalAlignment.BOTTOM,
                        ParagraphStyle.WrapPolicy.WRAP, TextFrame.OverflowBehavior.CLIP),
                new FontFallbackPolicy(List.of(new FontFamilyName("Noto Sans CJK SC")),
                        List.of(), List.of(), List.of()),
                List.of());
    }

    private static TextStyle sampleStyle() {
        return new TextStyle(
                new FontSelectionIntent(List.of(new FontFamilyName("Noto Sans CJK SC")),
                        FontSelectionIntent.WeightIntent.NORMAL,
                        FontSelectionIntent.StretchIntent.NORMAL,
                        FontSelectionIntent.SlantIntent.NORMAL,
                        OpticalSizingIntent.disabled(),
                        List.of()),
                new FontSize(FontRational.of(24, 1)),
                FontRational.of(0, 1),
                OpenTypeFeatureIntent.empty());
    }

    private static ExecutableTask compositeTaskWithoutSourceWindow() {
        ExecutableTask task = mock(ExecutableTask.class);
        ExecutableTaskMembership membership = mock(ExecutableTaskMembership.class);
        PhysicalPlanUnit unit = mock(PhysicalPlanUnit.class);
        InputBinding input = mock(InputBinding.class);
        ExecutionInputId inputId = new ExecutionInputId("input-media");
        when(input.inputId()).thenReturn(inputId);
        when(input.dependencyVariant()).thenReturn(
                new com.example.platform.render.domain.renderplan.RenderDependency.TimedCompositeInput(
                        MediaTime.ZERO, MediaTime.ofRational(2, 1)));
        when(unit.operationKey()).thenReturn("composite");
        when(unit.typedInputs()).thenReturn(List.of(input));
        when(unit.typedOutputs()).thenReturn(List.of(mock(OutputDeclaration.class)));
        when(unit.capabilityRequirementRefs()).thenReturn(List.of(capabilityRef("render.composite")));
        when(unit.executionIntentRefs()).thenReturn(List.of());
        when(unit.temporalWindow()).thenReturn(null);
        when(unit.propagatedExtent()).thenReturn(new RenderExtent(
                MediaTime.ZERO, MediaTime.ofRational(2, 1), FrameRate.of(30, 1)));
        when(unit.executionCoverage()).thenReturn(null);
        when(membership.physicalPlanUnit()).thenReturn(unit);
        when(task.id()).thenReturn(new ExecutableTaskId("e".repeat(64)));
        when(task.providerBindingPin()).thenReturn(FfmpegCpuProvider.BINDING);
        when(task.memberships()).thenReturn(List.of(membership));
        ExecutableInputProjection runtime = mock(ExecutableInputProjection.class);
        when(runtime.inputId()).thenReturn(inputId);
        when(task.requiredRuntimeInputs()).thenReturn(List.of(runtime));
        when(task.authoritativeOutputIds()).thenReturn(List.of(new ExecutionOutputId("output-media")));
        return task;
    }

    private static ExecutableTask rasterTask(TimedTextMaterializationRequirement requirement) {
        ExecutableTask task = mock(ExecutableTask.class);
        ExecutableTaskMembership membership = mock(ExecutableTaskMembership.class);
        PhysicalPlanUnit unit = mock(PhysicalPlanUnit.class);
        when(unit.operationKey()).thenReturn("raster");
        when(unit.typedInputs()).thenReturn(List.of());
        when(unit.typedOutputs()).thenReturn(List.of(mock(OutputDeclaration.class)));
        when(unit.capabilityRequirementRefs()).thenReturn(List.of(capabilityRef("subtitle.rasterize")));
        when(unit.executionIntentRefs()).thenReturn(List.of());
        when(unit.temporalWindow()).thenReturn(null);
        when(unit.propagatedExtent()).thenReturn(new RenderExtent(
                MediaTime.ZERO, MediaTime.ofRational(5, 1), FrameRate.of(30, 1)));
        when(unit.executionCoverage()).thenReturn(new RenderExecutionCoverage(
                MediaTime.ofRational(1, 1), MediaTime.ofRational(3, 1), FrameRate.of(30, 1)));
        when(membership.physicalPlanUnit()).thenReturn(unit);
        when(membership.mandatoryMaterializationMarkers()).thenReturn(List.of(requirement));
        when(task.id()).thenReturn(new ExecutableTaskId("d".repeat(64)));
        when(task.providerBindingPin()).thenReturn(FfmpegCpuProvider.BINDING);
        when(task.memberships()).thenReturn(List.of(membership));
        when(task.requiredRuntimeInputs()).thenReturn(List.of());
        when(task.authoritativeOutputIds()).thenReturn(List.of(new ExecutionOutputId("output-media")));
        return task;
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
