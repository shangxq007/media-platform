package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.domain.*;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TenantProjectService {

    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IdentityAccessService identityAccessService;

    public TenantProjectService(TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            IdentityAccessService identityAccessService) {
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.identityAccessService = identityAccessService;
    }

    public TenantResponse createTenant(CreateTenantRequest request) {
        String id = Ids.newId("ten");
        Tenant tenant = new Tenant(id, request.name(), Tenant.TenantStatus.ACTIVE, Instant.now());
        tenantRepository.save(tenant);
        return TenantResponse.from(tenant);
    }

    public TenantResponse getTenant(String tenantId) {
        assertTenantAccess(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        return TenantResponse.from(tenant);
    }

    public ProjectResponse createProject(String tenantId, CreateProjectRequest request) {
        assertTenantAccess(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        String id = Ids.newId("prj");
        Project project = new Project(id, tenantId, request.name(),
                request.description() != null ? request.description() : "",
                Project.ProjectStatus.ACTIVE, Instant.now());
        projectRepository.save(project);
        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> listProjects(String tenantId) {
        assertTenantAccess(tenantId);
        return projectRepository.findByTenantId(tenantId).stream()
                .map(ProjectResponse::from)
                .collect(Collectors.toList());
    }

    public ProjectResponse getProject(String projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        assertTenantAccess(project.tenantId());
        return ProjectResponse.from(project);
    }

    public UserResponse createUser(String tenantId, CreateUserRequest request) {
        assertTenantAccess(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        String id = Ids.newId("usr");
        User user = new User(id, tenantId, request.username(), request.email(),
                request.roleOrDefault(), User.UserStatus.ACTIVE, Instant.now());
        userRepository.save(user);
        return UserResponse.from(user);
    }

    public List<UserResponse> listUsers(String tenantId) {
        assertTenantAccess(tenantId);
        return userRepository.findByTenantId(tenantId).stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
    }

    public CreateApiKeyResponse createApiKey(String tenantId, CreateApiKeyRequest request) {
        assertTenantAccess(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        String plainKey = "ak_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String hashedKey = identityAccessService.hashApiKey(plainKey);
        String fingerprint = identityAccessService.fingerprint(plainKey);
        String id = Ids.newId("ak");
        ApiKeyRecord record = new ApiKeyRecord(id, tenantId, fingerprint, hashedKey,
                request.principal(), Instant.now(), null, null);
        identityAccessService.storeRecord(record);
        return new CreateApiKeyResponse(id, plainKey, fingerprint, request.principal(), Instant.now());
    }

    public List<ApiKeySummaryResponse> listApiKeys(String tenantId) {
        assertTenantAccess(tenantId);
        return identityAccessService.listRecords().stream()
                .filter(record -> tenantId.equals(record.tenantId()))
                .map(ApiKeySummaryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * List all tenants for platform admin. No TenantContext restriction —
     * caller must have ADMIN role (enforced by controller).
     *
     * @param limit max number to return (clamped to [1, 500])
     */
    public List<TenantResponse> listAllTenants(int limit) {
        return tenantRepository.findAll(limit).stream()
                .map(TenantResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Verifies that the current TenantContext matches the given tenantId.
     * Throws IllegalArgumentException (mapped to 404) on mismatch to avoid
     * leaking cross-tenant resource existence.
     */
    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant: " + tenantId);
        }
    }
}
