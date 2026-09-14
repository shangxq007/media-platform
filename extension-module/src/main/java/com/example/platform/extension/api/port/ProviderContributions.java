package com.example.platform.extension.api.port;
import com.example.platform.extension.domain.ExtensionTrustLevel;
import com.example.platform.extension.runtime.PluginRuntimeProviderBinding;
/** Existing provider binding contributions; does not define platform capability or execution semantics. */
public interface ProviderContributions {
    void registerProviderExtension(String key, PluginRuntimeProviderBinding extension, ExtensionTrustLevel trustLevel, String registeredBy);
    PluginRuntimeProviderBinding findProviderBinding(String key);
}
