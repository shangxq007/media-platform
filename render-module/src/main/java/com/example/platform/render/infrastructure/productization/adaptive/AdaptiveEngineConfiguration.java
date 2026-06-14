package com.example.platform.render.infrastructure.productization.adaptive;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdaptiveEngineConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "platform.adaptive-engine",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = false
    )
    public AdaptiveEngine adaptiveEngine() {
        return new AdaptiveEngine();
    }
}
