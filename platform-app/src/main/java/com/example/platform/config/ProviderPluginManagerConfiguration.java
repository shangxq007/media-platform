package com.example.platform.config;

import com.example.platform.extension.app.PluginRegistryImpl;
import com.example.platform.providerplugin.ProviderPluginHost;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Starts the canonical typed PF4J provider host without concrete provider dependencies. */
@Configuration
public class ProviderPluginManagerConfiguration {

    @Bean(destroyMethod = "close")
    ProviderPluginHost providerPluginHost(
            PluginRegistryImpl pluginRegistry,
            @Value("${app.extensions.plugins-dir:./plugins}") String pluginsDirectory) {
        ProviderPluginHost host = new ProviderPluginHost(Path.of(pluginsDirectory), pluginRegistry);
        host.loadAndStart();
        return host;
    }
}
