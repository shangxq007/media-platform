package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.ProviderResourceProfile;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.RuntimeDependencyRequirement;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import java.util.List;
import java.util.Optional;
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

    /**
     * Declared resource footprint for one task execution.
     *
     * <p>Distinct from {@link com.example.platform.workerfabric.domain.ProviderHardwareRequirement},
     * which describes capability needs (CPU architecture, build/codec features, sandbox permissions);
     * this carries only the amounts the runtime consumes (CPU millicores, memory bytes,
     * temporary-storage bytes, device demands).
     *
     * <p>Default is {@link Optional#empty()}: a contributor that declares nothing has no footprint, and
     * the demand derivation fails closed instead of inventing one.
     */
    default Optional<ProviderResourceProfile> resourceProfile() {
        return Optional.empty();
    }

    /**
     * Declared capability needs (CPU architecture, provider build/codec features, sandbox
     * permissions). Distinct from {@link ProviderResourceProfile}, which carries amounts.
     *
     * <p>Default empty: an undeclared contributor produces no hardware requirement, and the
     * scheduling chain fails closed instead of assuming one.
     */
    default Optional<ProviderHardwareRequirement> providerHardwareRequirement() {
        return Optional.empty();
    }

    /**
     * Declared external runtime dependencies (libraries, ABIs, features).
     *
     * <p>Default empty: a contributor with no declared dependency. An undeclared dependency is never
     * assumed present.
     */
    default List<RuntimeDependencyRequirement> runtimeDependencyRequirements() {
        return List.of();
    }

    /**
     * Whether this contribution requires sandboxed runtime mechanics.
     *
     * <p>Default {@link SandboxRuntimeRequirement#REQUIRED}: fail closed — a contributor that says
     * nothing is assumed to need a sandbox rather than being granted unsandboxed execution.
     */
    default SandboxRuntimeRequirement sandboxRequirement() {
        return SandboxRuntimeRequirement.REQUIRED;
    }

    WorkerRuntimeSupportRequirement workerRuntimeSupportRequirement();

    ProviderBindingPin providerBindingPin();

    ProviderNativeRuntimeBinding<?> createRuntimeBinding(ProviderPluginRuntimeContext context);
}
