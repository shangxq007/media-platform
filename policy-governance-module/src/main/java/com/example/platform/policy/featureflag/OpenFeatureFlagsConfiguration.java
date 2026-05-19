package com.example.platform.policy.featureflag;

import dev.openfeature.contrib.providers.unleash.UnleashProvider;
import dev.openfeature.contrib.providers.unleash.UnleashProviderConfig;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import io.getunleash.util.UnleashConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.HashMap;

@Configuration
@EnableConfigurationProperties(AppFeaturesProperties.class)
public class OpenFeatureFlagsConfiguration {

    @Bean
    public FeatureProvider openFeatureProvider(AppFeaturesProperties properties) {
        var unleash = properties.getUnleash();
        if (unleash.isEnabled()) {
            UnleashConfig.Builder builder = UnleashConfig.builder()
                    .appName(unleash.getAppName())
                    .instanceId(unleash.getInstanceId())
                    .unleashAPI(unleash.getApiUrl());
            if (StringUtils.hasText(unleash.getApiKey())) {
                builder.apiKey(unleash.getApiKey());
            }
            UnleashProviderConfig config =
                    UnleashProviderConfig.builder().unleashConfigBuilder(builder).build();
            return new UnleashProvider(config);
        }
        return new InMemoryProvider(new HashMap<>());
    }

    @Bean
    public OpenFeatureLifecycle openFeatureLifecycle(FeatureProvider openFeatureProvider) throws Exception {
        return new OpenFeatureLifecycle(openFeatureProvider);
    }
}
