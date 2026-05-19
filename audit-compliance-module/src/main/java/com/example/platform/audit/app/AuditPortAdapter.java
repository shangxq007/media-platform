package com.example.platform.audit.app;

import com.example.platform.shared.audit.AuditPort;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Adapter that exposes AuditService as AuditPort.
 */
@Component
public class AuditPortAdapter implements AuditPort {

    private final AuditService auditService;

    public AuditPortAdapter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String actorType, String action, String category,
            String resourceType, String resourceId, Map<String, Object> payload) {
        auditService.record(actorType, action, category, resourceType, resourceId, payload);
    }
}
