package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact accepted Phase 15 lowerer/adapter pairing for one provider-native plan type. */
public final class ProviderNativeRuntimeBinding<P extends ProviderNativeExecutionPlan> {

    private final PlanLowerer<P> planLowerer;
    private final RuntimeAdapter<P> runtimeAdapter;
    private final RuntimeCommandExecutor commandExecutor;

    public ProviderNativeRuntimeBinding(
            PlanLowerer<P> planLowerer,
            RuntimeAdapter<P> runtimeAdapter,
            RuntimeCommandExecutor commandExecutor) {
        this.planLowerer = Objects.requireNonNull(planLowerer, "planLowerer");
        this.runtimeAdapter = Objects.requireNonNull(runtimeAdapter, "runtimeAdapter");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
    }

    public ProviderExecutionOutput execute(
            ExecutableTask task,
            RuntimeExecutionContext runtimeContext,
            List<MaterializedExecutionInput> runtimeLocalInputs) throws IOException {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(runtimeContext, "runtimeContext");
        Objects.requireNonNull(runtimeLocalInputs, "runtimeLocalInputs");
        if (!task.id().equals(runtimeContext.executableTaskId())
                || !task.providerBindingPin().equals(runtimeContext.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "runtime context must retain the exact executable task and provider binding");
        }
        validateRuntimeInputIdentities(task, runtimeLocalInputs);
        P nativePlan = planLowerer.lower(
                task, StaticProviderExecutionContext.fromBinding(task.providerBindingPin()));
        nativePlan.requireTaskAndBinding(
                task.id(),
                task.providerBindingPin(),
                ProviderNativeFailureCode.PROVIDER_BINDING_MISMATCH);
        RuntimeExecutionBundle bundle = runtimeAdapter.adapt(nativePlan, runtimeContext);
        return commandExecutor.execute(bundle, List.copyOf(runtimeLocalInputs));
    }

    private static void validateRuntimeInputIdentities(
            ExecutableTask task,
            List<MaterializedExecutionInput> runtimeLocalInputs) {
        Set<ExecutionInputId> knownInputIds = new HashSet<>();
        task.requiredRuntimeInputs().stream()
                .forEach(input -> {
                    if (!knownInputIds.add(input.inputId())) {
                        throw new IllegalArgumentException(
                                "executable task contains duplicate logical input identity");
                    }
                });
        Set<ExecutionInputId> suppliedInputIds = new HashSet<>();
        for (MaterializedExecutionInput input : runtimeLocalInputs) {
            Objects.requireNonNull(input, "runtimeLocalInputs element");
            if (!suppliedInputIds.add(input.inputId())) {
                throw new IllegalArgumentException("duplicate materialized runtime input identity");
            }
            if (!knownInputIds.contains(input.inputId())) {
                throw new IllegalArgumentException("unknown materialized runtime input identity");
            }
        }
        if (!suppliedInputIds.equals(knownInputIds)) {
            throw new IllegalArgumentException("required materialized runtime input identity is absent");
        }
    }
}
