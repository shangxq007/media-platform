package com.example.platform.config;

import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.providerplugin.ProviderPluginHost;
import com.example.platform.providerplugin.execution.RuntimeExecutionBackends;
import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import java.nio.file.Path;
import java.util.List;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.providerplugin.InProcessProviderPlugins;
import com.example.platform.providerplugin.bmf.BmfProviderExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Starts the canonical typed PF4J provider host without concrete provider dependencies. */
@Configuration
public class ProviderPluginManagerConfiguration {

    @Bean
    ExecutionBackendRegistry executionBackendRegistry() {
        return RuntimeExecutionBackends.create();
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
            @Value("${platform.bmf-provider.enabled:false}") boolean bmfEnabled) {
        return InProcessProviderPlugins.catalogWith(
                providerPluginHost.catalog(), bmfEnabled
                        ? List.of(new BmfProviderExtension()) : List.of());
    }
}

