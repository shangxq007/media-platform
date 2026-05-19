package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.WorkspaceQuotaAllocation;
import com.example.platform.entitlement.infrastructure.WorkspaceQuotaAllocationRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkspaceQuotaAllocationService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceQuotaAllocationService.class);

    private final WorkspaceQuotaAllocationRepository allocationRepository;
    private final AuditPort auditPort;

    public WorkspaceQuotaAllocationService(
            @Autowired(required = false) WorkspaceQuotaAllocationRepository allocationRepository,
            AuditPort auditPort) {
        this.allocationRepository = allocationRepository;
        this.auditPort = auditPort;
    }

    public WorkspaceQuotaAllocation allocate(String workspaceId, String memberId,
            String quotaProfileKey, long allocatedAmount, String period, String actor) {
        String id = Ids.newId("ws_qa");
        Instant now = Instant.now();
        WorkspaceQuotaAllocation allocation = new WorkspaceQuotaAllocation(
                id, workspaceId, memberId, quotaProfileKey, allocatedAmount, 0L, period, now, now);
        if (allocationRepository != null) {
            allocationRepository.save(allocation);
        }
        audit("workspace.quota.allocated", actor, Map.of(
                "workspaceId", workspaceId, "memberId", memberId,
                "quotaProfileKey", quotaProfileKey, "allocatedAmount", allocatedAmount));
        log.info("Allocated {} quota from profile {} to member {} in workspace {}",
                allocatedAmount, quotaProfileKey, memberId, workspaceId);
        return allocation;
    }

    public void recordUsage(String workspaceId, String memberId, long usedDelta, String actor) {
        if (allocationRepository == null) return;
        Optional<WorkspaceQuotaAllocation> existing =
                allocationRepository.findByWorkspaceAndMember(workspaceId, memberId);
        if (existing.isPresent()) {
            WorkspaceQuotaAllocation alloc = existing.get();
            long newUsed = alloc.usedAmount() + usedDelta;
            allocationRepository.updateUsedAmount(alloc.id(), newUsed);
            audit("workspace.quota.usage", actor, Map.of(
                    "workspaceId", workspaceId, "memberId", memberId, "usedDelta", usedDelta));
        }
    }

    public void reclaim(String workspaceId, String memberId, long reclaimAmount, String actor) {
        if (allocationRepository == null) return;
        Optional<WorkspaceQuotaAllocation> existing =
                allocationRepository.findByWorkspaceAndMember(workspaceId, memberId);
        if (existing.isPresent()) {
            WorkspaceQuotaAllocation alloc = existing.get();
            long newUsed = Math.max(0, alloc.usedAmount() - reclaimAmount);
            allocationRepository.updateUsedAmount(alloc.id(), newUsed);
            audit("workspace.quota.reclaimed", actor, Map.of(
                    "workspaceId", workspaceId, "memberId", memberId, "reclaimAmount", reclaimAmount));
        }
    }

    public Optional<WorkspaceQuotaAllocation> getAllocation(String workspaceId, String memberId) {
        if (allocationRepository != null) {
            return allocationRepository.findByWorkspaceAndMember(workspaceId, memberId);
        }
        return Optional.empty();
    }

    public List<WorkspaceQuotaAllocation> getWorkspaceAllocations(String workspaceId) {
        if (allocationRepository != null) {
            return allocationRepository.findByWorkspaceId(workspaceId);
        }
        return List.of();
    }

    private void audit(String action, String actor, Map<String, Object> payload) {
        if (auditPort != null) {
            auditPort.record("ADMIN", action, "ENTITLEMENT",
                    "WORKSPACE_QUOTA", payload.getOrDefault("workspaceId", "unknown").toString(), payload);
        }
    }
}
