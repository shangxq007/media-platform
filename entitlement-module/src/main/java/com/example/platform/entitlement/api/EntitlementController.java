package com.example.platform.entitlement.api;

import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.EntitlementSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class EntitlementController {
    private final EntitlementService entitlementService;

    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    // -------------------------------------------------------------------------
    // Tenant-scoped entitlement endpoints (Prompt 13)
    // -------------------------------------------------------------------------

    @GetMapping("/tenants/{tenantId}/entitlements")
    public Map<String, Object> getEntitlements(@PathVariable String tenantId) {
        EntitlementSnapshot snapshot = entitlementService.getSnapshot(tenantId);
        return Map.of(
                "tenantId", tenantId,
                "entitlements", snapshot
        );
    }

    // -------------------------------------------------------------------------
    // Legacy endpoints (kept for backward compatibility)
    // -------------------------------------------------------------------------

    @GetMapping("/entitlements/subjects/{subjectId}")
    public EntitlementSnapshot snapshot(@PathVariable String subjectId) {
        return entitlementService.getSnapshot(subjectId);
    }
}
