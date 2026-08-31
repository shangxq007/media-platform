package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.TenantProjectService;
import com.example.platform.shared.audit.AdminAuditPublisher;
import com.example.platform.shared.authorization.FailClosedAuthorization;
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
        throw FailClosedAuthorization.unavailable("tenant creation");
    }

    @GetMapping("/tenants/{tenantId}")
    public TenantResponse getTenant(@PathVariable String tenantId) {
        return service.getTenant(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/projects")
    public ProjectResponse createProject(@PathVariable String tenantId,
            @Valid @RequestBody CreateProjectRequest request) {
        throw FailClosedAuthorization.unavailable("project creation");
    }

    @GetMapping("/tenants/{tenantId}/projects")
    public List<ProjectResponse> listProjects(@PathVariable String tenantId) {
        return service.listProjects(tenantId);
    }

    @GetMapping("/projects/{projectId}")
    public ProjectResponse getProject(@PathVariable String projectId) {
        return service.getProject(projectId);
    }

    @PostMapping("/tenants/{tenantId}/users")
    public UserResponse createUser(@PathVariable String tenantId,
            @Valid @RequestBody CreateUserRequest request) {
        throw FailClosedAuthorization.unavailable("tenant user creation");
    }

    @GetMapping("/tenants/{tenantId}/users")
    public List<UserResponse> listUsers(@PathVariable String tenantId) {
        return service.listUsers(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/apikeys")
    public CreateApiKeyResponse createApiKey(@PathVariable String tenantId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        throw FailClosedAuthorization.unavailable("API key creation");
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
        throw FailClosedAuthorization.unavailable("global tenant listing");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }
}
