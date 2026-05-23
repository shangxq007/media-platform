package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.AccessCheckRequest;
import com.example.platform.entitlement.domain.EntitlementDecision;
import com.example.platform.shared.collaboration.CollaborationAccessPort;
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

    @BeforeEach
    void setUp() {
        policyService = new EntitlementPolicyService(java.util.Optional.empty(), java.util.Optional.empty());
        collaborationAccessPort = mock(CollaborationAccessPort.class);
        decisionService = new EntitlementDecisionService(
                policyService, java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.of(collaborationAccessPort));
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
}
