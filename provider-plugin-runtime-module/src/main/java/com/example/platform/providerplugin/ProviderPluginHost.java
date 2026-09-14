package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.extension.api.port.PluginRegistries;
import com.example.platform.extension.api.port.PluginRegistrationException;
import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginDescriptorValidationIssue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;

/** Canonical PF4J host loader for typed provider contributions. */
public final class ProviderPluginHost implements AutoCloseable {

    private final PluginManager pluginManager;
    private final PluginRegistrationPort pluginRegistry;
    private final ProviderPluginCatalog catalog = new ProviderPluginCatalog();
    private boolean loaded;
    private final java.util.Map<String, PluginRegistrationPort.Registration> registrations = new java.util.LinkedHashMap<>();

    /** Standalone host composition uses the same canonical plugin validator and registry. */
    public static ProviderPluginHost open(Path pluginsDirectory) {
        return new ProviderPluginHost(pluginsDirectory, PluginRegistries.standalone());
    }

    public ProviderPluginHost(Path pluginsDirectory, PluginRegistrationPort pluginRegistry) {
        this(new DefaultPluginManager(Objects.requireNonNull(pluginsDirectory, "pluginsDirectory")),
                pluginRegistry);
    }

    ProviderPluginHost(PluginManager pluginManager, PluginRegistrationPort pluginRegistry) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
        this.pluginRegistry = Objects.requireNonNull(pluginRegistry, "pluginRegistry");
    }

    /** Loads, starts, validates, and registers every typed provider contribution once. */
    public synchronized ProviderPluginCatalog loadAndStart() {
        if (loaded) {
            return catalog;
        }
        try {
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
            List<ProviderPluginContribution> contributions =
                    pluginManager.getExtensions(ProviderPluginContribution.class);
            var descriptors = preflight(contributions);
            for (ProviderPluginContribution contribution : contributions) {
                try {
                    var descriptor = descriptors.get(contribution);
                    var registration = pluginRegistry.registerRuntime(descriptor);
                    registrations.put(descriptor.pluginId() + "@" + descriptor.pluginVersion(), registration);
                } catch (PluginRegistrationException rejected) {
                    throw new ProviderPluginLoadException("INVALID_PLATFORM_PLUGIN_DESCRIPTOR", rejected.issues().toString());
                }
                catalog.register(contribution);
            }
            loaded = true;
            return catalog;
        } catch (ProviderPluginLoadException failure) {
            stopAndUnloadAfterFailure(failure);
            throw failure;
        } catch (RuntimeException failure) {
            stopAndUnloadAfterFailure(failure);
            throw new ProviderPluginLoadException(
                    "PF4J_PROVIDER_PLUGIN_LOAD_FAILED", "provider plugin load/start failed", failure);
        }
    }

    public synchronized boolean disable(String pluginId) {
        PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
        if (wrapper == null) {
            return false;
        }
        String version = wrapper.getDescriptor().getVersion();
        try {
            pluginManager.stopPlugin(pluginId);
            return pluginManager.disablePlugin(pluginId);
        } finally { removeContribution(pluginId, version); }
    }

    public synchronized boolean unload(String pluginId) {
        PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
        if (wrapper == null) {
            return false;
        }
        String version = wrapper.getDescriptor().getVersion();
        try {
            pluginManager.stopPlugin(pluginId);
            return pluginManager.unloadPlugin(pluginId);
        } finally { removeContribution(pluginId, version); }
    }

    public ProviderPluginCatalog catalog() {
        return catalog;
    }

    @Override
    public synchronized void close() {
        RuntimeException failure = null;
        try {
            for (var plugin : List.copyOf(pluginManager.getStartedPlugins()))
                failure = attempt(() -> pluginManager.stopPlugin(plugin.getPluginId()), failure);
            for (var plugin : List.copyOf(pluginManager.getPlugins()))
                failure = attempt(() -> pluginManager.unloadPlugin(plugin.getPluginId()), failure);
        } finally {
            retireRegistrations();
            catalog.clear();
            loaded = false;
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException attempt(Runnable action, RuntimeException previous) {
        try { action.run(); }
        catch (RuntimeException failure) {
            if (previous == null) return failure;
            if (previous != failure && java.util.Arrays.stream(previous.getSuppressed()).noneMatch(e -> e == failure))
                previous.addSuppressed(failure);
        }
        return previous;
    }

    private java.util.Map<ProviderPluginContribution, PluginDescriptor> preflight(List<ProviderPluginContribution> contributions) {
        var descriptors = new java.util.IdentityHashMap<ProviderPluginContribution, PluginDescriptor>();
        if (pluginManager.getPlugins().stream().anyMatch(p -> p.getPluginState() == org.pf4j.PluginState.FAILED))
            throw new ProviderPluginLoadException("PF4J_PROVIDER_PLUGIN_LOAD_FAILED", "A plugin failed to start");
        Set<String> identities = new HashSet<>();
        Set<ProviderBindingPin> bindings = new HashSet<>();
        List<ProviderPluginContribution> deterministic = new ArrayList<>(contributions);
        deterministic.sort(java.util.Comparator.comparing(ProviderPluginContribution::pluginId)
                .thenComparing(ProviderPluginContribution::pluginVersion));
        for (ProviderPluginContribution contribution : deterministic) {
            PluginWrapper wrapper = pluginManager.whichPlugin(contribution.getClass());
            PluginDescriptor descriptor = Objects.requireNonNull(
                    contribution.pluginDescriptor(), "pluginDescriptor");
            String identity = contribution.pluginId() + "@" + contribution.pluginVersion();
            if (!identities.add(identity)) {
                throw new ProviderPluginLoadException(
                        "DUPLICATE_PLUGIN_ID_VERSION", "duplicate typed contribution " + identity);
            }
            if (!bindings.add(contribution.providerBindingPin())) {
                throw new ProviderPluginLoadException(
                        "DUPLICATE_PROVIDER_BINDING_PIN", "provider binding already contributed");
            }
            if (wrapper == null
                    || !wrapper.getPluginId().equals(contribution.pluginId())
                    || !wrapper.getDescriptor().getVersion().equals(contribution.pluginVersion())
                    || !descriptor.pluginId().equals(contribution.pluginId())
                    || !descriptor.pluginVersion().equals(contribution.pluginVersion())) {
                throw new ProviderPluginLoadException(
                        "PLUGIN_IDENTITY_MISMATCH", "PF4J, platform, and contribution identities differ");
            }
            var issues = pluginRegistry.validate(descriptor);
            if (!issues.isEmpty()) {
                throw new ProviderPluginLoadException(
                        "INVALID_PLATFORM_PLUGIN_DESCRIPTOR", issues.toString());
            }
            descriptors.put(contribution, descriptor);
            validateProviderContracts(contribution);
            if (pluginRegistry.findByPluginId(contribution.pluginId()).isPresent()) {
                throw new ProviderPluginLoadException(
                        "DUPLICATE_PLUGIN_ID_VERSION", "plugin registry already contains plugin identity");
            }
        }
        return descriptors;
    }

    private static void validateProviderContracts(ProviderPluginContribution contribution) {
        ProviderBindingPin binding = Objects.requireNonNull(
                contribution.providerBindingPin(), "providerBindingPin");
        var descriptor = Objects.requireNonNull(
                contribution.providerDescriptor(), "providerDescriptor");
        var executionContract = Objects.requireNonNull(
                contribution.providerExecutionContract(), "providerExecutionContract");
        var capabilityProfile = Objects.requireNonNull(
                contribution.providerCapabilityProfile(), "providerCapabilityProfile");
        var runtimeSupport = Objects.requireNonNull(
                contribution.workerRuntimeSupportRequirement(), "workerRuntimeSupportRequirement");
        if (!binding.providerId().equals(descriptor.providerId())
                || !binding.providerImplementationId().equals(descriptor.providerImplementationId())
                || !binding.providerVersion().equals(descriptor.providerVersion())
                || !binding.providerExecutionContractVersion()
                        .equals(executionContract.contractVersion())
                || !binding.providerCapabilityProfileVersionOrDigest()
                        .equals(capabilityProfile.reference())
                || !runtimeSupport.providerBindingPin().equals(binding)) {
            throw new ProviderPluginLoadException(
                    "INVALID_PROVIDER_CONTRIBUTION", "provider metadata does not retain one exact binding");
        }
    }

    private void removeContribution(String pluginId, String version) {
        var registration = registrations.remove(pluginId + "@" + version);
        if (registration != null) registration.close();
        catalog.remove(pluginId, version);
    }

    private void retireRegistrations() {
        registrations.values().forEach(PluginRegistrationPort.Registration::close);
        registrations.clear();
    }

    private void stopAndUnloadAfterFailure(RuntimeException failure) {
        failure = attempt(pluginManager::stopPlugins, failure);
        failure = attempt(pluginManager::unloadPlugins, failure);
        try {
            failure = attempt(this::retireRegistrations, failure);
        } finally {
            catalog.clear();
            loaded = false;
        }
    }
}
