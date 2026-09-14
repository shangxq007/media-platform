package com.example.platform.extension.api.port;

/** Extension-owned composition for standalone distribution hosts outside Spring. */
public final class PluginRegistries {
    private PluginRegistries() {}
    public static PluginRegistrationPort standalone() {
        return new com.example.platform.extension.app.PluginRegistryImpl(
                new com.example.platform.extension.app.PluginDescriptorValidator(),
                new com.example.platform.extension.app.PluginHealthRegistry());
    }
}
