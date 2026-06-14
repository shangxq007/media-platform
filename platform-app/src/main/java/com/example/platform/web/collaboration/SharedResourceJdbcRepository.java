package com.example.platform.web.collaboration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository

public class SharedResourceJdbcRepository {

    private final JdbcTemplate jdbc;

    public SharedResourceJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(SharedResourceGrant grant) {
        int updated = jdbc.update("""
                UPDATE shared_resource_grant SET
                resource_name = ?, resource_description = ?, resource_status = ?,
                shared_by_user_id = ?, permission = ?, status = ?, expires_at = ?
                WHERE grant_id = ?
                """,
                grant.resourceName(),
                grant.resourceDescription(),
                grant.resourceStatus(),
                grant.sharedByUserId(),
                grant.permission(),
                grant.status(),
                grant.expiresAt() != null ? Timestamp.from(grant.expiresAt()) : null,
                grant.grantId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO shared_resource_grant
                    (grant_id, tenant_id, resource_type, resource_id, resource_name, resource_description,
                     resource_status, shared_by_user_id, shared_with_user_id, permission, status, created_at, expires_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    grant.grantId(),
                    grant.tenantId(),
                    grant.resourceType(),
                    grant.resourceId(),
                    grant.resourceName(),
                    grant.resourceDescription(),
                    grant.resourceStatus(),
                    grant.sharedByUserId(),
                    grant.sharedWithUserId(),
                    grant.permission(),
                    grant.status(),
                    Timestamp.from(grant.createdAt()),
                    grant.expiresAt() != null ? Timestamp.from(grant.expiresAt()) : null);
        }
    }

    public List<SharedResourceGrant> findActiveForRecipient(String tenantId, String userId) {
        return jdbc.query("""
                SELECT * FROM shared_resource_grant
                WHERE tenant_id = ? AND shared_with_user_id = ? AND status = 'ACTIVE'
                ORDER BY created_at DESC
                """,
                this::map,
                tenantId,
                userId);
    }

    public List<SharedResourceGrant> findActiveByTenant(String tenantId) {
        return jdbc.query("""
                SELECT * FROM shared_resource_grant
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY created_at DESC
                """,
                this::map,
                tenantId);
    }

    public Optional<SharedResourceGrant> findByGrantId(String grantId) {
        List<SharedResourceGrant> rows = jdbc.query(
                "SELECT * FROM shared_resource_grant WHERE grant_id = ?",
                this::map,
                grantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<SharedResourceGrant> findByTenant(String tenantId, boolean includeRevoked) {
        if (includeRevoked) {
            return jdbc.query(
                    "SELECT * FROM shared_resource_grant WHERE tenant_id = ? ORDER BY created_at DESC",
                    this::map,
                    tenantId);
        }
        return findActiveByTenant(tenantId);
    }

    public boolean revoke(String grantId) {
        return jdbc.update(
                "UPDATE shared_resource_grant SET status = 'REVOKED' WHERE grant_id = ? AND status = 'ACTIVE'",
                grantId) > 0;
    }

    public boolean hasActiveGrant(String tenantId, String userId, String resourceType,
                                  String resourceId, String requiredPermission) {
        List<SharedResourceGrant> grants = jdbc.query("""
                SELECT * FROM shared_resource_grant
                WHERE tenant_id = ? AND shared_with_user_id = ? AND resource_type = ?
                  AND resource_id = ? AND status = 'ACTIVE'
                """,
                this::map,
                tenantId,
                userId,
                resourceType,
                resourceId);
        if (grants.isEmpty()) {
            return false;
        }
        for (SharedResourceGrant grant : grants) {
            if (permissionSatisfies(grant.permission(), requiredPermission)) {
                if (grant.expiresAt() == null || grant.expiresAt().isAfter(java.time.Instant.now())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean permissionSatisfies(String grantPermission, String required) {
        if (required == null || required.isBlank() || "read".equalsIgnoreCase(required)) {
            return true;
        }
        if ("write".equalsIgnoreCase(required) || "edit".equalsIgnoreCase(required)) {
            return "WRITE".equalsIgnoreCase(grantPermission) || "ADMIN".equalsIgnoreCase(grantPermission);
        }
        if ("admin".equalsIgnoreCase(required)) {
            return "ADMIN".equalsIgnoreCase(grantPermission);
        }
        return grantPermission != null && grantPermission.equalsIgnoreCase(required);
    }

    private SharedResourceGrant map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        return new SharedResourceGrant(
                rs.getString("grant_id"),
                rs.getString("tenant_id"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("resource_name"),
                rs.getString("resource_description"),
                rs.getString("resource_status"),
                rs.getString("shared_by_user_id"),
                rs.getString("shared_with_user_id"),
                rs.getString("permission"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(),
                expires != null ? expires.toInstant() : null);
    }

    public record SharedResourceGrant(
            String grantId,
            String tenantId,
            String resourceType,
            String resourceId,
            String resourceName,
            String resourceDescription,
            String resourceStatus,
            String sharedByUserId,
            String sharedWithUserId,
            String permission,
            String status,
            Instant createdAt,
            Instant expiresAt) {}
}
