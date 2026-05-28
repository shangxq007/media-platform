package com.example.platform.audit.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Default adapter that publishes security alerts to SLF4J structured logs.
 *
 * <p>Output format matches the existing {@code SECURITY_ALERT} logger format
 * for backward compatibility.
 */
public class Slf4jSecurityAlertAdapter implements SecurityAlertPort {

    private static final Logger alertLog = LoggerFactory.getLogger("SECURITY_ALERT");

    @Override
    public void publish(SecurityAlert alert) {
        try {
            Map<String, Object> attrs = alert.attributes();
            if (attrs != null && !attrs.isEmpty()) {
                alertLog.warn(
                        "event=audit_security_alert rule={} severity={} category={} action={} " +
                        "actorType={} actorId={} resourceType={} resourceId={} " +
                        "targetTenantId={} result={} requestId={} traceId={} attributes={}",
                        alert.rule(), alert.severity(), alert.category(), alert.action(),
                        alert.actorType(), alert.actorId(),
                        alert.resourceType(), alert.resourceId(),
                        alert.targetTenantId(), alert.result(),
                        alert.requestId(), alert.traceId(), attrs);
            } else {
                alertLog.warn(
                        "event=audit_security_alert rule={} severity={} category={} action={} " +
                        "actorType={} actorId={} resourceType={} resourceId={} " +
                        "targetTenantId={} result={} requestId={} traceId={}",
                        alert.rule(), alert.severity(), alert.category(), alert.action(),
                        alert.actorType(), alert.actorId(),
                        alert.resourceType(), alert.resourceId(),
                        alert.targetTenantId(), alert.result(),
                        alert.requestId(), alert.traceId());
            }
        } catch (Exception e) {
            // Logging failure must never block business flow
            Logger fallback = LoggerFactory.getLogger(Slf4jSecurityAlertAdapter.class);
            fallback.warn("Slf4jSecurityAlertAdapter failed to publish alert: {}", e.getMessage());
        }
    }
}
