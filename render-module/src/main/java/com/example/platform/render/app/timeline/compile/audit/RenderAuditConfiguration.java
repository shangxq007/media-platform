package com.example.platform.render.app.timeline.compile.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-closed render audit composition until Phase 3 supplies durable authority.
 */
@Configuration
public class RenderAuditConfiguration {

    @Bean
    public RenderAuditEventSink renderAuditEventSink() {
        return new FailClosedRenderAuditEventSink();
    }

    @Bean
    public RenderAuditTrail renderAuditTrail(RenderAuditEventSink sink) {
        return new RenderAuditTrail(sink);
    }
}
