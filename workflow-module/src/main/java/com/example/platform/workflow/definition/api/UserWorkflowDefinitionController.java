package com.example.platform.workflow.definition.api;

import com.example.platform.shared.authorization.AuthorizationActions;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.shared.web.CommonErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionArchiveRequest;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionCreateRequest;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionCreateVersionRequest;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionDto;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionPublishRequest;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionUpdateRequest;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionValidateRequest;
import com.example.platform.workflow.definition.app.UserWorkflowDefinitionService;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowErrorCode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowException;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionEdge;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionNode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.domain.UserWorkflowParameterDeclaration;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.example.platform.workflow.definition.domain.WorkflowNodeType;
import com.example.platform.workflow.definition.validation.UserWorkflowValidationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * W2 V1 public API — exactly the 9 frozen routes under /api
 * (public-api-contract.tsv). Route convention matches render-module
 * RenderController (/api + /tenants/{tenantId}/...). Domain entities are
 * never exposed directly; DTOs only. Errors render as ProblemDetail with the
 * frozen WORKFLOW-<status>-<seq> errorCode property (platform GlobalException
 * Handler response shape).
 *
 * <p><strong>Authorization (APPD-CHV1):</strong> each of the nine operations is
 * guarded at the application boundary by the canonical
 * {@link AuthorizationDecisionPort} (resolved here through the shared-kernel
 * {@link CanonicalActorResolver} port). The frozen Modulith boundary of
 * workflow-module excludes identity-access-module, so the controller depends only
 * on the shared-kernel authorization contract — the RBAC-backed
 * {@code AuthorizationDecisionPort} implementation is injected at runtime. The
 * path {@code tenantId} is treated as authoritative for the resource; the
 * authorization layer enforces the tenant-boundary default-deny, and cross-tenant
 * denial is surfaced as 404 (no existence leak), consistent with the service +
 * repository tenant scoping underneath.</p>
 *
 * <p>Missing canonical actor context fails closed. Security-disabled HTTP mode
 * does not grant workflow authority.</p>
 */
@RestController
@RequestMapping("/api")
public class UserWorkflowDefinitionController {

    /** Frozen W2 permission keys (authorization-contract.tsv). */
    public static final String PERMISSION_EDIT = "workflow-definition.edit";
    public static final String PERMISSION_PUBLISH = "workflow-definition.publish";
    public static final String PERMISSION_ARCHIVE = "workflow-definition.archive";
    public static final String PERMISSION_READ = "workflow-definition.read";

    private final UserWorkflowDefinitionService service;
    private final CanonicalActorResolver actorResolver;
    private final AuthorizationDecisionPort authorizationPort;

    public UserWorkflowDefinitionController(UserWorkflowDefinitionService service,
                                             CanonicalActorResolver actorResolver,
                                             AuthorizationDecisionPort authorizationPort) {
        this.service = service;
        this.actorResolver = actorResolver;
        this.authorizationPort = authorizationPort;
    }

    // ── authorization boundary ─────────────────────────────────────────────

    /**
     * Enforce authorization for {@code action} on the given resource scope.
     *
     * <p>If no authenticated actor resolves, a typed authentication failure is thrown.
     * If an actor is present but denied, an {@link AuthorizationDeniedException} is thrown: a tenant-boundary
     * (cross-tenant) denial is translated to the existing 404
     * {@code DEFINITION_NOT_FOUND} so existence is never leaked across tenants;
     * any other denial surfaces as 403.</p>
     */
    private void authorize(String tenantId, AuthorizationActions action, String resourceId) {
        CanonicalActor actor = requireActor();
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, resourceId, tenantId);
        AuthorizationRequest request = new AuthorizationRequest(
                actor, action.action(), resource,
                new AuthorizationContext("web"));
        try {
            authorizationPort.requireAuthorized(request);
        } catch (AuthorizationDeniedException ex) {
            if (ex.isTenantBoundary()) {
                throw new UserWorkflowException(UserWorkflowErrorCode.Code.DEFINITION_NOT_FOUND,
                        "definition not found: " + (resourceId != null ? resourceId : "<new>"));
            }
            throw ex;
        }
    }

    private void authorize(String tenantId, AuthorizationActions action) {
        authorize(tenantId, action, null);
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @PostMapping("/tenants/{tenantId}/workflow-definitions")
    public ResponseEntity<UserWorkflowDefinitionDto> create(
            @PathVariable String tenantId,
            @RequestBody UserWorkflowDefinitionCreateRequest request) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_EDIT);
        UserWorkflowDefinition created = service.create(
                tenantId, request.projectId(), request.name(), request.description(),
                toNodes(request), toEdges(request.edges()),
                toParameters(request.parameters()), toTrigger(request.trigger()),
                request.schemaVersion(), principalId());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserWorkflowDefinitionDto.from(created));
    }

    // ── LIST ───────────────────────────────────────────────────────────────

    @GetMapping("/tenants/{tenantId}/workflow-definitions")
    public List<UserWorkflowDefinitionDto> list(
            @PathVariable String tenantId,
            @RequestParam(required = false) String projectId) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_READ);
        return service.list(tenantId, projectId).stream()
                .map(UserWorkflowDefinitionDto::from)
                .toList();
    }

    // ── GET LATEST ─────────────────────────────────────────────────────────

    @GetMapping("/tenants/{tenantId}/workflow-definitions/{definitionId}")
    public UserWorkflowDefinitionDto getLatest(
            @PathVariable String tenantId,
            @PathVariable String definitionId) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_READ, definitionId);
        return UserWorkflowDefinitionDto.from(
                service.get(tenantId, UserWorkflowDefinitionId.of(definitionId)));
    }

    // ── GET EXACT VERSION ──────────────────────────────────────────────────

    @GetMapping("/tenants/{tenantId}/workflow-definitions/{definitionId}/versions/{versionNumber}")
    public UserWorkflowDefinitionDto getVersion(
            @PathVariable String tenantId,
            @PathVariable String definitionId,
            @PathVariable int versionNumber) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_READ, definitionId);
        return UserWorkflowDefinitionDto.from(
                service.getVersion(tenantId, UserWorkflowDefinitionId.of(definitionId),
                        UserWorkflowDefinitionVersion.of(versionNumber)));
    }

    // ── UPDATE DRAFT ───────────────────────────────────────────────────────

    @PutMapping("/tenants/{tenantId}/workflow-definitions/{definitionId}/versions/{versionNumber}")
    public UserWorkflowDefinitionDto updateDraft(
            @PathVariable String tenantId,
            @PathVariable String definitionId,
            @PathVariable int versionNumber,
            @RequestBody UserWorkflowDefinitionUpdateRequest request) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_EDIT, definitionId);
        UserWorkflowDefinition updated = service.updateDraft(
                tenantId, UserWorkflowDefinitionId.of(definitionId),
                UserWorkflowDefinitionVersion.of(versionNumber),
                request.optimisticVersion(), request.name(), request.description(),
                toNodes(request), toEdges(request.edges()),
                toParameters(request.parameters()), toTrigger(request.trigger()),
                principalId());
        return UserWorkflowDefinitionDto.from(updated);
    }

    // ── VALIDATE ───────────────────────────────────────────────────────────

    @PostMapping("/tenants/{tenantId}/workflow-definitions/{definitionId}/versions/{versionNumber}/validate")
    public UserWorkflowValidationResult validate(
            @PathVariable String tenantId,
            @PathVariable String definitionId,
            @PathVariable int versionNumber,
            @RequestBody(required = false) UserWorkflowDefinitionValidateRequest request) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_EDIT, definitionId);
        return service.validate(tenantId, UserWorkflowDefinitionId.of(definitionId),
                UserWorkflowDefinitionVersion.of(versionNumber), principalId());
    }

    // ── PUBLISH ────────────────────────────────────────────────────────────

    @PostMapping("/tenants/{tenantId}/workflow-definitions/{definitionId}/versions/{versionNumber}/publish")
    public UserWorkflowDefinitionDto publish(
            @PathVariable String tenantId,
            @PathVariable String definitionId,
            @PathVariable int versionNumber,
            @RequestBody UserWorkflowDefinitionPublishRequest request) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_PUBLISH, definitionId);
        return UserWorkflowDefinitionDto.from(service.publish(
                tenantId, UserWorkflowDefinitionId.of(definitionId),
                UserWorkflowDefinitionVersion.of(versionNumber),
                request.optimisticVersion(), principalId()));
    }

    // ── CREATE VERSION ─────────────────────────────────────────────────────

    @PostMapping("/tenants/{tenantId}/workflow-definitions/{definitionId}/versions")
    public ResponseEntity<UserWorkflowDefinitionDto> createVersion(
            @PathVariable String tenantId,
            @PathVariable String definitionId,
            @RequestBody(required = false) UserWorkflowDefinitionCreateVersionRequest request) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_EDIT, definitionId);
        UserWorkflowDefinition next = service.createVersion(
                tenantId, UserWorkflowDefinitionId.of(definitionId),
                request == null ? null : request.sourceVersion(), principalId());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserWorkflowDefinitionDto.from(next));
    }

    // ── ARCHIVE ────────────────────────────────────────────────────────────

    @PostMapping("/tenants/{tenantId}/workflow-definitions/{definitionId}/versions/{versionNumber}/archive")
    public UserWorkflowDefinitionDto archive(
            @PathVariable String tenantId,
            @PathVariable String definitionId,
            @PathVariable int versionNumber,
            @RequestBody UserWorkflowDefinitionArchiveRequest request) {
        authorize(tenantId, AuthorizationActions.WORKFLOW_DEFINITION_ARCHIVE, definitionId);
        return UserWorkflowDefinitionDto.from(service.archive(
                tenantId, UserWorkflowDefinitionId.of(definitionId),
                UserWorkflowDefinitionVersion.of(versionNumber),
                request.optimisticVersion(), principalId()));
    }

    // ── error translation ──────────────────────────────────────────────────

    @ExceptionHandler(UserWorkflowException.class)
    public ProblemDetail handleWorkflow(UserWorkflowException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(ex.errorCode().status()), ex.getMessage());
        problem.setTitle(ex.errorCode().title());
        problem.setType(URI.create("https://example.com/problems/" + ex.errorCode().codeString()));
        problem.setProperty("errorCode", ex.errorCode().codeString());
        problem.setProperty("title", ex.errorCode().title());
        problem.setProperty("status", ex.errorCode().status());
        problem.setProperty("detail", ex.getMessage());
        if (ex instanceof UserWorkflowDefinitionService.ValidationFailedException vfe) {
            problem.setProperty("validationIssues", vfe.validationResult().issues().stream()
                    .map(i -> Map.of(
                            "issueCode", i.issueCode().name(),
                            "errorCode", i.issueCode().errorCode().codeString(),
                            "message", i.message(),
                            "severity", i.severity().name()))
                    .toList());
        }
        return problem;
    }

    // ── mapping helpers ────────────────────────────────────────────────────

    private List<UserWorkflowDefinitionNode> toNodes(UserWorkflowDefinitionCreateRequest request) {
        return request.nodes() == null ? List.of()
                : request.nodes().stream().map(this::toNode).toList();
    }

    private List<UserWorkflowDefinitionNode> toNodes(UserWorkflowDefinitionUpdateRequest request) {
        return request.nodes() == null ? List.of()
                : request.nodes().stream().map(this::toNode).toList();
    }

    private UserWorkflowDefinitionNode toNode(UserWorkflowDefinitionDto.NodeDto n) {
        WorkflowNodeType type;
        try {
            type = WorkflowNodeType.valueOf(n.nodeType());
        } catch (IllegalArgumentException e) {
            throw new UserWorkflowException(UserWorkflowErrorCode.Code.INVALID_NODE_TYPE_CONFIGURATION,
                    "unknown node type: " + n.nodeType());
        }
        UserWorkflowDefinitionNode.ErrorPolicy policy;
        try {
            policy = UserWorkflowDefinitionNode.ErrorPolicy.valueOf(n.errorPolicy());
        } catch (IllegalArgumentException e) {
            policy = UserWorkflowDefinitionNode.ErrorPolicy.FAIL;
        }
        return new UserWorkflowDefinitionNode(
                n.nodeId(), type, n.name(), n.configSchemaRef(),
                new UserWorkflowDefinitionNode.VersionedJsonDocument(
                        1, UserWorkflowDefinitionDto.canonicalConfig(n.configValues())),
                toParameters(n.inputDeclarations()), toParameters(n.outputDeclarations()), policy);
    }

    private List<UserWorkflowDefinitionEdge> toEdges(List<UserWorkflowDefinitionDto.EdgeDto> edges) {
        return edges == null ? List.of()
                : edges.stream().map(e -> new UserWorkflowDefinitionEdge(
                        e.edgeId(), e.sourceNodeId(), e.targetNodeId(),
                        e.conditionRef() == null ? "" : e.conditionRef(), e.sortOrder())).toList();
    }

    private List<UserWorkflowParameterDeclaration> toParameters(
            List<UserWorkflowDefinitionDto.ParameterDto> parameters) {
        return parameters == null ? List.of()
                : parameters.stream().map(p -> new UserWorkflowParameterDeclaration(
                        p.parameterId(), p.name(), p.type(), p.schemaRef(),
                        p.required(), p.defaultValueRef())).toList();
    }

    private UserWorkflowTriggerBinding toTrigger(UserWorkflowDefinitionDto.TriggerDto t) {
        if (t == null) {
            return UserWorkflowTriggerBinding.manual();
        }
        return new UserWorkflowTriggerBinding(
                UserWorkflowTriggerBinding.TriggerType.valueOf(t.triggerType()),
                t.referenceId(), t.referenceVersion());
    }

    private String principalId() {
        return requireActor().actorId();
    }

    private CanonicalActor requireActor() {
        return actorResolver.resolveCurrentActor().orElseThrow(() ->
                new PlatformException(CommonErrorCode.AUTHENTICATION_REQUIRED));
    }
}
