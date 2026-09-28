package com.example.platform.workflow.definition.api;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.workflow.authorization.AuthorizationActions;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionCreateRequest;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionDto;
import com.example.platform.workflow.definition.api.dto.UserWorkflowDefinitionPublishRequest;
import com.example.platform.operation.operation.OperationDefinitionId;
import com.example.platform.operation.operation.OperationDefinitionVersion;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.workflow.plan.WorkflowPlan;
import com.example.platform.workflow.plan.WorkflowPlanCodec;
import com.example.platform.workflow.definition.app.UserWorkflowDefinitionService;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowErrorCode;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowException;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionId;
import com.example.platform.workflow.definition.domain.UserWorkflowDefinitionVersion;
import com.example.platform.workflow.definition.domain.UserWorkflowTriggerBinding;
import com.example.platform.workflow.definition.port.UserWorkflowDefinitionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W2 application-boundary authorization tests (APPD-CHV1). Exercises the
 * {@link UserWorkflowDefinitionController#authorize} logic with a stub
 * {@link CanonicalActorResolver} and a stub {@link AuthorizationDecisionPort} — no DB.
 *
 * <p>Covers: tenant owner/editor authorized; member without permission denied;
 * cross-tenant denied (surfaced as 404, no existence leak); publish requires
 * authorization; unauthenticated requests fail closed.</p>
 */
class UserWorkflowDefinitionAuthorizationTest {

    private static final Instant NOW = Instant.parse("2026-08-07T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final UserWorkflowDefinitionService service;
    private final UserWorkflowDefinitionController controller;

    UserWorkflowDefinitionAuthorizationTest() {
        this.service = new UserWorkflowDefinitionService(new InMemoryWorkflowRepository(), CLOCK);
        CanonicalActorResolver resolver = () ->
                Optional.of(CanonicalActor.user("owner-1", "tenant-a", Set.of("ADMIN"), "jwt"));
        AuthorizationDecisionPort port = req -> AuthorizationDecision.allow("RBAC");
        this.controller = new UserWorkflowDefinitionController(service, resolver, port);
    }

    @Test void unauthenticatedDefinitionAuthoringIsRejected() {
        var anonymous=new UserWorkflowDefinitionController(service,Optional::empty,request -> AuthorizationDecision.allow("unused"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->anonymous.create("tenant-a",createRequest("wf")));
    }

    @Test
    void authorizedOwnerCanCreate() {
        var response = controller.create("tenant-a", createRequest("wf"));
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void authorizedOwnerCanPublish() {
        UserWorkflowDefinitionDto created = controller.create("tenant-a", createRequest("wf")).getBody();
        assertNotNull(created);
        service.validate("tenant-a", UserWorkflowDefinitionId.of(created.definitionId()),
                UserWorkflowDefinitionVersion.of(created.versionNumber()), "owner-1");
        UserWorkflowDefinitionDto dto = controller.publish("tenant-a", created.definitionId(),
                created.versionNumber(), new UserWorkflowDefinitionPublishRequest(created.optimisticVersion()));
        assertNotNull(dto);
        assertEquals("PUBLISHED", dto.status());
    }

    @Test
    void memberWithoutPermissionIsDenied_403() {
        CanonicalActor member = CanonicalActor.user("member-1", "tenant-a", Set.of(), "jwt");
        ControllerWithService c = controllerWith(member,
                req -> AuthorizationDecision.deny("RBAC_DENY", "RBAC"));

        AuthorizationDeniedException ex = assertThrows(AuthorizationDeniedException.class,
                () -> c.controller().create("tenant-a", createRequest("wf")));
        assertFalse(ex.isTenantBoundary());
        assertEquals("RBAC_DENY", ex.decision().reasonCode());
    }

    @Test
    void crossTenantRead_Surfaces404_NoExistenceLeak() {
        CanonicalActor ownerATryingB = CanonicalActor.user("owner-a", "tenant-a", Set.of("ADMIN"), "jwt");
        ControllerWithService c = controllerWith(ownerATryingB, req -> {
            if (!"tenant-a".equals(req.resource().tenantId())) {
                return AuthorizationDecision.deny("TENANT_BOUNDARY", "TENANT_BOUNDARY");
            }
            return AuthorizationDecision.allow("RBAC");
        });

        UserWorkflowException ex = assertThrows(UserWorkflowException.class,
                () -> c.controller().getLatest("tenant-b", "def-1"));
        assertEquals(UserWorkflowErrorCode.Code.DEFINITION_NOT_FOUND, ex.errorCode());
    }

    @Test
    void publishRequiresAuthorization() {
        CanonicalActor viewer = CanonicalActor.user("viewer-1", "tenant-a", Set.of("VIEWER"), "jwt");
        ControllerWithService c = controllerWith(viewer, req -> {
            if ("workflow-definition.publish".equals(req.action().permissionKey())) {
                return AuthorizationDecision.deny("RBAC_DENY", "RBAC");
            }
            return AuthorizationDecision.allow("RBAC");
        });

        UserWorkflowDefinitionDto created = c.controller().create("tenant-a", createRequest("wf")).getBody();
        assertNotNull(created);
        c.service().validate("tenant-a", UserWorkflowDefinitionId.of(created.definitionId()),
                UserWorkflowDefinitionVersion.of(created.versionNumber()), "viewer-1");

        assertThrows(AuthorizationDeniedException.class, () ->
                c.controller().publish("tenant-a", created.definitionId(), created.versionNumber(),
                        new UserWorkflowDefinitionPublishRequest(created.optimisticVersion())));
    }

    @Test
    void noAuthenticatedActor_IsRejectedBeforeAuthorization() {
        UserWorkflowDefinitionController c = new UserWorkflowDefinitionController(
                service,
                () -> Optional.empty(),
                req -> {
                    fail("authorization must not be invoked when no actor is present");
                    return AuthorizationDecision.allow("RBAC");
                });

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> c.create("tenant-a",createRequest("wf")));
    }

    private ControllerWithService controllerWith(CanonicalActor actor,
                                                 Function<AuthorizationRequest, AuthorizationDecision> fn) {
        UserWorkflowDefinitionService svc = new UserWorkflowDefinitionService(new InMemoryWorkflowRepository(), CLOCK);
        return new ControllerWithService(
                new UserWorkflowDefinitionController(svc, () -> Optional.of(actor), fn::apply),
                svc);
    }

    private record ControllerWithService(UserWorkflowDefinitionController controller,
                                         UserWorkflowDefinitionService service) {}

    @SuppressWarnings("unchecked")
    private static UserWorkflowDefinitionCreateRequest createRequest(String name) {
        UserWorkflowDefinitionDto.NodeDto node = new UserWorkflowDefinitionDto.NodeDto(
                "n0", "OPERATION_INVOCATION", "node-n0", "workflow.node.v2",
                UserWorkflowDefinitionDto.parseConfig(executableNodeJson("n0")),
                List.of(), List.of(), "RETRY");
        return new UserWorkflowDefinitionCreateRequest(
                name, null, "project-a", 2, List.of(node), List.of(), List.of(),
                new UserWorkflowDefinitionDto.TriggerDto("MANUAL", null, null));
    }

    /** Executable schema-2 node JSON: a real OPERATION_INVOCATION node produced by the
     * canonical WorkflowPlanCodec so it compiles under WorkflowPlanCompiler. */
    private static String executableNodeJson(String nodeId) {
        try {
            var request = new OperationRequest(
                    new OperationDefinitionId("test.echo"),
                    OperationDefinitionVersion.V1_0,
                    new OperationTargetRequest.TimelineTargetRequest("timeline-" + nodeId),
                    new OperationParameters.NoParameters(),
                    "base", "base-hash", null);
            var node = new WorkflowPlan.Node(
                    nodeId, WorkflowPlan.Kind.OPERATION_INVOCATION,
                    null, null, 0, 0, null, request,
                    List.of(), Map.of(), new WorkflowPlan.Retry(3, 1), null);
            var plan = new WorkflowPlan(
                    1, "generated", 1, "tenant-a", "project-a", nodeId, List.of(node), List.of());
            String encoded = new WorkflowPlanCodec().encode(plan);
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(encoded).path("nodes").get(0).toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** In-memory repository port for boundary tests (no DB). */
    private static final class InMemoryWorkflowRepository implements UserWorkflowDefinitionRepository {
        private final Map<String, UserWorkflowDefinition> store = new LinkedHashMap<>();

        private static String key(UserWorkflowDefinition d) {
            return d.tenantId() + ":" + d.definitionId().value() + ":" + d.version().versionNumber();
        }

        @Override public void insertDraft(UserWorkflowDefinition d) { store.put(key(d), d); }

        @Override public Optional<UserWorkflowDefinition> findExactVersion(String t, UserWorkflowDefinitionId id, UserWorkflowDefinitionVersion v) {
            return store.values().stream().filter(d -> d.tenantId().equals(t) && d.definitionId().equals(id) && d.version().equals(v)).findFirst();
        }

        @Override public Optional<UserWorkflowDefinition> findLatest(String tenantId, UserWorkflowDefinitionId id) {
            return store.values().stream()
                    .filter(d -> d.tenantId().equals(tenantId) && d.definitionId().equals(id))
                    .max(java.util.Comparator.comparingInt(d -> d.version().versionNumber()));
        }

        @Override public List<UserWorkflowDefinition> listByTenant(String tenantId, String projectId) {
            return store.values().stream().filter(d -> d.tenantId().equals(tenantId)).toList();
        }

        @Override public UserWorkflowDefinition updateDraft(UserWorkflowDefinition d) { store.put(key(d), d); return d; }
        @Override public UserWorkflowDefinition publish(UserWorkflowDefinition d) { store.put(key(d), d); return d; }
        @Override public UserWorkflowDefinition archive(UserWorkflowDefinition d) { store.put(key(d), d); return d; }
        @Override public void insertVersion(UserWorkflowDefinition d) { store.put(key(d), d); }
    }
}
