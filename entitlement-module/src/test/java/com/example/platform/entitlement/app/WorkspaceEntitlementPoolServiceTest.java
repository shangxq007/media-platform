package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class WorkspaceEntitlementPoolServiceTest {

    private WorkspaceEntitlementPoolService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceEntitlementPoolService(null, null, null);
    }

    @Test
    void getPoolReturnsEmptyWithoutRepository() {
        List<WorkspaceEntitlementPool> pools = service.getPool("ws-1");
        assertNotNull(pools);
        assertTrue(pools.isEmpty());
    }

    @Test
    void getPoolForFeatureThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.getPoolForFeature("ws-1", "render"));
    }

    @Test
    void createPoolReturnsPoolWithoutRepository() {
        WorkspaceEntitlementPool result = service.createPool("ws-1", "render", 1000, "MONTHLY", "admin");
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("ws-1", result.workspaceId());
        assertEquals("render", result.featureKey());
        assertEquals(1000, result.totalQuota());
    }

    @Test
    void reclaimFromMemberDoesNotThrowWithoutRepository() {
        assertDoesNotThrow(() ->
                service.reclaimFromMember("ws-1", "member-1", "render", 50, "admin"));
    }

    @Test
    void getMemberGrantsUsesCanonicalScopedQueryIncludingNonemptyResults() {
        var owner = org.mockito.Mockito.mock(EntitlementService.class);
        var grant = new com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant(
                "grant", "ws-1", "user", "render", 1, java.time.Instant.EPOCH, null, "ACTIVE", "actor", java.time.Instant.EPOCH, java.time.Instant.EPOCH);
        org.mockito.Mockito.when(owner.listWorkspaceGrants("tenant", "ws-1")).thenReturn(List.of(grant));
        service = new WorkspaceEntitlementPoolService(null, owner, null);
        assertEquals(List.of(grant), service.getMemberGrants("tenant", "ws-1"));
        org.mockito.Mockito.verify(owner).listWorkspaceGrants("tenant", "ws-1");
    }
}
