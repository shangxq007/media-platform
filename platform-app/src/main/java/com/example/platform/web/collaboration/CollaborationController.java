package com.example.platform.web.collaboration;

import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me/shared-resources")
public class CollaborationController {

    private final SharedResourceService sharedResourceService;

    public CollaborationController(SharedResourceService sharedResourceService) {
        this.sharedResourceService = sharedResourceService;
    }

    @PostMapping("/grants")
    public ResponseEntity<Map<String, Object>> grantAccess(
            @RequestBody GrantSharedResourceRequest body,
            HttpServletRequest req) {
        String tenantId = TenantContext.get();
        if (tenantId == null) {
            tenantId = body.tenantId();
        }
        String sharedBy = headerOrDefault(req, "X-User-ID", body.sharedByUserId());
        if (tenantId == null || body.sharedWithUserId() == null || body.resourceId() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "tenantId, resourceId, and sharedWithUserId are required"));
        }
        String resourceType = body.resourceType() != null ? body.resourceType() : "project";
        SharedResourceJdbcRepository.SharedResourceGrant grant =
                sharedResourceService.grantAccess(
                        tenantId,
                        resourceType,
                        body.resourceId(),
                        body.resourceName() != null ? body.resourceName() : body.resourceId(),
                        sharedBy,
                        body.sharedWithUserId(),
                        body.permission() != null ? body.permission() : "READ");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("grantId", grant.grantId());
        result.put("tenantId", grant.tenantId());
        result.put("resourceType", resourceType);
        result.put("resourceId", grant.resourceId());
        result.put("sharedWithUserId", grant.sharedWithUserId());
        result.put("permission", grant.permission());
        result.put("status", grant.status());
        return ResponseEntity.ok(result);
    }

    private static String headerOrDefault(HttpServletRequest req, String header, String fallback) {
        String value = req.getHeader(header);
        return value != null ? value : fallback;
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<Map<String, Object>> revokeMyGrant(@PathVariable String grantId) {
        boolean revoked = sharedResourceService.revokeGrant(grantId);
        if (!revoked) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("grantId", grantId, "status", "REVOKED"));
    }

    public record GrantSharedResourceRequest(
            String tenantId,
            String resourceType,
            String resourceId,
            String resourceName,
            String sharedByUserId,
            String sharedWithUserId,
            String permission) {}
}
