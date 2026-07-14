package com.example.platform;

import com.example.platform.shared.monitoring.SentryMonitoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import com.example.platform.ingest.experimental.tika.TikaExperimentalProperties;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceContractProperties;
import com.example.platform.ingest.preflight.policy.config.IngestPreflightPolicyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({IngestPreflightPolicyProperties.class, TikaExperimentalProperties.class, SafePreflightPersistenceContractProperties.class})
public class PlatformBeanConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SentryMonitoringService sentryMonitoringService() {
        return new SentryMonitoringService(false, "development", 1.0);
    }
}
