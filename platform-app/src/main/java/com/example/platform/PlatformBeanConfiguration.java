package com.example.platform;

import com.example.platform.shared.monitoring.SentryMonitoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformBeanConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SentryMonitoringService sentryMonitoringService() {
        return new SentryMonitoringService(false, "development", 1.0);
    }
}
