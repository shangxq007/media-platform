package com.example.platform.shared.authorization;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the shared-kernel authorization value types and the
 * {@link AuthorizationDecisionPort#requireAuthorized} default semantics.
 */
class AuthorizationContractTest {

    @Test
    void canonicalActorFactoriesAndImmutability() {
        CanonicalActor user = CanonicalActor.user("u-1", "t-1", Set.of("WRITE"), "jwt");
        assertEquals(ActorType.USER, user.actorType());
        assertEquals("u-1", user.actorId());
        assertEquals("t-1", user.tenantId());
        assertTrue(user.roles().contains("WRITE"));
        assertThrows(Exception.class, () -> user.roles().add("x"));

        CanonicalActor apiKey = CanonicalActor.apiKey("k-1", "t-1", Set.of(), "api-key");
        assertEquals(ActorType.API_KEY_PRINCIPAL, apiKey.actorType());

        CanonicalActor system = CanonicalActor.system("sched", null);
        assertEquals(ActorType.SYSTEM, system.actorType());
        assertNull(system.tenantId());
        assertTrue(system.isSystem());
    }

    @Test
    void canonicalActorNullGuards() {
        assertThrows(NullPointerException.class, () -> new CanonicalActor(null, ActorType.USER, "t", Set.of(), "x"));
        assertThrows(NullPointerException.class, () -> new CanonicalActor("id", null, "t", Set.of(), "x"));
    }

    @Test
    void actionRejectsBlankKey() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizationAction("", AuthorizationResourceType.WORKFLOW_DEFINITION, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizationAction("k", AuthorizationResourceType.WORKFLOW_DEFINITION, ""));
    }

    @Test
    void typedActionsCarryFrozenKeys() {
        AuthorizationAction edit = AuthorizationActions.WORKFLOW_DEFINITION_EDIT.action();
        assertEquals("workflow-definition.edit", edit.permissionKey());
        assertEquals(AuthorizationResourceType.WORKFLOW_DEFINITION, edit.resourceType());

        AuthorizationAction read = AuthorizationActions.WORKFLOW_DEFINITION_READ.action();
        assertEquals("workflow-definition.read", read.permissionKey());
    }

    @Test
    void resourceRefRequiresTenant() {
        AuthorizableResourceRef ref = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "t-1");
        assertEquals("t-1", ref.tenantId());
        assertEquals("def-1", ref.resourceId());
        assertNull(ref.projectId());
        assertNull(ref.ownerId());
        AuthorizableResourceRef withProject = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, "def-1", "t-1", "p-1", "o-1");
        assertEquals("p-1", withProject.projectId());
        assertEquals("o-1", withProject.ownerId());
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizableResourceRef(AuthorizationResourceType.WORKFLOW_DEFINITION, "x", null));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthorizableResourceRef(AuthorizationResourceType.WORKFLOW_DEFINITION, "x", "  "));
    }

    @Test
    void contextSignalsAreImmutable() {
        AuthorizationContext ctx = new AuthorizationContext("web", "ws-1", Map.of("k", "v"));
        assertEquals("web", ctx.requestSource());
        assertEquals("ws-1", ctx.workspaceId());
        assertThrows(UnsupportedOperationException.class, () -> ctx.additionalReadOnlySignals().put("x", "y"));
    }

    @Test
    void requireAuthorizedThrowsOnDenyAndCarriesDecision() {
        AuthorizationDecisionPort denying = req -> AuthorizationDecision.deny("RBAC_DENY", "RBAC", "no perm");
        AuthorizationRequest request = request("u-1", "t-1",
                AuthorizationActions.WORKFLOW_DEFINITION_EDIT.action(), "def-1", "t-1");

        AuthorizationDeniedException ex = assertThrows(AuthorizationDeniedException.class,
                () -> denying.requireAuthorized(request));
        assertFalse(ex.decision().allowed());
        assertEquals("RBAC_DENY", ex.decision().reasonCode());
        assertFalse(ex.isTenantBoundary());
        assertEquals(403, ex.getErrorCode().status());
    }

    @Test
    void tenantBoundaryDenialFlaggedFor404Translation() {
        AuthorizationDecisionPort crossTenant = req -> AuthorizationDecision.deny("TENANT_BOUNDARY", "TENANT_BOUNDARY");
        AuthorizationRequest request = request("u-1", "t-2",
                AuthorizationActions.WORKFLOW_DEFINITION_READ.action(), "def-1", "t-1");

        AuthorizationDeniedException ex = assertThrows(AuthorizationDeniedException.class,
                () -> crossTenant.requireAuthorized(request));
        assertTrue(ex.isTenantBoundary());
    }

    @Test
    void requireAuthorizedReturnsDecisionOnAllow() {
        AuthorizationDecisionPort allowing = req -> AuthorizationDecision.allow("RBAC");
        AuthorizationRequest request = request("u-1", "t-1",
                AuthorizationActions.WORKFLOW_DEFINITION_READ.action(), "def-1", "t-1");
        AuthorizationDecision decision = allowing.requireAuthorized(request);
        assertTrue(decision.allowed());
    }

    private static AuthorizationRequest request(String actorId, String actorTenant,
                                                 AuthorizationAction action, String resourceId, String resourceTenant) {
        CanonicalActor actor = CanonicalActor.user(actorId, actorTenant, Set.of("WRITE"), "jwt");
        AuthorizableResourceRef resource = new AuthorizableResourceRef(
                AuthorizationResourceType.WORKFLOW_DEFINITION, resourceId, resourceTenant);
        return new AuthorizationRequest(actor, action, resource, new AuthorizationContext("web"));
    }
}
