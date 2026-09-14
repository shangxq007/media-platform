package com.example.platform.identity.app;

import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.domain.*;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.shared.authorization.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TenantProjectService implements ProjectReadQuery {
    private final CanonicalActorResolver actors;
    private final com.example.platform.identity.api.workspace.WorkspaceQueries workspaces;
    private final AuthorizationDecisionPort authorization;
    private static final AuthorizationAction READ = new AuthorizationAction("READ", AuthorizationResourceType.PROJECT, "Read Project");

    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final IdentityAccessService identityAccessService;

    public TenantProjectService(TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            IdentityAccessService identityAccessService, CanonicalActorResolver actors, AuthorizationDecisionPort authorization, com.example.platform.identity.api.workspace.WorkspaceQueries workspaces) {
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.identityAccessService = identityAccessService;
        this.actors = java.util.Objects.requireNonNull(actors);
        this.workspaces=workspaces;
        this.authorization = java.util.Objects.requireNonNull(authorization);
    }

    public TenantResponse createTenant(CreateTenantRequest request) {
        String id = ("ten_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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

    @org.springframework.transaction.annotation.Transactional
    public ProjectResponse createProject(String tenantId, CreateProjectRequest request) {
        var actor=requireReadActor(tenantId);
        if(request.workspaceId()==null||request.workspaceId().isBlank())throw new PlatformException(CommonErrorCode.INVALID_REQUEST,"Explicit Workspace required");
        var workspace=workspaces.getWorkspace(request.workspaceId());
        if(!tenantId.equals(workspace.tenantId()))throw new PlatformException(CommonErrorCode.INSUFFICIENT_PERMISSION,"Project scope unavailable");
        authorization.requireAuthorized(new AuthorizationRequest(actor,
                new AuthorizationAction("CREATE",AuthorizationResourceType.PROJECT,"Create Project"),
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,null,tenantId),
                new AuthorizationContext("project-create",workspace.id(),Map.of())));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        String id = ("prj_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        Project project = new Project(id, tenantId, request.name(),
                request.description() != null ? request.description() : "",
                Project.ProjectStatus.ACTIVE, Instant.now(), request.workspaceId());
        projectRepository.save(project);
        return ProjectResponse.from(project);
    }

    @Override
    public List<ProjectResponse> listProjects(String tenantId) {
        CanonicalActor actor = requireReadActor(tenantId);
        return projectRepository.findByTenantId(tenantId).stream()
                .filter(project -> authorization.decide(readRequest(actor, tenantId, project.id())).allowed())
                .map(ProjectResponse::from).toList();
    }

    @Override
    public ProjectResponse getProject(String tenantId, String projectId) {
        CanonicalActor actor = requireReadActor(tenantId);
        if (projectId == null || projectId.isBlank()) throw new PlatformException(CommonErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
        authorization.requireAuthorized(readRequest(actor, tenantId, projectId));
        Project project = projectRepository.findByIdAndTenant(projectId, tenantId)
                .orElseThrow(() -> new PlatformException(CommonErrorCode.RESOURCE_NOT_FOUND, "Resource not found"));
        return ProjectResponse.from(project);
    }

    private CanonicalActor requireReadActor(String tenantId) {
        CanonicalActor actor = actors.resolveCurrentActor().orElseThrow(() -> new PlatformException(CommonErrorCode.AUTHENTICATION_REQUIRED, "Authentication required"));
        if (tenantId == null || tenantId.isBlank() || !tenantId.equals(TenantContext.get()) || !tenantId.equals(actor.tenantId()))
            throw new AuthorizationDeniedException(AuthorizationDecision.deny("TENANT_BOUNDARY", "IDENTITY", "Resource unavailable"));
        return actor;
    }

    private AuthorizationRequest readRequest(CanonicalActor actor, String tenantId, String projectId) {
        return new AuthorizationRequest(actor, READ,
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT, projectId, tenantId, projectId, null),
                new AuthorizationContext("project-read", null, Map.of()));
    }

    public UserResponse createUser(String tenantId, CreateUserRequest request) {
        assertTenantAccess(tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        String id = ("usr_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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
        String id = ("ak_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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
