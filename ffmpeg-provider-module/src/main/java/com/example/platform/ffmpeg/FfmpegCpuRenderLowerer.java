package com.example.platform.ffmpeg;

import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.render.domain.renderplan.ExactTextTimelineTimeProjection;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.providernative.PlanLowerer;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure fail-closed capability-aware lowering for the bounded CPU ffmpeg slice.
 *
 * <p>Accepts the typed-chain node operations this provider genuinely serves
 * ({@code decode} / {@code composite} / {@code encode}) plus the capability-neutral
 * {@code transcode} slice, and rejects everything outside the bounded shape. A typed
 * operation must carry its own capability requirement, and every declared capability
 * requirement must be one this provider actually declares.</p>
 */
public final class FfmpegCpuRenderLowerer implements PlanLowerer<FfmpegCpuRenderPlan> {

    private static final Logger LOG = LoggerFactory.getLogger(FfmpegCpuRenderLowerer.class);

    private static final Set<String> DECLARED_CAPABILITIES = Set.of(
            FfmpegCpuProvider.COVER_CAPABILITY.value(),
            FfmpegCpuProvider.THUMBNAIL_CAPABILITY.value(),
            FfmpegCpuProvider.TRANSCODE_CAPABILITY.value(),
            FfmpegCpuProvider.VIDEO_DECODE_CAPABILITY.value(),
            FfmpegCpuProvider.COMPOSITE_CAPABILITY.value(),
            FfmpegCpuProvider.OUTPUT_CAPABILITY.value(),
            FfmpegCpuProvider.SUBTITLE_RASTERIZE_CAPABILITY.value());

    /**
     * Declared worker font execution dependency (absolute normalized path), or {@code null} when
     * the worker did not declare one. Subtitle rasterization fails closed without it; the font is
     * a worker environment dependency like the FFmpeg executable, never a request-supplied path.
     */
    private final String fontFile;

    public FfmpegCpuRenderLowerer() {
        this(null);
    }

    public FfmpegCpuRenderLowerer(String fontFile) {
        this.fontFile = fontFile == null || fontFile.isBlank() ? null : fontFile;
    }

    @Override
    public FfmpegCpuRenderPlan lower(
            ExecutableTask task, StaticProviderExecutionContext context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");
        if (!FfmpegCpuProvider.BINDING.equals(task.providerBindingPin())
                || !FfmpegCpuProvider.BINDING.equals(context.providerBindingPin())) {
            throw failure(ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH,
                    "FFmpeg CPU lowering requires its exact ProviderBindingPin");
        }
        if (task.memberships().size() != 1) {
            throw failure(ProviderNativeFailureCode.ILLEGAL_MULTI_MEMBERSHIP_LOWERING,
                    "FFmpeg CPU lowering requires exactly one PhysicalPlanUnit membership");
        }
        ExecutableTaskMembership membership = task.memberships().getFirst();
        var unit = membership.physicalPlanUnit();
        FfmpegCpuRenderPlan.Operation operation = operationOf(unit.operationKey());
        if (unit.propagatedExtent() != null && !unit.propagatedExtent().raster().equals(
                com.example.platform.render.domain.renderplan.RenderRaster.DEFAULT)) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "FFmpeg CPU requires the default formal output raster");
        }
        requireDeclaredCapabilities(unit.capabilityRequirementRefs(), operation);
        if (unit.typedOutputs().size() != 1 || task.authoritativeOutputIds().size() != 1) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY,
                    "FFmpeg CPU lowering requires one authoritative output",
                    Map.of("authoritativeOutputCount",
                            Integer.toString(task.authoritativeOutputIds().size())));
        }
        if (operation == FfmpegCpuRenderPlan.Operation.COMPOSITE && !unit.typedInputs().isEmpty()) {
            var extent = unit.propagatedExtent();
            if (extent == null || task.requiredRuntimeInputs().size() != unit.typedInputs().size()) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "timed composition requires an exact extent and all declared inputs");
            }
            var runtimeIds = task.requiredRuntimeInputs().stream()
                    .map(input -> input.inputId()).collect(java.util.stream.Collectors.toSet());
            var inputs = new java.util.ArrayList<FfmpegCpuRenderPlan.TimedInput>();
            for (var input : unit.typedInputs()) {
                MediaTime overlayStart;
                MediaTime overlayEnd;
                boolean textOverlay = false;
                if (input.dependencyVariant() instanceof
                        com.example.platform.render.domain.renderplan.RenderDependency.TimedCompositeInput timed) {
                    if (!runtimeIds.contains(input.inputId())
                            || timed.start().isLessThan(extent.start())
                            || timed.end().isGreaterThan(extent.end())) {
                        throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                                "each composition input requires exact in-extent timing");
                    }
                    overlayStart = timed.start();
                    overlayEnd = timed.end();
                } else if (input.dependencyVariant() instanceof
                        com.example.platform.render.domain.renderplan.RenderDependency.CompositeInput) {
                    // TIMED_TEXT raster streams carry their own visibility interval in the
                    // drawtext `enable` expression, so the composite overlay spans the whole
                    // propagated extent and the transparent stream gates the visible interval.
                    if (!runtimeIds.contains(input.inputId())) {
                        throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                                "each text overlay input requires a declared runtime input");
                    }
                    overlayStart = extent.start();
                    overlayEnd = extent.end();
                    textOverlay = true;
                } else {
                    throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                            "each composition input requires a typed composite dependency");
                }
                inputs.add(new FfmpegCpuRenderPlan.TimedInput(input.inputId(),
                        overlayStart.subtract(extent.start()), overlayEnd.subtract(extent.start()), textOverlay));
            }
            inputs.sort(java.util.Comparator.comparing(FfmpegCpuRenderPlan.TimedInput::overlay)
                    .thenComparing(FfmpegCpuRenderPlan.TimedInput::start)
                    .thenComparing(input -> input.inputId().value()));
            FfmpegCpuRenderPlan.TimedInput previousBase = null;
            for (var input : inputs) {
                if (input.overlay()) continue;
                if (previousBase != null && previousBase.end().isGreaterThan(input.start())) {
                    throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                            "overlapping base video inputs are not supported");
                }
                previousBase = input;
            }
            LOG.info("FFMPEG_COMPOSITE_PLAN task={} extent={}..{} frameRate={} inputs={} duration={}",
                    task.id().sha256Hex(), extent.start(), extent.end(), extent.frameRate(),
                    inputs, extent.end().subtract(extent.start()));
            return new FfmpegCpuRenderPlan(task.id(), task.providerBindingPin(), operation,
                    inputs.getFirst().inputId(), task.authoritativeOutputIds().getFirst(),
                    Optional.empty(), Optional.empty(), inputs,
                    Optional.of(extent.end().subtract(extent.start())),
                    Optional.of(extent.frameRate()), Optional.empty());
        }
        if (operation == FfmpegCpuRenderPlan.Operation.RASTER) {
            return rasterPlan(task, membership, unit);
        }
        if (unit.typedInputs().size() != 1 || task.requiredRuntimeInputs().size() != 1) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "FFmpeg CPU lowering requires exact materialized runtime inputs");
        }
        var artifactRequest = unit.artifactCapabilityRequest();
        if (operation == FfmpegCpuRenderPlan.Operation.FRAME_EXTRACT) {
            if (artifactRequest == null || unit.typedInputs().getFirst().sourceArtifact() == null
                    || !artifactRequest.source().artifactId().equals(unit.typedInputs().getFirst().sourceArtifact().artifactId())
                    || !artifactRequest.source().contentDigest().matches(unit.typedInputs().getFirst().sourceArtifact().contentDigest())
                    || unit.capabilityRequirementRefs().size() != 1
                    || !artifactRequest.capabilityId().equals(unit.capabilityRequirementRefs().getFirst().declaration().capabilityId().value())
                    || unit.temporalWindow() == null || !unit.temporalWindow().isFreezePoint()
                    || !unit.temporalWindow().start().equals(artifactRequest.parameters().position())
                    || unit.typedOutputs().getFirst().finalArtifactExpectations().size() != 1
                    || unit.typedOutputs().getFirst().finalArtifactExpectations().getFirst().role()
                            != com.example.platform.render.domain.renderplan.RenderOutputRole.DERIVED_MEDIA) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "frame extraction requires one pinned Artifact and typed image output contract");
            }
        } else if (artifactRequest != null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "Artifact capability parameters cannot change a Render operation");
        }
        Optional<FfmpegCpuRenderPlan.SourceWindow> window = operation == FfmpegCpuRenderPlan.Operation.FRAME_EXTRACT
                ? Optional.empty() : sourceWindow(unit.temporalWindow(), operation);
        var extent = unit.propagatedExtent();
        return new FfmpegCpuRenderPlan(
                task.id(),
                task.providerBindingPin(),
                operation,
                task.requiredRuntimeInputs().getFirst().inputId(),
                task.authoritativeOutputIds().getFirst(),
                window, artifactRequest == null ? Optional.empty() : Optional.of(artifactRequest.parameters()),
                java.util.List.of(), Optional.empty(),
                extent == null ? Optional.empty() : Optional.of(extent.frameRate()),
                Optional.empty());
    }

    private static FfmpegCpuRenderPlan.Operation operationOf(String operationKey) {
        return switch (operationKey) {
            case "frame-extract" -> FfmpegCpuRenderPlan.Operation.FRAME_EXTRACT;
            case "transcode" -> FfmpegCpuRenderPlan.Operation.TRANSCODE;
            case "decode" -> FfmpegCpuRenderPlan.Operation.DECODE;
            case "composite" -> FfmpegCpuRenderPlan.Operation.COMPOSITE;
            case "encode" -> FfmpegCpuRenderPlan.Operation.ENCODE;
            case "raster" -> FfmpegCpuRenderPlan.Operation.RASTER;
            default -> throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                    "FFmpeg CPU provider does not lower operation: " + operationKey);
        };
    }

    private static void requireDeclaredCapabilities(
            java.util.List<CapabilityRequirementRef> references,
            FfmpegCpuRenderPlan.Operation operation) {
        for (CapabilityRequirementRef reference : references) {
            String capabilityId = reference.declaration().capabilityId().value();
            if (!DECLARED_CAPABILITIES.contains(capabilityId)) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                        "FFmpeg CPU provider does not declare capability: " + capabilityId);
            }
        }
        String required = switch (operation) {
            case DECODE -> FfmpegCpuProvider.VIDEO_DECODE_CAPABILITY.value();
            case COMPOSITE -> FfmpegCpuProvider.COMPOSITE_CAPABILITY.value();
            case ENCODE -> FfmpegCpuProvider.OUTPUT_CAPABILITY.value();
            case RASTER -> FfmpegCpuProvider.SUBTITLE_RASTERIZE_CAPABILITY.value();
            case TRANSCODE, FRAME_EXTRACT -> null;
        };
        if (required != null && references.stream()
                .noneMatch(reference -> required.equals(
                        reference.declaration().capabilityId().value()))) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "FFmpeg CPU " + operation + " lowering requires its own capability requirement");
        }
    }

    private FfmpegCpuRenderPlan rasterPlan(
            ExecutableTask task, ExecutableTaskMembership membership,
            com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit unit) {
        if (fontFile == null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization requires a declared worker font");
        }
        if (!unit.typedInputs().isEmpty() || !task.requiredRuntimeInputs().isEmpty()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization is input-free (the font is a declared worker dependency)");
        }
        TimedTextMaterializationRequirement text = null;
        for (var marker : membership.mandatoryMaterializationMarkers()) {
            if (marker instanceof TimedTextMaterializationRequirement candidate) {
                if (text != null) {
                    throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                            "subtitle rasterization requires exactly one timed-text requirement");
                }
                text = candidate;
            }
        }
        if (text == null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization requires its typed TIMED_TEXT materialization requirement");
        }
        if (unit.capabilityRequirementRefs().size() != 1
                || !unit.capabilityRequirementRefs().getFirst().declaration().capabilityId().value()
                        .equals(FfmpegCpuProvider.SUBTITLE_RASTERIZE_CAPABILITY.value())) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization requires exactly the subtitle.rasterize capability");
        }
        var extent = unit.propagatedExtent();
        if (extent == null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization requires an exact propagated extent");
        }
        if (unit.executionCoverage() == null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization requires exact execution coverage");
        }
        var startResult = ExactTextTimelineTimeProjection.project(text.start());
        var endResult = ExactTextTimelineTimeProjection.projectEnd(text.start(), text.duration());
        if (!startResult.exact() || !endResult.exact()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization timing must project exactly");
        }
        var start = ((ExactTextTimelineTimeProjection.Projected) startResult).mediaTime();
        var end = ((ExactTextTimelineTimeProjection.Projected) endResult).mediaTime();
        if (start.isLessThan(extent.start()) || end.isGreaterThan(extent.end())) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization interval must be inside the propagated extent");
        }
        StyledText styledText = text.styledText();
        String content = styledText.content().value();
        if (content.isEmpty()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization requires non-empty text content");
        }
        if (styledText.styleRuns().isEmpty()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "subtitle rasterization requires at least one style run");
        }
        var fontSizeValue = styledText.styleRuns().getFirst().style().fontSize().value();
        int fontSize = (int) Math.round(fontSizeValue.numerator().doubleValue()
                / fontSizeValue.denominator().doubleValue());
        String x = switch (text.frame().horizontalAlignment()) {
            case START -> "20";
            case CENTER -> "(w-text_w)/2";
            case END -> "w-text_w-20";
        };
        String y = switch (text.frame().verticalAlignment()) {
            case TOP -> "20";
            case CENTER -> "(h-text_h)/2";
            case BOTTOM -> "h-text_h-20";
        };
        var parameters = new FfmpegCpuRenderPlan.RasterParameters(
                content, start.subtract(extent.start()), end.subtract(extent.start()),
                extent.end().subtract(extent.start()), fontSize, fontFile, x, y);
        LOG.info("FFMPEG_RASTER_PLAN task={} start={} end={} fontSize={} contentLength={}",
                task.id().sha256Hex(), parameters.start(), parameters.end(), fontSize, content.length());
        return new FfmpegCpuRenderPlan(task.id(), task.providerBindingPin(),
                FfmpegCpuRenderPlan.Operation.RASTER, null, task.authoritativeOutputIds().getFirst(),
                Optional.empty(), Optional.empty(), java.util.List.of(), Optional.empty(),
                Optional.of(extent.frameRate()), Optional.of(parameters));
    }

    private static Optional<FfmpegCpuRenderPlan.SourceWindow> sourceWindow(
            RenderSampleWindow window, FfmpegCpuRenderPlan.Operation operation) {
        if (window == null) {
            return Optional.empty();
        }
        if (operation != FfmpegCpuRenderPlan.Operation.DECODE) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "only the decode operation carries an authored source sample window");
        }
        if (window.isFreezePoint()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "bounded V1 decode does not serve zero-length freeze windows");
        }
        return Optional.of(new FfmpegCpuRenderPlan.SourceWindow(window.start(), window.end()));
    }

    private static ProviderNativeExecutionFailure failure(
            ProviderNativeFailureCode code, String message) {
        return new ProviderNativeExecutionFailure(code, message);
    }
}
