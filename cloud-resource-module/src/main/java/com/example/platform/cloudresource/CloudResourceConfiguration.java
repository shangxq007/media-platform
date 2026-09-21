package com.example.platform.cloudresource;

import com.example.platform.cloudresource.api.CloudResourceController;
import com.example.platform.cloudresource.app.CloudResourceCatalogService;
import com.example.platform.cloudresource.domain.CloudResourceProvider;
import com.example.platform.cloudresource.infrastructure.StubCloudResourceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import java.util.List;

/** The cloud module's single composition boundary. No production provider is bundled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.cloud-resource", name = "enabled", havingValue = "true")
public class CloudResourceConfiguration {
    @Bean
    @Profile("dev & !prod")
    StubCloudResourceProvider stubCloudResourceProvider() {
        return new StubCloudResourceProvider();
    }

    @Bean
    CloudResourceCatalogService cloudResourceCatalogService(List<CloudResourceProvider> providers,
            org.springframework.core.env.Environment environment) {
        if (providers.size() != 1 || providers.getFirst().code() == null
                || providers.getFirst().code().isBlank()) {
            throw new IllegalStateException("app.cloud-resource.enabled requires exactly one valid CloudResourceProvider");
        }
        if (providers.getFirst() instanceof StubCloudResourceProvider
                && !environment.matchesProfiles("dev & !prod")) {
            throw new IllegalStateException("Cloud stub requires explicit dev profile without prod");
        }
        return new CloudResourceCatalogService(providers);
    }

    @Bean
    CloudResourceController cloudResourceController(CloudResourceCatalogService catalog) {
        return new CloudResourceController(catalog);
    }
}
