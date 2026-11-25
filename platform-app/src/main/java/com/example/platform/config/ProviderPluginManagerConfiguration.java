package com.example.platform.config;

import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.providerplugin.ProviderPluginHost;
import com.example.platform.providerplugin.execution.RuntimeExecutionBackends;
import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import java.nio.file.Path;
import java.util.List;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.InProcessProviderPlugins;
import com.example.platform.bmfprovider.BmfProviderExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Starts the canonical typed PF4J provider host without concrete provider dependencies. */
@Configuration
public class ProviderPluginManagerConfiguration {

    /**
     * Composition-root backend assembly. The generic provider-plugin host no longer hardcodes a
     * concrete provider backend: the LocalProcess backend always exists, and the BMF backend is
     * contributed only when the same flag that enables the BMF provider contribution is on.
     */
    @Bean
    ExecutionBackendRegistry executionBackendRegistry(
            @Value("${platform.bmf-provider.enabled:false}") boolean bmfEnabled) {
        java.util.List<com.example.platform.sandbox.execution.ExecutionBackend> backends =
                new java.util.ArrayList<>();
        backends.add(new com.example.platform.providerplugin.execution.LocalProcessExecutionBackend());
        if (bmfEnabled) {
            backends.add(com.example.platform.bmfprovider.BmfProviderPlugin.executionBackend());
        }
        return new RuntimeExecutionBackends(backends);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "platform.runtime.role", havingValue = "API", matchIfMissing = true)
    ProviderPluginHost providerPluginHost(
            PluginRegistrationPort pluginRegistry,
            @Value("${app.extensions.plugins-dir:./plugins}") String pluginsDirectory) {
        ProviderPluginHost host = new ProviderPluginHost(Path.of(pluginsDirectory), pluginRegistry);
        host.loadAndStart();
        return host;
    }
    /** API planning consumes the same typed provider declarations as the worker. */
    @Bean
    @ConditionalOnProperty(name = "platform.runtime.role", havingValue = "API", matchIfMissing = true)
    ProviderPluginCatalog providerPluginCatalog(ProviderPluginHost providerPluginHost,
            @Value("${platform.bmf-provider.enabled:false}") boolean bmfEnabled,
            @Value("${platform.bmf.bundle-sha256:}") String bundleDigest) {
        return InProcessProviderPlugins.catalogWith(
                providerPluginHost.catalog(), bmfEnabled
                        ? List.of(new BmfProviderExtension(null, bundleDigest)) : List.of());
    }
}

