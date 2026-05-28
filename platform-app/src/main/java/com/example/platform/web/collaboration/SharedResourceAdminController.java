package com.example.platform.web.collaboration;

import com.example.platform.security.AdminAuditHelper;
import com.example.platform.shared.web.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/shared-resources")
public class SharedResourceAdminController {

    private final SharedResourceService sharedResourceService;
    private final AdminAuditHelper auditHelper;

    public SharedResourceAdminController(SharedResourceService sharedResourceService,
                                          AdminAuditHelper auditHelper) {
        this.sharedResourceService = sharedResourceService;
        this.auditHelper = auditHelper;
    }

    @GetMapping("/grants")
    public List<Map<String, Object>> listGrants(
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "false") boolean includeRevoked,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_LIST_SHARED_GRANTS", "shared_resource", null, tenantId);
        String effectiveTenant = resolveTenantId(tenantId);
        auditHelper.log(request, "ADMIN_LIST_SHARED_GRANTS", "shared_resource", null, effectiveTenant, "SUCCESS");
        return sharedResourceService.listGrantsForTenant(effectiveTenant, includeRevoked);
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<Map<String, Object>> revokeGrant(@PathVariable String grantId,
            HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_REVOKE_SHARED_GRANT", "shared_resource", grantId, null);
        boolean revoked = sharedResourceService.revokeGrant(grantId);
        auditHelper.log(request, "ADMIN_REVOKE_SHARED_GRANT", "shared_resource", grantId, null,
                revoked ? "SUCCESS" : "NOT_FOUND");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grantId", grantId);
        body.put("revoked", revoked);
        body.put("status", revoked ? "REVOKED" : "NOT_FOUND_OR_ALREADY_REVOKED");
        return revoked ? ResponseEntity.ok(body) : ResponseEntity.notFound().build();
    }

    private String resolveTenantId(String requestedTenantId) {
        String contextTenant = TenantContext.get();
        if (contextTenant == null || contextTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.equals(contextTenant)) {
            throw new SecurityException("Tenant ID does not match authenticated tenant");
        }
        return contextTenant;
    }

    private void requireAdminRole(HttpServletRequest request) {
        requireAdminRole(request, "ADMIN_SHARED_RESOURCE", "shared_resource", null, null);
    }

    private void requireAdminRole(HttpServletRequest request, String action,
            String resourceType, String resourceId, String tenantId) {
        // 1. Check Spring Security context (OAuth2 Resource Server path)
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getAuthorities() != null) {
            for (var authority : auth.getAuthorities()) {
                String a = authority.getAuthority();
                if ("ROLE_ADMIN".equals(a) || "ADMIN".equals(a)) {
                    return;
                }
            }
        }
        // 2. Check request.isUserInRole
        if (request.isUserInRole("ADMIN")) {
            return;
        }
        // 3. Check jwt.roles request attribute (Legacy HMAC JWT path)
        if (hasRoleFromRequestAttribute(request, "ADMIN")) {
            return;
        }
        auditHelper.logDenied(request, action, resourceType, resourceId, tenantId);
        throw new SecurityException("Admin role required for this operation");
    }

    private static boolean hasRoleFromRequestAttribute(HttpServletRequest request, String role) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return roles.stream().anyMatch(r -> r != null && role.equalsIgnoreCase(r.toString().trim()));
        } else if (rolesAttr instanceof String rolesStr) {
            for (String r : rolesStr.split(",")) {
                if (role.equalsIgnoreCase(r.trim())) return true;
            }
        }
        return false;
    }
}
