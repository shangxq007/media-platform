package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.entitlement.domain.WorkspaceQuotaAllocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class WorkspaceQuotaAllocationServiceTest {

    private WorkspaceQuotaAllocationService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceQuotaAllocationService(null, null);
    }

    @Test
    void allocateReturnsAllocationWithId() {
        WorkspaceQuotaAllocation result = service.allocate(
                "ws-1", "member-1", "pro-quota", 500, "MONTHLY", "admin");

        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("ws-1", result.workspaceId());
        assertEquals("member-1", result.memberId());
        assertEquals("pro-quota", result.quotaProfileKey());
        assertEquals(500, result.allocatedAmount());
        assertEquals(0, result.usedAmount());
    }

    @Test
    void getAllocationReturnsEmptyWithoutRepository() {
        Optional<WorkspaceQuotaAllocation> result = service.getAllocation("ws-1", "member-1");
        assertTrue(result.isEmpty());
    }

    @Test
    void getWorkspaceAllocationsReturnsEmptyWithoutRepository() {
        List<WorkspaceQuotaAllocation> result = service.getWorkspaceAllocations("ws-1");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void recordUsageDoesNotThrowWithoutRepository() {
        assertDoesNotThrow(() ->
                service.recordUsage("ws-1", "member-1", 10, "admin"));
    }

    @Test
    void reclaimDoesNotThrowWithoutRepository() {
        assertDoesNotThrow(() ->
                service.reclaim("ws-1", "member-1", 50, "admin"));
    }
}
