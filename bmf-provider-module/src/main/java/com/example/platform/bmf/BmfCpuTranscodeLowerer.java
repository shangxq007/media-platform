package com.example.platform.bmf;

import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.workerfabric.domain.providernative.PlanLowerer;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Admits the bounded BMF render units: one materialized video input, one authoritative output.
 *
 * <p>Supported operation keys are {@code decode}, {@code composite}, {@code encode} and
 * {@code transcode} ({@link BmfOperation}). Anything else fails before a runtime process starts.
 * The bounded V1 does not compose timed text, multiple base inputs, audio or authored execution
 * intents; those cases are rejected instead of being silently ignored.
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
