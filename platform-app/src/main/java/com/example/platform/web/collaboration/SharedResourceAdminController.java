package com.example.platform.web.collaboration;

import com.example.platform.shared.web.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/shared-resources")
public class SharedResourceAdminController {

    private final SharedResourceService sharedResourceService;

    public SharedResourceAdminController(SharedResourceService sharedResourceService) {
        this.sharedResourceService = sharedResourceService;
    }

    @GetMapping("/grants")
    public List<Map<String, Object>> listGrants(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "false") boolean includeRevoked) {
        String effectiveTenant = tenantId != null ? tenantId : TenantContext.get();
        if (effectiveTenant == null) {
            return List.of();
        }
        return sharedResourceService.listGrantsForTenant(effectiveTenant, includeRevoked);
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<Map<String, Object>> revokeGrant(@PathVariable String grantId) {
        boolean revoked = sharedResourceService.revokeGrant(grantId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grantId", grantId);
        body.put("revoked", revoked);
        body.put("status", revoked ? "REVOKED" : "NOT_FOUND_OR_ALREADY_REVOKED");
        return revoked ? ResponseEntity.ok(body) : ResponseEntity.notFound().build();
    }
}
