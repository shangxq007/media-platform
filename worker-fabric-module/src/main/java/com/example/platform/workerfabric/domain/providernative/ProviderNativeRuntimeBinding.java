package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workerfabric.reuse.MaterializedArtifact;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Exact accepted Phase 15 lowerer/adapter pairing for one provider-native plan type. */
public final class ProviderNativeRuntimeBinding<P extends ProviderNativeExecutionPlan> {

    private final PlanLowerer<P> planLowerer;
    private final RuntimeAdapter<P> runtimeAdapter;

    public ProviderNativeRuntimeBinding(
            PlanLowerer<P> planLowerer,
            RuntimeAdapter<P> runtimeAdapter) {
        this.planLowerer = Objects.requireNonNull(planLowerer, "planLowerer");
        this.runtimeAdapter = Objects.requireNonNull(runtimeAdapter, "runtimeAdapter");
    }

    public ProviderExecutionOutput execute(
            ExecutableTask task,
            RuntimeExecutionContext runtimeContext,
            List<MaterializedArtifact> runtimeLocalInputs) throws IOException {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(runtimeContext, "runtimeContext");
        Objects.requireNonNull(runtimeLocalInputs, "runtimeLocalInputs");
        if (!task.id().equals(runtimeContext.executableTaskId())
                || !task.providerBindingPin().equals(runtimeContext.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "runtime context must retain the exact executable task and provider binding");
        }
        P nativePlan = planLowerer.lower(
                task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin()));
        nativePlan.requireTaskAndBinding(
                task.id(),
                task.providerBindingPin(),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        RuntimeExecutionBundle bundle = runtimeAdapter.adapt(nativePlan, runtimeContext);
        return runtimeAdapter.execute(bundle, List.copyOf(runtimeLocalInputs));
    }
}
