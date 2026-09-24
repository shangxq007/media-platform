package com.example.platform.config;

import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.composition.app.OwnerPortCompositionMaterialization;
import com.example.platform.composition.app.CompositionMaterializationPort;
import com.example.platform.composition.app.CompositionResultRepository;
import com.example.platform.storage.api.StorageOutputPort;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds Composition output materialization to the existing owner services. */
@Configuration
public class CompositionMaterializationConfiguration {
    @Bean
    CompositionMaterializationPort compositionMaterializationPort(
            StorageOutputPort storage, ArtifactCommitService artifacts,
            @Value("${app.storage.local-root:./.data/storage}") String root, CompositionResultRepository results) {
        return new OwnerPortCompositionMaterialization(storage, artifacts, Path.of(root), results);
    }
}
