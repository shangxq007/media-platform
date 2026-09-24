package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.entitlement.domain.EntitlementGrantView;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import java.util.List;
import java.util.Map;
import com.example.platform.entitlement.api.collaboration.CollaborationAccessPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntitlementDecisionServiceCollaborationTest {

    private EntitlementPolicyService policyService;
    private CollaborationAccessPort collaborationAccessPort;
    private EntitlementDecisionService decisionService;
    private EntitlementService entitlementService;
    private QuotaUsageAuthority quotaUsageAuthority;

    @BeforeEach
    void setUp() {
        policyService = new EntitlementPolicyService(java.util.Optional.empty());
        collaborationAccessPort = mock(CollaborationAccessPort.class);
        entitlementService = mock(EntitlementService.class);
        quotaUsageAuthority = mock(QuotaUsageAuthority.class);
        when(quotaUsageAuthority.currentUsage(any(QuotaUsageQuery.class))).thenReturn(java.math.BigDecimal.ZERO);
        decisionService = new EntitlementDecisionService(
                policyService, entitlementService, java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.of(collaborationAccessPort),
                java.util.Optional.of(new QuotaPolicyService()), java.util.Optional.of(quotaUsageAuthority));
    }

    @Test
    void allowsAccessWhenSharedResourceGrantMatches() {
        when(collaborationAccessPort.hasSharedAccess(
                eq("tenant-1"), eq("user-2"), eq("project"), eq("proj-1"), eq("read")))
                .thenReturn(true);

        AccessCheckRequest request = new AccessCheckRequest(
                "tenant-1", null, "user-2", "USER", "user-2",
                "read", "project", "proj-1", null, null, null, "api", null, null);

        EntitlementDecision decision = decisionService.evaluate(request);

        assertTrue(decision.allowed());
        assertEquals("SHARED_RESOURCE_GRANT", decision.reasonCode());
    }
    @Test
    void directGrantPublishesRemainingQuota() {
        when(entitlementService.listGrants(any())).thenReturn(List.of(
                new EntitlementGrantView("grant-1", new PrincipalRef("tenant-1", PrincipalType.USER, "user-2", "workspace-1", null),
                        "render.job.create", null, "TEST", "test", "ACTIVE", java.time.Instant.parse("2026-01-01T00:00:00Z"), null, 7, false)));
        AccessCheckRequest request = new AccessCheckRequest("tenant-1", "workspace-1", "user-2", "USER", "user-2",
                "composition.admit", "COMPOSITION", "composition-1", "render.job.create", null, null, "WEB", null, Map.of(),
                new AccessCheckRequest.VersionedRequirement("render.job.create", "7", "composition",
                        java.time.Instant.parse("2026-09-01T00:00:00Z"), java.time.Instant.parse("2026-10-01T00:00:00Z")));
        EntitlementDecision decision = decisionService.evaluate(request);
        assertTrue(decision.allowed());
        assertEquals("7", decision.matchedGrantId().equals("grant-1") ? "7" : "");
        assertTrue(decision.quotaRemaining().signum() > 0);
    }

    @Test
    void mismatchedPublishedGrantVersionFailsClosed() {
        when(entitlementService.listGrants(any())).thenReturn(List.of(
                new EntitlementGrantView("grant-1", null, "render.job.create", null, "TEST", "test", "ACTIVE", null, null, 6, false)));
        AccessCheckRequest request = new AccessCheckRequest("tenant-1", "workspace-1", "user-2", "USER", "user-2",
                "composition.admit", "COMPOSITION", "composition-1", "render.job.create", null, null, "WEB", null, Map.of(),
                new AccessCheckRequest.VersionedRequirement("render.job.create", "7", "composition",
                        java.time.Instant.parse("2026-09-01T00:00:00Z"), java.time.Instant.parse("2026-10-01T00:00:00Z")));
        EntitlementDecision decision = decisionService.evaluate(request);
        assertTrue(!decision.allowed());
        assertEquals("DEFAULT_DENY", decision.reasonCode());
    }

}
