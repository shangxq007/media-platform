package com.example.platform.bmf;

import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workerfabric.domain.providernative.PlanLowerer;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.util.Objects;

/** Admits only the canonical, unparameterized, single-input transcode unit. */
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
                    "BMF transcode requires one physical unit");
        }
        var unit = task.memberships().getFirst().physicalPlanUnit();
        if (!"transcode".equals(unit.operationKey())) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                    "BMF currently serves only transcode");
        }
        if (!unit.capabilityRequirementRefs().isEmpty() || !unit.executionIntentRefs().isEmpty()
                || unit.temporalWindow() != null || unit.propagatedExtent() != null
                || unit.executionCoverage() != null || unit.artifactCapabilityRequest() != null
                || unit.typedInputs().size() != 1 || task.requiredRuntimeInputs().size() != 1) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_EXECUTABLE_TASK_SEMANTICS,
                    "BMF transcode requires one unparameterized materialized input");
        }
        if (unit.typedOutputs().size() != 1 || task.authoritativeOutputIds().size() != 1) {
            throw failure(ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY,
                    "BMF transcode requires one authoritative output");
        }
        return new BmfCpuTranscodePlan(task.id(), task.providerBindingPin(),
                task.requiredRuntimeInputs().getFirst().inputId(),
                task.authoritativeOutputIds().getFirst());
    }

    private static ProviderNativeExecutionFailure failure(ProviderNativeFailureCode code, String message) {
        return new ProviderNativeExecutionFailure(code, message);
    }
}
