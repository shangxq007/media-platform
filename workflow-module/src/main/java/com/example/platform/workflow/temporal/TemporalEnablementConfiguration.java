package com.example.platform.workflow.temporal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gates Temporal runtime marker bean when {@code app.temporal.enabled=true}.
 * Temporal Spring Boot autoconfig is excluded in {@code application.yml} when disabled (default).
 */
@Configuration
@EnableConfigurationProperties(AppTemporalProperties.class)
public class TemporalEnablementConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.temporal", name = "enabled", havingValue = "true")
    TemporalRuntimeMarker temporalRuntimeMarker() {
        return new TemporalRuntimeMarker();
    }

    public record TemporalRuntimeMarker() {}
}
