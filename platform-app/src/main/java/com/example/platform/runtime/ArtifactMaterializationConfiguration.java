package com.example.platform.runtime;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assembles the canonical, digest-verified Artifact materializer for the media worker.
 *
 * <p>No workerfabric code is modified: the existing {@link StorageProvider} beans of this context are
 * collected into the {@code Map<StorageProviderId, StorageProvider>} that
 * {@link DirectStorageArtifactMaterializer} expects, together with a bounded worker-local byte cache.
 * Two providers claiming one id fail closed.
 */
@Configuration
@ConditionalOnExpression("'${platform.runtime.role:}' == 'WORKER' or '${platform.runtime.role:}' == 'COMBINED'")
public class ArtifactMaterializationConfiguration {

    @Bean
    @ConditionalOnMissingBean(ArtifactMaterializerPort.class)
    ArtifactMaterializerPort artifactMaterializer(
            ArtifactQueryService artifacts,
            List<StorageProvider> storageProviders,
            @Value("${platform.ffmpeg-worker.materialization-root:./.data/materialized}") String cacheRoot,
            @Value("${platform.ffmpeg-worker.materialization-capacity-bytes:1073741824}") long capacityBytes) {
        Map<StorageProviderId, StorageProvider> providers = new LinkedHashMap<>();
        for (StorageProvider provider : storageProviders) {
            StorageProviderId id = provider.providerId();
            if (providers.putIfAbsent(id, provider) != null) {
                throw new IllegalStateException("duplicate storage provider id: " + id.value());
            }
        }
        if (providers.isEmpty()) {
            throw new IllegalStateException("media input reads require a registered StorageProvider");
        }
        return new DirectStorageArtifactMaterializer(
                artifacts,
                providers,
                new WorkerLocalMaterializationCache(Path.of(cacheRoot), capacityBytes));
    }
}
