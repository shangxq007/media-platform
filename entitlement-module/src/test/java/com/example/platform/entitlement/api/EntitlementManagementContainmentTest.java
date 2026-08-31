package com.example.platform.entitlement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.entitlement.app.AccessDecisionService;
import com.example.platform.entitlement.app.EntitlementPolicyService;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.app.WorkspaceEntitlementPoolService;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EntitlementManagementContainmentTest {

    @Test
    void policyTierAndWorkspacePoolMutationsDenyBeforeRequestAuthorityIsUsed() {
        EntitlementService entitlement = mock(EntitlementService.class);
        EntitlementPolicyService policy = mock(EntitlementPolicyService.class);
        AccessDecisionService access = mock(AccessDecisionService.class);
        WorkspaceEntitlementPoolService pool = mock(WorkspaceEntitlementPoolService.class);
        EntitlementController controller = new EntitlementController(entitlement, policy, access);
        WorkspaceEntitlementPoolController poolController =
                new WorkspaceEntitlementPoolController(pool);
        Instant now = Instant.parse("2026-08-31T00:00:00Z");

        assertUnavailable(controller::refreshPolicies);
        assertUnavailable(() -> controller.setTenantTier(
                "request-tenant", new EntitlementController.TierUpdateRequest("ENTERPRISE")));
        assertUnavailable(() -> poolController.allocate(
                "workspace",
                new WorkspaceEntitlementPoolController.AllocateRequest(
                        "feature", "member", 10, now, now.plusSeconds(60),
                        "source", "idempotency", "reason", "trace"),
                "request-header-actor"));
        assertUnavailable(() -> poolController.reclaim(
                "workspace",
                new WorkspaceEntitlementPoolController.ReclaimRequest("member", "feature", 10),
                "request-header-actor"));

        verifyNoInteractions(entitlement, policy, access, pool);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
