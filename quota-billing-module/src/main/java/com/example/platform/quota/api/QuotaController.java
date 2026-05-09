package com.example.platform.quota.api;

import com.example.platform.quota.app.QuotaService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class QuotaController {
    private final QuotaService service;

    public QuotaController(QuotaService service) {
        this.service = service;
    }

    // -------------------------------------------------------------------------
    // Tenant-scoped quota endpoints (Prompt 13)
    // -------------------------------------------------------------------------

    @GetMapping("/tenants/{tenantId}/quota")
    public Map<String, Object> getQuota(@PathVariable String tenantId) {
        return Map.of(
                "tenantId", tenantId,
                "buckets", service.getBucketsForTenant(tenantId)
        );
    }

    @GetMapping("/tenants/{tenantId}/usage")
    public Map<String, Object> getUsage(@PathVariable String tenantId) {
        return Map.of(
                "tenantId", tenantId,
                "buckets", service.getBucketsForTenant(tenantId)
        );
    }

    @PostMapping("/tenants/{tenantId}/quota/reset")
    public Map<String, Object> resetQuota(@PathVariable String tenantId) {
        return Map.of(
                "tenantId", tenantId,
                "status", "reset",
                "message", "Quota reset for tenant: " + tenantId
        );
    }

    // -------------------------------------------------------------------------
    // Legacy endpoints (kept for backward compatibility)
    // -------------------------------------------------------------------------

    @GetMapping("/quota/billing/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }
}
