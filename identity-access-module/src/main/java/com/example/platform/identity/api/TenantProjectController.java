package com.example.platform.identity.api;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.app.TenantProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/identity")
public class TenantProjectController {

    private final TenantProjectService service;

    public TenantProjectController(TenantProjectService service) {
        this.service = service;
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
        return service.getProject(projectId);
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        return pd;
    }
}
