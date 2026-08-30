package com.example.platform;

import com.example.platform.ingest.experimental.tika.TikaExperimentalProperties;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceContractProperties;
import com.example.platform.ingest.preflight.policy.config.IngestPreflightPolicyProperties;
import com.example.platform.observability.monitoring.SentryMonitoringService;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.usage.ObservedRuntimeUsageEmissionPort;
import com.example.platform.usage.app.ObservedRuntimeUsageEmissionService;
import com.example.platform.usage.app.ObservedRuntimeUsageOutboxPublisher;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties({IngestPreflightPolicyProperties.class, TikaExperimentalProperties.class, SafePreflightPersistenceContractProperties.class})
public class PlatformBeanConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SentryMonitoringService sentryMonitoringService() {
        return new SentryMonitoringService(false, "development", 1.0);
    }

    @Bean
    public ObservedRuntimeUsageJdbcRepository observedRuntimeUsageRepository(
            JdbcTemplate jdbcTemplate) {
        return new ObservedRuntimeUsageJdbcRepository(jdbcTemplate);
    }

    @Bean
    public ObservedRuntimeUsageOutboxPublisher observedRuntimeUsageOutboxPublisher(
            ObservedRuntimeUsageJdbcRepository repository, OutboxEventService outboxEventService) {
        return new ObservedRuntimeUsageOutboxPublisher(repository, outboxEventService);
    }

    @Bean
    public ObservedRuntimeUsageEmissionPort observedRuntimeUsageEmissionPort(
            ObservedRuntimeUsageOutboxPublisher publisher) {
        return new ObservedRuntimeUsageEmissionService(publisher);
    }
}
