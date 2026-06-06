package com.example.platform.audit.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuditAlertService {

    private static final Logger log = LoggerFactory.getLogger(AuditAlertService.class);

    private final AuditAlertProperties properties;
    private final SecurityAlertPort alertPublisher;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();

    @Autowired(required = false)
    public AuditAlertService(@Autowired(required = false) AuditAlertProperties properties,
                             @Autowired(required = false) SecurityAlertPort alertPublisher) {
        this.properties = properties;
        this.alertPublisher = alertPublisher;
        this.clock = Clock.systemUTC();
    }

    AuditAlertService(AuditAlertProperties properties, SecurityAlertPort alertPublisher, Clock clock) {
        this.properties = properties;
        this.alertPublisher = alertPublisher;
        this.clock = clock;
    }

    public void evaluate(String category, String action, String actorType, String actorId,
                         String resourceType, String resourceId, String targetTenantId,
                         String result, String requestId, String traceId) {
        if (properties == null || alertPublisher == null) {
            return;
        }
        if (category == null || result == null) {
            return;
        }
        try {
            if ("ADMIN_AUDIT".equals(category) && "DENIED".equals(result)) {
                alertPublisher.publish(new SecurityAlert(
                        "SINGLE_DENIED", "HIGH", category, action,
                        actorType, actorId, resourceType, resourceId,
                        targetTenantId, result, requestId, traceId,
                        Instant.now(), Map.of()));
            } else if ("ADMIN_AUDIT".equals(category) && "FAILED".equals(result)) {
                alertPublisher.publish(new SecurityAlert(
                        "SINGLE_FAILED", "MEDIUM", category, action,
                        actorType, actorId, resourceType, resourceId,
                        targetTenantId, result, requestId, traceId,
                        Instant.now(), Map.of()));
            }
        } catch (Exception e) {
            log.warn("Alert evaluation failed: {}", e.getMessage());
        }
    }
}
