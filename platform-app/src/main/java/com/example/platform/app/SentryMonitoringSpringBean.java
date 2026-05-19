package com.example.platform.shared.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sentry monitoring service - Spring bean version.
 */
@Component
@ConditionalOnProperty(name = "sentry.enabled", havingValue = "true", matchIfMissing = false)
public class SentryMonitoringSpringBean extends SentryMonitoringService {

    public SentryMonitoringSpringBean(
            @Value("${sentry.enabled:false}") boolean enabled,
            @Value("${sentry.environment:development}") String environment,
            @Value("${sentry.traces-sample-rate:1.0}") double tracesSampleRate) {
        super(enabled, environment, tracesSampleRate);
    }
}
