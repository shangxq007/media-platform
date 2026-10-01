package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
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

    /**
     * Static Stage-1 compatibility declaration for this contribution.
     *
     * <p>Capability support is declared separately and solely by
     * {@link #providerCapabilityProfile()}; this declaration carries only the
     * non-capability support facts the Stage-1 compatibility kernel consumes
     * (artifact requirement kinds, codecs, device kinds, runtime classes,
     * sandbox modes, determinism classes, boundary contracts and lowering
     * support).</p>
     *
     * <p>Default is {@link ProviderStaticCompatibility#unknown()}: a contributor
     * that declares nothing produces no kernel proof, so no physical plan unit can
     * bind to it — fail closed, never an implicit "compatible".</p>
     */
    default ProviderStaticCompatibility providerStaticCompatibility() {
        return ProviderStaticCompatibility.unknown();
    }

    WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement();

    ProviderBindingPin providerBindingPin();

    ProviderNativeRuntimeBinding<?> createRuntimeBinding(ProviderPluginRuntimeContext context);
}
