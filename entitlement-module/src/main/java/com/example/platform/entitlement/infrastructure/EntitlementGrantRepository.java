package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.entitlement.domain.EntitlementGrantView;
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

/** Canonical subordinate generic grant store; only EntitlementService may mutate it. */
@Repository
public class EntitlementGrantRepository {
    private final JdbcTemplate jdbc;

    public EntitlementGrantRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public EntitlementGrantView insert(EntitlementGrantCommand command, Instant now) {
        int inserted = jdbc.update("""
                INSERT INTO entitlement_grant
                    (id, tenant_id, subject_type, subject_id, bundle_code, quota_profile_code,
                     source_type, source_ref, grant_status, effective_at, expires_at,
                     version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, ?)
                """, command.grantId(), command.principal().tenantId(),
                command.principal().principalType().name(), command.principal().principalId(),
                command.bundleCode(), command.quotaProfileCode(), command.sourceType(), command.sourceRef(),
                Timestamp.from(command.effectiveAt()), timestamp(command.expiresAt()),
                Timestamp.from(now), Timestamp.from(now));
        if (inserted != 1) throw new IllegalStateException("Entitlement grant insert did not write one row");
        return find(command.principal(), command.grantId()).orElseThrow();
    }

    public EntitlementGrantView transition(
            EntitlementGrantCommand command, String nextStatus, Instant nextExpiry) {
        int updated = jdbc.update("""
                UPDATE entitlement_grant
                SET grant_status = ?, expires_at = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND tenant_id = ? AND subject_type = ? AND subject_id = ?
                  AND grant_status = 'ACTIVE' AND version = ?
                """, nextStatus, timestamp(nextExpiry), Timestamp.from(Instant.now()), command.grantId(),
                command.principal().tenantId(), command.principal().principalType().name(),
                command.principal().principalId(), command.expectedVersion());
        if (updated != 1) {
            if (find(command.principal(), command.grantId()).isEmpty()) {
                throw new IllegalArgumentException("Entitlement grant not found for principal: " + command.grantId());
            }
            throw new IllegalStateException("Stale or illegal entitlement transition: " + command.grantId());
        }
        return find(command.principal(), command.grantId()).orElseThrow();
    }

    public Optional<EntitlementGrantView> find(PrincipalRef principal, String grantId) {
        List<EntitlementGrantView> rows = jdbc.query("""
                SELECT * FROM entitlement_grant
                WHERE id = ? AND tenant_id = ? AND subject_type = ? AND subject_id = ?
                """, this::map, grantId, principal.tenantId(),
                principal.principalType().name(), principal.principalId());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<EntitlementGrantView> findActive(PrincipalRef principal, Instant now) {
        return jdbc.query("""
                SELECT * FROM entitlement_grant
                WHERE tenant_id = ? AND subject_type = ? AND subject_id = ?
                  AND grant_status = 'ACTIVE' AND effective_at <= ?
                  AND (expires_at IS NULL OR expires_at > ?)
                ORDER BY effective_at DESC
                """, this::map, principal.tenantId(), principal.principalType().name(),
                principal.principalId(), Timestamp.from(now), Timestamp.from(now));
    }

    public List<EntitlementGrantView> findAllActive() {
        return jdbc.query("SELECT * FROM entitlement_grant WHERE grant_status = 'ACTIVE'",
                this::map);
    }

    private EntitlementGrantView map(ResultSet rs, int row) throws SQLException {
        PrincipalRef principal = PrincipalRef.tenantScoped(rs.getString("tenant_id"),
                PrincipalType.valueOf(rs.getString("subject_type")), rs.getString("subject_id"));
        return new EntitlementGrantView(rs.getString("id"), principal, rs.getString("bundle_code"),
                rs.getString("quota_profile_code"), rs.getString("source_type"),
                rs.getString("source_ref"), rs.getString("grant_status"),
                rs.getTimestamp("effective_at").toInstant(), instant(rs.getTimestamp("expires_at")),
                rs.getLong("version"), false);
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
