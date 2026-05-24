package com.example.platform.shared.tenant;

import com.example.platform.shared.web.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class TenantIsolationService {

    public String requireTenantId() {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context not set — cannot execute tenant-scoped query");
        }
        return tenantId;
    }

    public void assertTenantAccess(String resourceTenantId) {
        String currentTenant = requireTenantId();
        if (!currentTenant.equals(resourceTenantId)) {
            throw new SecurityException(
                    "Tenant mismatch: current=" + currentTenant + " resource=" + resourceTenantId);
        }
    }

    public boolean isTenantSet() {
        String tenantId = TenantContext.get();
        return tenantId != null && !tenantId.isBlank();
    }

    public String tenantOrDefault(String fallback) {
        String tenantId = TenantContext.get();
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : fallback;
    }
}
