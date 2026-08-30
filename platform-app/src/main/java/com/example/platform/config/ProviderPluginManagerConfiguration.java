package com.example.platform.config;

import com.example.platform.providerplugin.ProviderPluginCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Phase-0 provider-plugin composition with external directory execution disabled fail-closed. */
@Configuration
public class ProviderPluginManagerConfiguration {

    /**
     * Exposes only the empty typed catalog used by explicitly bundled platform mechanics.
     * Directory-backed PF4J loading remains unavailable until an immutable digest authority exists.
     */
    @Bean
    ProviderPluginCatalog providerPluginCatalog() {
        return new ProviderPluginCatalog();
    }
}
