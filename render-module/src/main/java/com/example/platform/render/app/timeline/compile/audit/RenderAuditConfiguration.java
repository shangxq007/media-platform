package com.example.platform.render.app.timeline.compile.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for render audit event trail.
 * Default: no-op sink (events discarded in production unless overridden).
 */
@Configuration
public class RenderAuditConfiguration {

    @Bean
    public RenderAuditEventSink renderAuditEventSink() {
        return new NoopRenderAuditEventSink();
    }

    @Bean
    public RenderAuditTrail renderAuditTrail(RenderAuditEventSink sink) {
        return new RenderAuditTrail(sink);
    }
}
