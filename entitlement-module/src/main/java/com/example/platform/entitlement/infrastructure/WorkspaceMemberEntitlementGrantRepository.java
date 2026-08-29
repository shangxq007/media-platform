package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.entitlement.domain.EntitlementGrantView;
import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Distinct workspace grant store subordinate to the same EntitlementService command boundary. */
@Repository
public class WorkspaceMemberEntitlementGrantRepository {
    private final JdbcTemplate jdbc;

    public WorkspaceMemberEntitlementGrantRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public EntitlementGrantView insert(EntitlementGrantCommand command, Instant now) {
        int inserted = jdbc.update("""
                INSERT INTO workspace_member_entitlement_grant
                    (id, tenant_id, workspace_id, principal_type, member_id, feature_key,
                     quota_amount, source_type, source_ref, starts_at, expires_at, status,
                     version, granted_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?, ?)
                """, command.grantId(), command.principal().tenantId(), command.principal().workspaceId(),
                command.principal().principalType().name(), command.principal().principalId(),
                command.bundleCode(), command.sourceType(), command.sourceRef(),
                Timestamp.from(command.effectiveAt()), timestamp(command.expiresAt()), command.actor(),
                Timestamp.from(now), Timestamp.from(now));
        if (inserted != 1) throw new IllegalStateException("Workspace grant insert did not write one row");
        return find(command.principal(), command.grantId()).orElseThrow();
    }

    public EntitlementGrantView transition(
            EntitlementGrantCommand command, String nextStatus, Instant nextExpiry) {
        int updated = jdbc.update("""
                UPDATE workspace_member_entitlement_grant
                SET status = ?, expires_at = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND workspace_id = ?
                  AND principal_type = ? AND member_id = ? AND status = 'ACTIVE' AND version = ?
                """, nextStatus, timestamp(nextExpiry), Timestamp.from(Instant.now()), command.grantId(),
                command.principal().tenantId(), command.principal().workspaceId(),
                command.principal().principalType().name(), command.principal().principalId(),
                command.expectedVersion());
        if (updated != 1) {
            if (find(command.principal(), command.grantId()).isEmpty()) {
                throw new IllegalArgumentException("Workspace grant not found for principal: " + command.grantId());
            }
            throw new IllegalStateException("Stale or illegal workspace grant transition: " + command.grantId());
        }
        return find(command.principal(), command.grantId()).orElseThrow();
    }

    public Optional<EntitlementGrantView> find(PrincipalRef principal, String grantId) {
        List<EntitlementGrantView> rows = jdbc.query("""
                SELECT * FROM workspace_member_entitlement_grant
                WHERE id = ? AND tenant_id = ? AND workspace_id = ?
                  AND principal_type = ? AND member_id = ?
                """, this::mapView, grantId, principal.tenantId(), principal.workspaceId(),
                principal.principalType().name(), principal.principalId());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<EntitlementGrantView> findActive(PrincipalRef principal, Instant now) {
        if (principal.workspaceId() == null) return List.of();
        return jdbc.query("""
                SELECT * FROM workspace_member_entitlement_grant
                WHERE tenant_id = ? AND workspace_id = ? AND principal_type = ? AND member_id = ?
                  AND status = 'ACTIVE' AND starts_at <= ? AND (expires_at IS NULL OR expires_at > ?)
                ORDER BY created_at DESC
                """, this::mapView, principal.tenantId(), principal.workspaceId(),
                principal.principalType().name(), principal.principalId(),
                Timestamp.from(now), Timestamp.from(now));
    }

    public List<WorkspaceMemberEntitlementGrant> findByWorkspace(String tenantId, String workspaceId) {
        return jdbc.query("""
                SELECT * FROM workspace_member_entitlement_grant
                WHERE tenant_id = ? AND workspace_id = ? ORDER BY created_at DESC
                """, this::mapLegacy, tenantId, workspaceId);
    }

    private EntitlementGrantView mapView(ResultSet rs, int row) throws SQLException {
        PrincipalRef principal = new PrincipalRef(rs.getString("tenant_id"),
                PrincipalType.valueOf(rs.getString("principal_type")), rs.getString("member_id"),
                rs.getString("workspace_id"), null);
        return new EntitlementGrantView(rs.getString("id"), principal, rs.getString("feature_key"),
                null, rs.getString("source_type"), rs.getString("source_ref"), rs.getString("status"),
                rs.getTimestamp("starts_at").toInstant(), instant(rs.getTimestamp("expires_at")),
                rs.getLong("version"), true);
    }

    private WorkspaceMemberEntitlementGrant mapLegacy(ResultSet rs, int row) throws SQLException {
        return new WorkspaceMemberEntitlementGrant(rs.getString("id"), rs.getString("workspace_id"),
                rs.getString("member_id"), rs.getString("feature_key"), rs.getLong("quota_amount"),
                rs.getTimestamp("starts_at").toInstant(), instant(rs.getTimestamp("expires_at")),
                rs.getString("status"), rs.getString("granted_by"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
