package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.AccessDecision;
import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementCommandType;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.entitlement.domain.EntitlementGrantView;
import com.example.platform.entitlement.domain.EntitlementSnapshot;
import com.example.platform.entitlement.domain.FeatureCheckCommand;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import com.example.platform.entitlement.infrastructure.EntitlementCommandAuditRepository;
import com.example.platform.entitlement.infrastructure.EntitlementGrantRepository;
import com.example.platform.entitlement.infrastructure.InMemoryEntitlementCache;
import com.example.platform.entitlement.infrastructure.WorkspaceMemberEntitlementGrantRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Sole logical command and fail-closed read boundary for Entitlement grants. */
@Service
public class EntitlementService {
    private final EntitlementGrantRepository grants;
    private final WorkspaceMemberEntitlementGrantRepository workspaceGrants;
    private final EntitlementCommandAuditRepository audit;
    private final InMemoryEntitlementCache cache;

    public EntitlementService(
            EntitlementGrantRepository grants,
            WorkspaceMemberEntitlementGrantRepository workspaceGrants,
            EntitlementCommandAuditRepository audit,
            InMemoryEntitlementCache cache) {
        this.grants = grants;
        this.workspaceGrants = workspaceGrants;
        this.audit = audit;
        this.cache = cache;
    }

    /** Idempotency claim, state mutation, and durable audit completion are one transaction. */
    @Transactional
    public EntitlementCommandResult execute(EntitlementGrantCommand command) {
        String commandId = Ids.newId("ent_cmd");
        if (!audit.claim(commandId, command, Instant.now())) return audit.replay(command);
        EntitlementGrantView result = switch (command.commandType()) {
            case GRANT -> grants.insert(command, Instant.now());
            case REVOKE -> grants.transition(command, "REVOKED", currentExpiry(command));
            case EXTEND -> extendGeneric(command);
            case WORKSPACE_GRANT -> workspaceGrants.insert(command, Instant.now());
            case WORKSPACE_REVOKE -> workspaceGrants.transition(command, "REVOKED", currentExpiry(command));
            case WORKSPACE_EXTEND -> extendWorkspace(command);
        };
        audit.complete(commandId, result, Instant.now());
        publishProjectionAfterCommit(result);
        return new EntitlementCommandResult(commandId, result);
    }

    private EntitlementGrantView extendGeneric(EntitlementGrantCommand command) {
        EntitlementGrantView current = grants.find(command.principal(), command.grantId())
                .orElseThrow(() -> new IllegalArgumentException("Entitlement grant not found for principal"));
        validateExtension(current, command.expiresAt());
        return grants.transition(command, "ACTIVE", command.expiresAt());
    }

    private EntitlementGrantView extendWorkspace(EntitlementGrantCommand command) {
        EntitlementGrantView current = workspaceGrants.find(command.principal(), command.grantId())
                .orElseThrow(() -> new IllegalArgumentException("Workspace grant not found for principal"));
        validateExtension(current, command.expiresAt());
        return workspaceGrants.transition(command, "ACTIVE", command.expiresAt());
    }

    private Instant currentExpiry(EntitlementGrantCommand command) {
        Optional<EntitlementGrantView> current = command.commandType().name().startsWith("WORKSPACE_")
                ? workspaceGrants.find(command.principal(), command.grantId())
                : grants.find(command.principal(), command.grantId());
        return current.orElseThrow(() -> new IllegalArgumentException(
                "Entitlement grant not found for principal: " + command.grantId())).expiresAt();
    }

    private static void validateExtension(EntitlementGrantView current, Instant nextExpiry) {
        if (!"ACTIVE".equals(current.status())) throw new IllegalStateException("Only ACTIVE grants may be extended");
        if (nextExpiry == null || (current.expiresAt() != null && !nextExpiry.isAfter(current.expiresAt()))) {
            throw new IllegalStateException("Grant extension must move expiry forward");
        }
    }

    @Transactional(readOnly = true)
    public Optional<EntitlementGrantView> findGrant(PrincipalRef principal, String grantId) {
        try {
            Optional<EntitlementGrantView> generic = grants.find(principal, grantId);
            if (generic.isPresent() || principal.workspaceId() == null) return generic;
            return workspaceGrants.find(principal, grantId);
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<EntitlementGrantView> listGrants(PrincipalRef principal) {
        try {
            java.util.ArrayList<EntitlementGrantView> result = new java.util.ArrayList<>(
                    grants.findActive(principal, Instant.now()));
            result.addAll(workspaceGrants.findActive(principal, Instant.now()));
            return List.copyOf(result);
        } catch (RuntimeException unavailable) {
            cache.remove(cacheKey(principal));
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberEntitlementGrant> listWorkspaceGrants(
            String tenantId, String workspaceId) {
        try {
            return workspaceGrants.findByWorkspace(tenantId, workspaceId);
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public AccessDecision checkFeature(PrincipalRef principal, String featureCode) {
        try {
            List<EntitlementGrantView> active = listGrants(principal);
            EntitlementGrantView matched = active.stream()
                    .filter(grant -> featureCode.equals(grant.bundleCode())).findFirst().orElse(null);
            if (matched == null) {
                cache.remove(cacheKey(principal));
                return decision(false, null, "no-grant");
            }
            cache.put(new EntitlementSnapshot(cacheKey(principal),
                    active.stream().map(EntitlementGrantView::bundleCode).distinct().toList(),
                    matched.quotaProfileCode(), matched.expiresAt()));
            return decision(true, matched.grantId(), "explicit-grant");
        } catch (RuntimeException unavailable) {
            cache.remove(cacheKey(principal));
            return decision(false, null, "persistence-unavailable");
        }
    }

    @Transactional(readOnly = true)
    public EntitlementSnapshot getSnapshot(PrincipalRef principal) {
        List<EntitlementGrantView> active = listGrants(principal);
        if (active.isEmpty()) return new EntitlementSnapshot(cacheKey(principal), List.of(), null, null);
        Instant expiry = active.stream().map(EntitlementGrantView::expiresAt)
                .filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null);
        EntitlementSnapshot projection = new EntitlementSnapshot(cacheKey(principal),
                active.stream().map(EntitlementGrantView::bundleCode).distinct().toList(),
                active.stream().map(EntitlementGrantView::quotaProfileCode)
                        .filter(java.util.Objects::nonNull).findFirst().orElse(null), expiry);
        cache.put(projection);
        return projection;
    }

    /** Ambiguous legacy reads cannot grant access. */
    public AccessDecision checkFeature(FeatureCheckCommand command) {
        return decision(false, null, "principal-required");
    }

    public AccessDecision checkFeatureAccess(FeatureCheckCommand command) { return checkFeature(command); }

    private void publishProjectionAfterCommit(EntitlementGrantView result) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                if ("ACTIVE".equals(result.status())) {
                    cache.put(new EntitlementSnapshot(cacheKey(result.principal()),
                            List.of(result.bundleCode()), result.quotaProfileCode(), result.expiresAt()));
                } else {
                    cache.remove(cacheKey(result.principal()));
                }
            }
        });
    }

    private static AccessDecision decision(boolean allowed, String grantId, String reason) {
        return new AccessDecision(allowed, allowed ? "ALLOW" : "DENY", reason,
                allowed ? "Access granted" : "Access denied", null, List.of(), grantId,
                null, null, null, null, List.of(), null, false);
    }

    private static String cacheKey(PrincipalRef principal) {
        return principal.tenantId() + ":" + principal.principalId();
    }

}
