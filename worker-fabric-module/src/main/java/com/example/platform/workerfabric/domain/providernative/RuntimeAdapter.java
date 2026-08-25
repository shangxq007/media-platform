package com.example.platform.workerfabric.domain.providernative;

/**
 * Runtime SPI translating a provider-native plan into typed runtime commands/provider requests.
 *
 * <p>Adapters must not choose another provider, rebind ProviderBindingPin, create platform attempts,
 * own lease/retry lifecycle, write canonical domain state, commit Artifact authority, or decide
 * completion.
 */
@FunctionalInterface
public interface RuntimeAdapter<P extends ProviderNativeExecutionPlan> {

    RuntimeExecutionBundle adapt(P nativePlan, RuntimeExecutionContext context)
            throws ProviderNativeExecutionFailure;
}
