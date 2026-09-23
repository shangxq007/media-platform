package com.example.platform.policy.featureflag;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppFeaturesProperties.class)
public class OpenFeatureFlagsConfiguration {
    @Bean
    public FeatureProvider openFeatureProvider(LocalFeatureFlagProvider provider) {
        return new PlatformOpenFeatureProvider(provider);
    }

    @Bean
    public OpenFeatureLifecycle openFeatureLifecycle(FeatureProvider provider) throws Exception {
        return new OpenFeatureLifecycle(provider);
    }

    @Bean
    public FeatureFlagSnapshotResolver featureFlagSnapshotResolver(PlatformFeatureProvider provider) {
        return new FeatureFlagSnapshotResolver(provider);
    }
}
