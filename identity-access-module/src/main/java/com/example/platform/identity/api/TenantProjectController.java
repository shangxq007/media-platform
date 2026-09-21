package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.auditcontract.api.AdminAuditPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/identity")
public class TenantProjectController {

    private final TenantProjectService service;
    private final AdminAuditPublisher auditPublisher;

    public TenantProjectController(TenantProjectService service, AdminAuditPublisher auditPublisher) {
        this.service = service;
        this.auditPublisher = auditPublisher;
    }

    @PostMapping("/tenants")
    public TenantResponse createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return service.createTenant(request);
    }

    @GetMapping("/tenants/{tenantId}")
    public TenantResponse getTenant(@PathVariable String tenantId) {
        return service.getTenant(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/projects")
    public ProjectResponse createProject(@PathVariable String tenantId,
            @Valid @RequestBody CreateProjectRequest request) {
        return service.createProject(tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/projects")
    public List<ProjectResponse> listProjects(@PathVariable String tenantId) {
        return service.listProjects(tenantId);
    }

    @GetMapping("/projects/{projectId}")
    public ProjectResponse getProject(@PathVariable String projectId) {
        return service.getProject(com.example.platform.shared.web.TenantContext.get(), projectId);
    }

    @PostMapping("/tenants/{tenantId}/users")
    public UserResponse createUser(@PathVariable String tenantId,
            @Valid @RequestBody CreateUserRequest request) {
        return service.createUser(tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/users")
    public List<UserResponse> listUsers(@PathVariable String tenantId) {
        return service.listUsers(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/apikeys")
    public CreateApiKeyResponse createApiKey(@PathVariable String tenantId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        return service.createApiKey(tenantId, request);
    }

    @GetMapping("/tenants/{tenantId}/apikeys")
    public List<ApiKeySummaryResponse> listApiKeys(@PathVariable String tenantId) {
        return service.listApiKeys(tenantId);
    }

    // ========== Admin tenant list ==========

    /**
     * List all tenants (platform admin only).
     * Requires ADMIN role via SecurityHttpRules / requireAdminRole check.
     */
    @GetMapping("/admin/tenants")
    public List<TenantResponse> listAllTenants(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            @RequestParam(defaultValue = "100") int limit) {
        if (!isAdmin(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            auditPublisher.publish(
                    extractActor(request), extractRoles(request),
                    "ADMIN_LIST_TENANTS", "tenant", null, null, "DENIED");
            throw new SecurityException("Admin role required");
        }
        int effectiveLimit = Math.min(Math.max(limit, 1), 500);
        List<TenantResponse> result = service.listAllTenants(effectiveLimit);
        auditPublisher.publish(
                extractActor(request), extractRoles(request),
                "ADMIN_LIST_TENANTS", "tenant", null, null, "SUCCESS");
        return result;
    }

    /**
     * Check ADMIN role via both OAuth2 SecurityContext and Legacy JWT request attribute.
     */
    private static boolean isAdmin(jakarta.servlet.http.HttpServletRequest request) {
        // Listing all tenants requires an explicit Account-level fact, not a tenant ADMIN role.
        return Boolean.TRUE.equals(request.getAttribute("identity.platformAdministrator"));
    }

    private static String extractActor(jakarta.servlet.http.HttpServletRequest request) {
        Object subject = request.getAttribute("jwt.subject");
        return subject != null && !subject.toString().isBlank() ? subject.toString() : "anonymous";
    }

    private static String extractRoles(jakarta.servlet.http.HttpServletRequest request) {
        Object rolesAttr = request.getAttribute("jwt.roles");
        if (rolesAttr instanceof java.util.List<?> roles) {
            return String.join(",", roles.stream().map(Object::toString).toList());
        } else if (rolesAttr instanceof String rolesStr) {
            return rolesStr;
        }
        return "none";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }
}
