package com.example.platform.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

import java.time.Clock;

/**
 * Shared runtime clock belongs to application assembly, not a retired Workflow execution service.
 */
@Configuration
public class PlatformClockConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock platformClock() {
        return Clock.systemUTC();
    }
}
