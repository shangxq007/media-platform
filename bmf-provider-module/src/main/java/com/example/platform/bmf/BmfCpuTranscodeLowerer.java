package com.example.platform.bmf;

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
 * Admits the bounded BMF render units: one materialized video input or a contiguous multi-input
 * composite, one authoritative output.
 *
 * <p>Supported operation keys are {@code decode}, {@code composite}, {@code encode} and
 * {@code transcode} ({@link BmfOperation}). A COMPOSITE unit with several timed base inputs is
 * lowered only when the inputs are contiguous and cover the whole propagated extent; a gap, an
 * overlap or a timed-text input is rejected so BMF never silently compresses the timeline. Audio and
 * authored execution intents remain unsupported.
 */
public final class BmfCpuTranscodeLowerer implements PlanLowerer<BmfCpuTranscodePlan> {
    @Override
    public BmfCpuTranscodePlan lower(ExecutableTask task, StaticProviderExecutionContext context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");
        if (!BmfCpuProvider.BINDING.equals(task.providerBindingPin())
                || !BmfCpuProvider.BINDING.equals(context.providerBindingPin())) {
            throw failure(ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH,
                    "BMF lowering requires its exact provider binding");
        }
        if (task.memberships().size() != 1) {
            throw failure(ProviderNativeFailureCode.ILLEGAL_MULTI_MEMBERSHIP_LOWERING,
                    "BMF requires one physical unit");
        }
        var unit = task.memberships().getFirst().physicalPlanUnit();
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
        if (operation != BmfOperation.DECODE && unit.temporalWindow() != null) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "only a BMF decode unit may carry an authored source window");
        }
        Optional<BmfCpuTranscodePlan.SourceWindow> window = operation == BmfOperation.DECODE
                ? sourceWindow(unit.temporalWindow())
                : Optional.empty();
        return new BmfCpuTranscodePlan(task.id(), task.providerBindingPin(), operation,
                task.requiredRuntimeInputs().getFirst().inputId(),
                task.authoritativeOutputIds().getFirst(), window);
    }

    private static boolean isMultiInputComposite(
            com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit unit) {
        if (unit.typedInputs().size() > 1) {
            return true;
        }
        return unit.typedInputs().stream().anyMatch(input ->
                input.dependencyVariant() instanceof RenderDependency.TimedCompositeInput);
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
                Optional.of(extent.frameRate()));
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
