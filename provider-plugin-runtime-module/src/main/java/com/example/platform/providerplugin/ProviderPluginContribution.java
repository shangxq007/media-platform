package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import org.pf4j.ExtensionPoint;

/**
 * Typed PF4J contribution for one provider-native implementation.
 *
 * <p>This is metadata plus a bounded binding factory. It is not the JSON
 * extension runtime, does not execute by itself, and owns no task lifecycle or
 * artifact commit/completion authority.</p>
 */
public interface ProviderPluginContribution extends ExtensionPoint {

    String pluginId();

    String pluginVersion();

    PluginDescriptor pluginDescriptor();

    ProviderDescriptor providerDescriptor();

    ProviderExecutionContract providerExecutionContract();

    ProviderCapabilityProfile providerCapabilityProfile();

    WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement();

    ProviderBindingPin providerBindingPin();

    ProviderNativeRuntimeBinding<?> createRuntimeBinding(ProviderPluginRuntimeContext context);
}
