package com.example.platform.bmf;

import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.workerfabric.domain.providernative.PlanLowerer;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lowers single-input video and audio units and validates the exact timed inputs of composition.
 *
 * <p>The existing concatenation operation requires continuous video. Multitrack composition accepts
 * gaps and overlaps between distinct tracks. Both forms reject unsupported media dependencies and
 * authored execution intents before runtime.
 */
public final class BmfCpuTranscodeLowerer implements PlanLowerer<BmfCpuTranscodePlan> {
    @Override
    public BmfCpuTranscodePlan lower(ExecutableTask task, StaticProviderExecutionContext context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");
        if (!BmfCpuProvider.recognizes(task.providerBindingPin())
                || !task.providerBindingPin().equals(context.providerBindingPin())) {
            throw failure(ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH,
                    "BMF lowering requires its exact provider binding");
        }
        if (task.memberships().size() != 1) {
            throw failure(ProviderNativeFailureCode.ILLEGAL_MULTI_MEMBERSHIP_LOWERING,
                    "BMF requires one physical unit");
        }
        var membership = task.memberships().getFirst();
        var unit = membership.physicalPlanUnit();
        BmfOperation operation;
        try {
            operation = BmfOperation.ofOperationKey(unit.operationKey());
        } catch (IllegalArgumentException unsupported) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                    "BMF does not lower operation: " + unit.operationKey());
        }
        requireDeclaredCapabilities(unit.capabilityRequirementRefs(), operation);
        if (unit.typedOutputs().size() != 1 || task.authoritativeOutputIds().size() != 1) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY,
                    "BMF requires one authoritative output");
        }
        if (!unit.executionIntentRefs().isEmpty() || unit.artifactCapabilityRequest() != null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF does not accept authored execution intents or artifact capability parameters");
        }
        if (operation == BmfOperation.MULTITRACK_COMPOSITE) {
            return multitrackPlan(task, membership, unit);
        }
        if (operation == BmfOperation.COMPOSITE && isMultiInputComposite(unit)) {
            return compositePlan(task, unit);
        }
        if (unit.typedInputs().size() != 1 || task.requiredRuntimeInputs().size() != 1) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF requires exactly one materialized input");
        }
        if (operation == BmfOperation.COMPOSITE) {
            for (var input : unit.typedInputs()) {
                if (input.dependencyVariant() instanceof RenderDependency.CompositeInput) {
                    throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                            "BMF V1 does not compose timed text");
                }
            }
        }
        boolean decode = operation == BmfOperation.DECODE || operation == BmfOperation.AUDIO_DECODE;
        if (!decode && unit.temporalWindow() != null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "only a BMF decode unit may carry an authored source window");
        }
        Optional<BmfCpuTranscodePlan.SourceWindow> window = decode
                ? sourceWindow(unit.temporalWindow())
                : Optional.empty();
        var output = Optional.ofNullable(unit.propagatedExtent()).filter(extent ->
                operation == BmfOperation.ENCODE || operation == BmfOperation.MULTITRACK_ENCODE);
        return new BmfCpuTranscodePlan(task.id(), task.providerBindingPin(), operation,
                task.requiredRuntimeInputs().getFirst().inputId(),
                task.authoritativeOutputIds().getFirst(), window, List.of(),
                output.map(extent -> extent.end().subtract(extent.start())), output.map(extent -> extent.frameRate()),
                unit.propagatedExtent() == null ? com.example.platform.render.domain.renderplan.RenderRaster.DEFAULT
                        : unit.propagatedExtent().raster());
    }

    private static BmfCpuTranscodePlan multitrackPlan(ExecutableTask task, ExecutableTaskMembership membership,
            com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit unit) {
        var extent = unit.propagatedExtent();
        var runtimeIds = task.requiredRuntimeInputs().stream().map(input -> input.inputId())
                .collect(java.util.stream.Collectors.toSet());
        if (extent == null || unit.typedInputs().isEmpty()
                || task.requiredRuntimeInputs().size() != unit.typedInputs().size()
                || runtimeIds.size() != unit.typedInputs().size()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "multitrack composition requires every timed input and an exact extent");
        }
        var inputs = new java.util.ArrayList<BmfCpuTranscodePlan.TimedInput>();
        for (var input : unit.typedInputs()) {
            if (!(input.dependencyVariant() instanceof RenderDependency.TimedTrackInput timed)
                    || !runtimeIds.contains(input.inputId()) || timed.start().isLessThan(extent.start())
                    || timed.end().isGreaterThan(extent.end())) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "BMF multitrack composition requires exact audio or video placement");
            }
            var placement = placement(timed.placement());
            if (!timed.audio() && !BmfCpuTranscodePlan.VideoPlacement.fitsIntermediateRaster(
                    extent.raster(), placement.scaleX(), placement.scaleY(), placement.degrees())) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "BMF transformed composition input exceeds its intermediate raster budget");
            }
            inputs.add(new BmfCpuTranscodePlan.TimedInput(input.inputId(), timed.start().subtract(extent.start()),
                    timed.end().subtract(extent.start()), timed.layer(), timed.audio(),
                    timed.gainLinear(), timed.muted(), timed.balanceValue(), placement,
                    timed.effect(), timed.start().subtract(timed.authoredStart()),
                    timed.authoredEnd().subtract(timed.authoredStart()),
                    timed.dissolve().map(RenderDependency.Dissolve::duration).orElse(MediaTime.ZERO),
                    timed.opacityKeys().map(k -> k.relativeTo(timed.authoredStart())),
                    timed.positionKeys().map(p -> p.relativeTo(timed.authoredStart())),
                    timed.scaleKeys().map(k -> k.relativeTo(timed.authoredStart())),
                    timed.gainKeys().map(k -> k.relativeTo(timed.authoredStart())), timed.fades(),
                    timed.dissolve().map(RenderDependency.Dissolve::wipeLeftToRight).orElse(false)));
        }
        inputs.sort(java.util.Comparator.comparingInt(BmfCpuTranscodePlan.TimedInput::layer)
                .thenComparing(BmfCpuTranscodePlan.TimedInput::start));
        for (int index = 1; index < inputs.size(); index++) {
            var previous = inputs.get(index - 1);
            var next = inputs.get(index);
            if (previous.layer() == next.layer() && previous.audio() == next.audio()
                    && previous.end().isGreaterThan(next.start())) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "BMF rejects overlapping clips of the same stream type on one track");
            }
        }
        var cues = new java.util.ArrayList<com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement.CueRaster>();
        try {
            for (var marker : membership.mandatoryMaterializationMarkers()) {
                if (!(marker instanceof com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement cue)
                        || !cue.editableCue()) throw new IllegalArgumentException("Unsupported multitrack materialization");
                cue.cueRaster(extent).ifPresent(cues::add);
            }
        } catch (ArithmeticException | IllegalArgumentException invalid) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS, invalid.getMessage());
        }
        return new BmfCpuTranscodePlan(task.id(), task.providerBindingPin(), BmfOperation.MULTITRACK_COMPOSITE,
                inputs.getFirst().inputId(), task.authoritativeOutputIds().getFirst(), Optional.empty(),
                List.copyOf(inputs), Optional.of(extent.end().subtract(extent.start())), Optional.of(extent.frameRate()), extent.raster(), cues);
    }

    private static BmfCpuTranscodePlan.VideoPlacement placement(
            RenderDependency.VideoPlacement transform) {
        return new BmfCpuTranscodePlan.VideoPlacement(transform.x(), transform.y(), transform.scaleX(),
                transform.scaleY(), transform.degrees(), transform.left(), transform.right(),
                transform.top(), transform.bottom(), transform.opacity());
    }

    private static boolean isMultiInputComposite(
            com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit unit) {
        // A single timed input keeps the established single-input graph; only a real multi-input
        // plan takes the concatenating composite program (the single-input E2E parses that argv).
        return unit.typedInputs().size() > 1;
    }

    /**
     * Multi-input composite: every input must be a timed base video, the inputs must be contiguous
     * and cover the whole propagated extent. BMF V1 concatenates them, so a gap must fail closed
     * rather than being silently compressed.
     */
    private static BmfCpuTranscodePlan compositePlan(
            ExecutableTask task,
            com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit unit) {
        var extent = unit.propagatedExtent();
        if (extent == null || !extent.end().isGreaterThan(extent.start())) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF multi-input composition requires an exact propagated extent");
        }
        var runtimeIds = task.requiredRuntimeInputs().stream()
                .map(input -> input.inputId()).collect(java.util.stream.Collectors.toSet());
        if (runtimeIds.size() != unit.typedInputs().size()
                || task.requiredRuntimeInputs().size() != unit.typedInputs().size()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF multi-input composition requires every declared input");
        }
        var timed = new java.util.ArrayList<BmfCpuTranscodePlan.TimedInput>();
        for (var input : unit.typedInputs()) {
            if (input.dependencyVariant() instanceof RenderDependency.CompositeInput) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "BMF V1 does not compose timed text");
            }
            if (!(input.dependencyVariant() instanceof RenderDependency.TimedCompositeInput window)) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "BMF multi-input composition requires timed composite dependencies");
            }
            if (!runtimeIds.contains(input.inputId())
                    || window.start().isLessThan(extent.start())
                    || window.end().isGreaterThan(extent.end())
                    || !window.end().isGreaterThan(window.start())) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "each composition input requires exact in-extent timing");
            }
            timed.add(new BmfCpuTranscodePlan.TimedInput(input.inputId(),
                    window.start().subtract(extent.start()), window.end().subtract(extent.start())));
        }
        timed.sort(java.util.Comparator.comparing(BmfCpuTranscodePlan.TimedInput::start)
                .thenComparing(input -> input.inputId().value()));
        var previous = timed.getFirst();
        if (!previous.start().equals(MediaTime.ZERO)) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF composition cannot express a timeline gap before the first input");
        }
        for (int index = 1; index < timed.size(); index++) {
            var current = timed.get(index);
            if (!previous.end().equals(current.start())) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                        "BMF composition requires contiguous non-overlapping inputs (no timeline gap)");
            }
            previous = current;
        }
        MediaTime duration = extent.end().subtract(extent.start());
        if (!previous.end().equals(duration)) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF composition requires the inputs to cover the whole extent");
        }
        return new BmfCpuTranscodePlan(task.id(), task.providerBindingPin(), BmfOperation.COMPOSITE,
                timed.getFirst().inputId(), task.authoritativeOutputIds().getFirst(),
                Optional.empty(), java.util.List.copyOf(timed), Optional.of(duration),
                Optional.of(extent.frameRate()), extent.raster());
    }

    private static Optional<BmfCpuTranscodePlan.SourceWindow> sourceWindow(
            com.example.platform.render.domain.renderplan.RenderSampleWindow window) {
        if (window == null) {
            return Optional.empty();
        }
        if (window.isFreezePoint()) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "bounded V1 decode does not serve zero-length freeze windows");
        }
        return Optional.of(new BmfCpuTranscodePlan.SourceWindow(
                window.start(), window.end().subtract(window.start())));
    }

    private static void requireDeclaredCapabilities(List<CapabilityRequirementRef> references,
            BmfOperation operation) {
        for (CapabilityRequirementRef reference : references) {
            String capabilityId = reference.declaration().capabilityId().value();
            if (!BmfOperation.declaresCapability(capabilityId)) {
                throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                        "BMF does not declare capability: " + capabilityId);
            }
        }
        if (references.stream().noneMatch(reference -> operation.capabilityId()
                .equals(reference.declaration().capabilityId().value()))) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF " + operation.operationKey() + " lowering requires its own capability requirement");
        }
    }

    private static ProviderNativeExecutionFailure failure(
            ProviderNativeFailureCode code, String message) {
        return new ProviderNativeExecutionFailure(code, message);
    }
}
