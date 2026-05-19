package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    void allocateToMemberThrowsWithoutRepository() {
        assertThrows(IllegalStateException.class, () ->
                service.allocateToMember("ws-1", "render", "member-1",
                        100, Instant.now(), null, "admin"));
    }

    @Test
    void reclaimFromMemberDoesNotThrowWithoutRepository() {
        assertDoesNotThrow(() ->
                service.reclaimFromMember("ws-1", "member-1", "render", 50, "admin"));
    }

    @Test
    void getMemberGrantsReturnsEmptyWithoutRepository() {
        var grants = service.getMemberGrants("ws-1");
        assertNotNull(grants);
        assertTrue(grants.isEmpty());
    }
}
