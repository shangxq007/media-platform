package com.example.platform.bmf;

import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workerfabric.domain.providernative.PlanLowerer;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.StaticProviderExecutionContext;
import java.util.Objects;

/** Fail-closed B1 lowerer that admits no executable-task semantics. */
public final class BmfCpuUnsupportedLowerer implements PlanLowerer<BmfCpuNativePlan> {

    @Override
    public BmfCpuNativePlan lower(
            ExecutableTask task, StaticProviderExecutionContext context) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(context, "context");
        if (!BmfCpuProvider.BINDING.equals(task.providerBindingPin())
                || !BmfCpuProvider.BINDING.equals(context.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH,
                    "BMF CPU lowering requires its exact ProviderBindingPin");
        }
        throw new ProviderNativeExecutionFailure(
                ProviderNativeFailureCode.UNSUPPORTED_OPERATION_NATIVE_LOWERING,
                "BMF CPU native lowering is unsupported in B1");
    }
}
