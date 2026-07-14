package com.example.platform.storage.delivery.config;

import com.example.platform.storage.delivery.registry.StorageDeliveryProfileRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the StorageDeliveryProfileRegistry as a Spring bean.
 * Uses the canonical catalog as the default profile source.
 */
@Configuration
public class StorageDeliveryProfileRegistryConfiguration {

    @Bean
    public StorageDeliveryProfileRegistry storageDeliveryProfileRegistry() {
        return StorageDeliveryProfileRegistry.fromCanonicalCatalog();
    }
}
