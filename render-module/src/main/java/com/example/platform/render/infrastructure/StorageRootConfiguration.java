package com.example.platform.render.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Configuration for the local storage root path.
 *
 * <p>Provides a {@link Path} bean that represents the root directory
 * for local file storage used by the render pipeline.</p>
 */
@Configuration
public class StorageRootConfiguration {

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    @Bean
    public Path storageRootPath() {
        return Path.of(storageRoot);
    }
}
