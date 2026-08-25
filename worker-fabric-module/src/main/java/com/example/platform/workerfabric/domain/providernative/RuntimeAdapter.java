package com.example.platform.workerfabric.domain.providernative;

import com.example.platform.workerfabric.reuse.MaterializedExecutionInput;
import java.io.IOException;
import java.util.List;

/**
 * Runtime SPI translating and executing a provider-native plan through typed runtime mechanics.
 *
 * <p>Adapters must not choose another provider, rebind ProviderBindingPin, create platform attempts,
 * own lease/retry lifecycle, write canonical domain state, commit Artifact authority, or decide
 * completion. Runtime inputs are immutable worker-local handles; storage providers and locations
 * are deliberately absent.
 */
public interface RuntimeAdapter<P extends ProviderNativeExecutionPlan> {

    RuntimeExecutionBundle adapt(P nativePlan, RuntimeExecutionContext context)
            throws ProviderNativeExecutionFailure;

    ProviderExecutionOutput execute(
            RuntimeExecutionBundle executionBundle,
            List<MaterializedExecutionInput> runtimeLocalInputs) throws IOException;
}
