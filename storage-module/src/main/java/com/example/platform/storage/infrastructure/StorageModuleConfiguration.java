package com.example.platform.storage.infrastructure;

import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageS3Properties.class)
public class StorageModuleConfiguration {

    @Bean
    CanonicalStorageObjectIdAllocator canonicalStorageObjectIdAllocator() {
        return new CanonicalStorageObjectIdAllocator();
    }

    @Bean
    PersistedStorageIdentityClassifier persistedStorageIdentityClassifier() {
        return new PersistedStorageIdentityClassifier();
    }
}
