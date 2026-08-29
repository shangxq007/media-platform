package com.example.platform.bmf;

import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.RuntimeAdapter;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import java.util.Objects;

/** Fail-closed B1 adapter that emits no runtime commands. */
public final class BmfCpuUnsupportedRuntimeAdapter implements RuntimeAdapter<BmfCpuNativePlan> {

    @Override
    public RuntimeExecutionBundle adapt(
            BmfCpuNativePlan nativePlan, RuntimeExecutionContext context) {
        Objects.requireNonNull(nativePlan, "nativePlan");
        Objects.requireNonNull(context, "context");
        if (!BmfCpuProvider.BINDING.equals(nativePlan.providerBindingPin())
                || !BmfCpuProvider.BINDING.equals(context.providerBindingPin())
                || !nativePlan.executableTaskId().equals(context.executableTaskId())
                || !nativePlan.providerBindingPin().equals(context.providerBindingPin())) {
            throw new ProviderNativeExecutionFailure(
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH,
                    "BMF CPU runtime adapter requires the exact task and ProviderBindingPin");
        }
        throw new ProviderNativeExecutionFailure(
                ProviderNativeFailureCode.RUNTIME_ADAPTER_UNSUPPORTED_PLAN,
                "BMF CPU runtime adaptation is unsupported in B1");
    }
}
