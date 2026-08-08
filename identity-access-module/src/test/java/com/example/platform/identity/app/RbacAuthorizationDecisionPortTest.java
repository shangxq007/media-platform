package com.example.platform.identity.app;

import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.AuthorizationActions;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link RbacAuthorizationDecisionPort} — tenant default-deny
 * (Layer 0) and RBAC (Layer 1) WITHOUT a DB (PermissionService is mocked).
 * AUTH-RED-001/002/003/004/005/006 evidence.
 */
class RbacAuthorizationDecisionPortTest {

    private final PermissionService permissionService = mock(PermissionService.class);
    private final RbacAuthorizationDecisionPort port = new RbacAuthorizationDecisionPort(permissionService);

    @Test
    void crossTenantDeniedEvenWithPermission_AUTHORIZATION_RED_001() {
        CanonicalActor actor = CanonicalActor.user("u-1", "tenant-a", Set.of("ADMIN"), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-b");
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_READ.action(), resource, new AuthorizationContext("web"));

        // Even though RBAC would grant it, cross-tenant must be DENY.
        when(permissionService.hasPermission("u-1", "tenant-b", "workflow-definition.read")).thenReturn(true);

        AuthorizationDecision decision = port.decide(request);

        assertFalse(decision.allowed());
        assertEquals("TENANT_BOUNDARY", decision.reasonCode());
        verifyNoInteractions(permissionService);
    }

    @Test
    void roleWithoutPermissionDenied_AUTHORIZATION_RED_002() {
        CanonicalActor actor = CanonicalActor.user("u-1", "tenant-a", Set.of("VIEWER"), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_PUBLISH.action(), resource, new AuthorizationContext("web"));

        when(permissionService.hasPermission("u-1", "tenant-a", "workflow-definition.publish")).thenReturn(false);

        AuthorizationDecision decision = port.decide(request);

        assertFalse(decision.allowed());
        assertEquals("RBAC_DENY", decision.reasonCode());
    }

    @Test
    void sameTenantWithPermissionAllowed() {
        CanonicalActor actor = CanonicalActor.user("admin-1", "tenant-a", Set.of("ADMIN"), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_EDIT.action(), resource, new AuthorizationContext("web"));

        when(permissionService.hasPermission("admin-1", "tenant-a", "workflow-definition.edit")).thenReturn(true);

        AuthorizationDecision decision = port.decide(request);

        assertTrue(decision.allowed());
        assertEquals("RBAC", decision.ruleRef());
    }

    @Test
    void entitlementSnapshotInContextDoesNotGrantAuthorization_AUTHORIZATION_RED_003() {
        // Context carries an "entitlement" signal, but authorization is decided ONLY by
        // RBAC. An actor without the permission is denied regardless of the signal.
        CanonicalActor actor = CanonicalActor.user("u-1", "tenant-a", Set.of(), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationContext ctx = new AuthorizationContext("web", "ws-1",
                Map.of("entitlement.granted", "true", "feature.enabled", "true"));
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_PUBLISH.action(), resource, ctx);

        when(permissionService.hasPermission("u-1", "tenant-a", "workflow-definition.publish")).thenReturn(false);

        AuthorizationDecision decision = port.decide(request);
        assertFalse(decision.allowed());
        assertEquals("RBAC_DENY", decision.reasonCode());
    }

    @Test
    void featureFlagInContextDoesNotGrantAuthorization_AUTHORIZATION_RED_004() {
        CanonicalActor actor = CanonicalActor.user("u-1", "tenant-a", Set.of(), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationContext ctx = new AuthorizationContext("web", null,
                Map.of("feature-flag:workflow.publish", "true"));
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_PUBLISH.action(), resource, ctx);

        when(permissionService.hasPermission("u-1", "tenant-a", "workflow-definition.publish")).thenReturn(false);

        assertFalse(port.decide(request).allowed());
    }

    @Test
    void capabilityInContextDoesNotGrantAuthorization_AUTHORIZATION_RED_005() {
        CanonicalActor actor = CanonicalActor.user("u-1", "tenant-a", Set.of(), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationContext ctx = new AuthorizationContext("web", null,
                Map.of("capability", "workflow.publish"));
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_PUBLISH.action(), resource, ctx);

        when(permissionService.hasPermission("u-1", "tenant-a", "workflow-definition.publish")).thenReturn(false);

        assertFalse(port.decide(request).allowed());
    }

    @Test
    void systemActorDeniedWithoutExplicitPolicy_AUTHORIZATION_RED_006() {
        // SYSTEM is not a universal implicit allow. A non-system. permission key is denied.
        CanonicalActor system = CanonicalActor.system("system:gc", "tenant-a");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationRequest request = new AuthorizationRequest(system,
                AuthorizationActions.WORKFLOW_DEFINITION_ARCHIVE.action(), resource, new AuthorizationContext("system"));

        AuthorizationDecision decision = port.decide(request);
        assertFalse(decision.allowed());
        assertEquals("SYSTEM_NOT_AUTHORIZED", decision.reasonCode());
    }

    @Test
    void systemActorAllowedOnlyForExplicitSystemPermission() {
        CanonicalActor system = CanonicalActor.system("system:admin", "tenant-a");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationRequest request = new AuthorizationRequest(system,
                com.example.platform.shared.authorization.AuthorizationActions
                        .WORKFLOW_DEFINITION_READ.action(),
                resource, new AuthorizationContext("system"));
        // Use a system. key to exercise the explicit system policy path.
        var systemAction = new com.example.platform.shared.authorization.AuthorizationAction(
                "system.workflow-definition.read", AuthorizationResourceType.WORKFLOW_DEFINITION, "System read");
        AuthorizationRequest systemKeyRequest = new AuthorizationRequest(system, systemAction, resource, new AuthorizationContext("system"));
        when(permissionService.hasPermission("system:admin", "tenant-a", "system.workflow-definition.read")).thenReturn(true);

        AuthorizationDecision systemDecision = port.decide(systemKeyRequest);
        assertTrue(systemDecision.allowed());
        assertEquals("SYSTEM_POLICY", systemDecision.ruleRef());
    }

    @Test
    void nullActorTenantDenied() {
        CanonicalActor actor = CanonicalActor.user("u-1", null, Set.of("ADMIN"), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_READ.action(), resource, new AuthorizationContext("web"));

        assertFalse(port.decide(request).allowed());
        assertEquals("TENANT_BOUNDARY", decisionReason(port, request));
    }

    @Test
    void rbacEvaluationErrorFailsClosed() {
        CanonicalActor actor = CanonicalActor.user("u-1", "tenant-a", Set.of("ADMIN"), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "tenant-a");
        AuthorizationRequest request = new AuthorizationRequest(actor,
                AuthorizationActions.WORKFLOW_DEFINITION_READ.action(), resource, new AuthorizationContext("web"));
        when(permissionService.hasPermission(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("DB down"));

        AuthorizationDecision decision = port.decide(request);
        assertFalse(decision.allowed());
        assertEquals("RBAC_ERROR", decision.reasonCode());
    }

    private String decisionReason(RbacAuthorizationDecisionPort port, AuthorizationRequest request) {
        return port.decide(request).reasonCode();
    }
}
