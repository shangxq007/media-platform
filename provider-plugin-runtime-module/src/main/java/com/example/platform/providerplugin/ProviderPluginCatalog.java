package com.example.platform.providerplugin;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Separate typed provider contribution catalog; never a CapabilityRegistry. */
public final class ProviderPluginCatalog {

    private final Map<String, ProviderPluginContribution> byPluginIdentity = new LinkedHashMap<>();
    private final Map<ProviderBindingPin, ProviderPluginContribution> byBinding = new LinkedHashMap<>();

    public synchronized void register(ProviderPluginContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        String pluginId = requireIdentity(contribution.pluginId(), "pluginId");
        String pluginVersion = requireIdentity(contribution.pluginVersion(), "pluginVersion");
        ProviderBindingPin bindingPin = Objects.requireNonNull(
                contribution.providerBindingPin(), "providerBindingPin");
        String identity = pluginId + "@" + pluginVersion;
        if (byPluginIdentity.containsKey(identity)) {
            throw new ProviderPluginLoadException(
                    "DUPLICATE_PLUGIN_ID_VERSION", "duplicate typed contribution " + identity);
        }
        if (byBinding.containsKey(bindingPin)) {
            throw new ProviderPluginLoadException(
                    "DUPLICATE_PROVIDER_BINDING_PIN", "provider binding already contributed");
        }
        byPluginIdentity.put(identity, contribution);
        byBinding.put(bindingPin, contribution);
    }

    public synchronized Optional<ProviderPluginContribution> find(ProviderBindingPin bindingPin) {
        return Optional.ofNullable(byBinding.get(bindingPin));
    }

    public synchronized List<ProviderPluginContribution> contributions() {
        List<ProviderPluginContribution> result = new ArrayList<>(byPluginIdentity.values());
        result.sort(Comparator.comparing(ProviderPluginContribution::pluginId)
                .thenComparing(ProviderPluginContribution::pluginVersion));
        return List.copyOf(result);
    }

    public synchronized void remove(String pluginId, String pluginVersion) {
        ProviderPluginContribution removed = byPluginIdentity.remove(pluginId + "@" + pluginVersion);
        if (removed != null) {
            byBinding.remove(removed.providerBindingPin(), removed);
        }
    }

    /** Removes every active typed contribution during host failure or shutdown. */
    synchronized void clear() {
        byPluginIdentity.clear();
        byBinding.clear();
    }

    private static String requireIdentity(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new ProviderPluginLoadException("INVALID_PLUGIN_CONTRIBUTION", field + " is invalid");
        }
        return value;
    }
}
