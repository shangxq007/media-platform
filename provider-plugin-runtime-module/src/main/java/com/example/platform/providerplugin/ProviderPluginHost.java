package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.extension.app.PluginDescriptorValidator;
import com.example.platform.extension.app.PluginRegistryImpl;
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
    private final PluginRegistryImpl pluginRegistry;
    private final ProviderPluginCatalog catalog = new ProviderPluginCatalog();
    private boolean loaded;

    public ProviderPluginHost(Path pluginsDirectory, PluginRegistryImpl pluginRegistry) {
        this(new DefaultPluginManager(Objects.requireNonNull(pluginsDirectory, "pluginsDirectory")),
                pluginRegistry);
    }

    ProviderPluginHost(PluginManager pluginManager, PluginRegistryImpl pluginRegistry) {
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
            preflight(contributions);
            for (ProviderPluginContribution contribution : contributions) {
                List<PluginDescriptorValidationIssue> issues =
                        pluginRegistry.register(contribution.pluginDescriptor());
                if (!issues.isEmpty()) {
                    throw new ProviderPluginLoadException(
                            "INVALID_PLATFORM_PLUGIN_DESCRIPTOR", issues.toString());
                }
                catalog.register(contribution);
            }
            loaded = true;
            return catalog;
        } catch (ProviderPluginLoadException failure) {
            stopAndUnloadAfterFailure();
            throw failure;
        } catch (RuntimeException failure) {
            stopAndUnloadAfterFailure();
            throw new ProviderPluginLoadException(
                    "PF4J_PROVIDER_PLUGIN_LOAD_FAILED", "provider plugin load/start failed", failure);
        }
    }

    public synchronized boolean disable(String pluginId) {
        PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
        if (wrapper == null) {
            return false;
        }
        List<ProviderPluginContribution> contributions =
                pluginManager.getExtensions(ProviderPluginContribution.class, pluginId);
        pluginManager.stopPlugin(pluginId);
        boolean disabled = pluginManager.disablePlugin(pluginId);
        removeContributions(contributions);
        return disabled;
    }

    public synchronized boolean unload(String pluginId) {
        PluginWrapper wrapper = pluginManager.getPlugin(pluginId);
        if (wrapper == null) {
            return false;
        }
        List<ProviderPluginContribution> contributions =
                pluginManager.getExtensions(ProviderPluginContribution.class, pluginId);
        pluginManager.stopPlugin(pluginId);
        boolean unloaded = pluginManager.unloadPlugin(pluginId);
        removeContributions(contributions);
        return unloaded;
    }

    public PluginManager pluginManager() {
        return pluginManager;
    }

    public ProviderPluginCatalog catalog() {
        return catalog;
    }

    @Override
    public synchronized void close() {
        try {
            List<String> started = pluginManager.getStartedPlugins().stream()
                    .map(PluginWrapper::getPluginId).toList();
            started.forEach(pluginManager::stopPlugin);
            List<String> loadedPlugins = pluginManager.getPlugins().stream()
                    .map(PluginWrapper::getPluginId).toList();
            loadedPlugins.forEach(pluginManager::unloadPlugin);
        } finally {
            catalog.clear();
            loaded = false;
        }
    }

    private void preflight(List<ProviderPluginContribution> contributions) {
        PluginDescriptorValidator validator = new PluginDescriptorValidator();
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
            if (!validator.validate(descriptor).isEmpty()) {
                throw new ProviderPluginLoadException(
                        "INVALID_PLATFORM_PLUGIN_DESCRIPTOR", validator.validate(descriptor).toString());
            }
            validateProviderContracts(contribution);
            if (pluginRegistry.findByPluginId(contribution.pluginId()).isPresent()) {
                throw new ProviderPluginLoadException(
                        "DUPLICATE_PLUGIN_ID_VERSION", "plugin registry already contains plugin identity");
            }
        }
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

    private void removeContributions(List<ProviderPluginContribution> contributions) {
        contributions.forEach(contribution -> catalog.remove(
                contribution.pluginId(), contribution.pluginVersion()));
    }

    private void stopAndUnloadAfterFailure() {
        try {
            pluginManager.stopPlugins();
        } finally {
            try {
                pluginManager.unloadPlugins();
            } finally {
                catalog.clear();
                loaded = false;
            }
        }
    }
}
