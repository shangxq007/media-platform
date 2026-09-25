package com.example.platform.coverimage;

import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.workerfabric.reuse.ArtifactMaterializerPort;
import com.example.platform.workerfabric.reuse.DirectStorageArtifactMaterializer;
import com.example.platform.workerfabric.reuse.WorkerLocalMaterializationCache;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the canonical, digest-verified Artifact materializer for the cover worker.
 *
 * <p>No workerfabric code is modified: the existing {@link StorageProvider} beans of this context are
 * collected into the {@code Map<StorageProviderId, StorageProvider>} that
 * {@link DirectStorageArtifactMaterializer} expects, together with a bounded worker-local byte cache.
 * Two providers claiming one id fail closed.
 */
@Configuration
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public class CoverImageMaterializationConfiguration {

    @Bean
    @ConditionalOnMissingBean(ArtifactMaterializerPort.class)
    ArtifactMaterializerPort coverImageArtifactMaterializer(
            ArtifactQueryService artifacts,
            List<StorageProvider> storageProviders,
            @Value("${app.cover-image.materialization-root:./.data/cover-image-materialized}") String cacheRoot,
            @Value("${app.cover-image.materialization-capacity-bytes:1073741824}") long capacityBytes) {
        Map<StorageProviderId, StorageProvider> providers = new LinkedHashMap<>();
        for (StorageProvider provider : storageProviders) {
            StorageProviderId id = provider.providerId();
            if (providers.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("duplicate storage provider id: " + id.value());
            }
        }
        if (providers.isEmpty()) {
            throw new IllegalStateException("cover-image subject reads require a registered StorageProvider");
        }
        return new DirectStorageArtifactMaterializer(
                artifacts,
                providers,
                new WorkerLocalMaterializationCache(Path.of(cacheRoot), capacityBytes));
    }
}
