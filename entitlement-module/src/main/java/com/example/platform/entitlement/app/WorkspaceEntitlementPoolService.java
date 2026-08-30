package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import com.example.platform.entitlement.domain.EntitlementCommandType;
import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.entitlement.infrastructure.WorkspaceEntitlementPoolRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class WorkspaceEntitlementPoolService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceEntitlementPoolService.class);

    private final WorkspaceEntitlementPoolRepository poolRepository;
    private final EntitlementService entitlementService;
    private final AuditPort auditPort;

    public WorkspaceEntitlementPoolService(
            @Autowired(required = false) WorkspaceEntitlementPoolRepository poolRepository,
            EntitlementService entitlementService,
            AuditPort auditPort) {
        this.poolRepository = poolRepository;
        this.entitlementService = entitlementService;
        this.auditPort = auditPort;
    }

    public List<WorkspaceEntitlementPool> getPool(String workspaceId) {
        if (poolRepository != null) {
            return poolRepository.findByWorkspaceId(workspaceId);
        }
        return List.of();
    }

    public WorkspaceEntitlementPool getPoolForFeature(String workspaceId, String featureKey) {
        if (poolRepository != null) {
            return poolRepository.findByWorkspaceAndFeature(workspaceId, featureKey)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No entitlement pool for workspace " + workspaceId + " feature " + featureKey));
        }
        throw new IllegalStateException("No pool repository available");
    }

    public WorkspaceEntitlementPool extendPoolQuota(String workspaceId, String featureKey,
            long additionalQuota, String actor) {
        WorkspaceEntitlementPool pool = getPoolForFeature(workspaceId, featureKey);
        long newTotal = pool.totalQuota() + additionalQuota;
        if (poolRepository != null) {
            poolRepository.updateTotal(pool.id(), newTotal);
        }
        WorkspaceEntitlementPool updated = new WorkspaceEntitlementPool(
                pool.id(), pool.workspaceId(), pool.featureKey(),
                newTotal, pool.usedQuota(), pool.period(), pool.createdAt(), java.time.Instant.now());
        audit("workspace.pool.extended", actor, Map.of(
                "workspaceId", workspaceId, "featureKey", featureKey, "additionalQuota", additionalQuota,
                "newTotal", newTotal));
        log.info("Extended pool {} by {} for workspace {} feature {}", pool.id(), additionalQuota, workspaceId, featureKey);
        return updated;
    }

    public WorkspaceEntitlementPool createPool(String workspaceId, String featureKey,
            long totalQuota, String period, String actor) {
        String id = Ids.newId("ws_pool");
        Instant now = Instant.now();
        WorkspaceEntitlementPool pool = new WorkspaceEntitlementPool(
                id, workspaceId, featureKey, totalQuota, 0L, period, now, now);
        if (poolRepository != null) {
            poolRepository.save(pool);
        }
        audit("workspace.pool.created", actor, Map.of(
                "workspaceId", workspaceId, "featureKey", featureKey, "totalQuota", totalQuota));
        return pool;
    }

    public WorkspaceMemberEntitlementGrant allocateToMember(
            String tenantId, String workspaceId, String featureKey,
            String memberId, long quotaAmount, Instant startsAt, Instant expiresAt, String actor,
            String sourceRef, String idempotencyKey, String reason, String traceId) {
        WorkspaceEntitlementPool pool = getPoolForFeature(workspaceId, featureKey);
        long available = pool.totalQuota() - pool.usedQuota();
        if (quotaAmount > available) {
            throw new IllegalArgumentException(
                    "Insufficient pool quota. Requested: " + quotaAmount + ", Available: " + available);
        }
        String grantId = Ids.newId("ws_grant");
        Instant now = Instant.now();
        WorkspaceMemberEntitlementGrant grant = new WorkspaceMemberEntitlementGrant(
                grantId, workspaceId, memberId, featureKey, quotaAmount,
                startsAt, expiresAt, "ACTIVE", actor, now, now);
        PrincipalRef member = new PrincipalRef(
                tenantId, PrincipalType.USER, memberId, workspaceId, null);
        entitlementService.execute(new EntitlementGrantCommand(
                EntitlementCommandType.WORKSPACE_GRANT, member, grantId, featureKey, null,
                "ADMIN", sourceRef, idempotencyKey, actor, reason, traceId,
                startsAt, expiresAt, 0));
        if (poolRepository != null) {
            poolRepository.updateUsage(pool.id(), pool.usedQuota() + quotaAmount);
        }
        audit("workspace.pool.allocated", actor, Map.of(
                "workspaceId", workspaceId, "memberId", memberId,
                "featureKey", featureKey, "quotaAmount", quotaAmount));
        log.info("Allocated {} quota of {} to member {} in workspace {}", quotaAmount, featureKey, memberId, workspaceId);
        return grant;
    }

    public EntitlementCommandResult revokeFromMember(
            String tenantId, String workspaceId, String grantId, String memberId,
            long expectedVersion, String actor, String sourceRef, String idempotencyKey,
            String reason, String traceId) {
        PrincipalRef member = new PrincipalRef(
                tenantId, PrincipalType.USER, memberId, workspaceId, null);
        return entitlementService.execute(new EntitlementGrantCommand(
                EntitlementCommandType.WORKSPACE_REVOKE, member, grantId, null, null,
                "ADMIN", sourceRef, idempotencyKey, actor, reason, traceId,
                Instant.now(), null, expectedVersion));
    }

    public void reclaimFromMember(String workspaceId, String memberId, String featureKey,
            long quotaAmount, String actor) {
        if (poolRepository == null) {
            log.debug("No pool repository available, skipping reclaim");
            return;
        }
        WorkspaceEntitlementPool pool = getPoolForFeature(workspaceId, featureKey);
        long toReclaim = Math.min(quotaAmount, pool.usedQuota());
        poolRepository.updateUsage(pool.id(), pool.usedQuota() - toReclaim);
        audit("workspace.pool.reclaimed", actor, Map.of(
                "workspaceId", workspaceId, "memberId", memberId,
                "featureKey", featureKey, "quotaAmount", toReclaim));
        log.info("Reclaimed {} quota of {} from member {} in workspace {}", toReclaim, featureKey, memberId, workspaceId);
    }

    public List<WorkspaceMemberEntitlementGrant> getMemberGrants(String tenantId, String workspaceId) {
        return entitlementService.listWorkspaceGrants(tenantId, workspaceId);
    }

    public List<WorkspaceMemberEntitlementGrant> getMemberGrants(String workspaceId) { return List.of(); }

    private void audit(String action, String actor, Map<String, Object> payload) {
        if (auditPort != null) {
            auditPort.record("ADMIN", action, "ENTITLEMENT",
                    "WORKSPACE_POOL", payload.getOrDefault("workspaceId", "unknown").toString(), payload);
        }
    }
}
