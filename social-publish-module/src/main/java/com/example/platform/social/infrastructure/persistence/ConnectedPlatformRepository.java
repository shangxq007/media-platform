package com.example.platform.social.infrastructure.persistence;

import com.example.platform.shared.web.TenantGuard;
import com.example.platform.social.domain.ConnectedPlatform;
import com.example.platform.social.app.SocialAccountReadModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ConnectedPlatformRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<ConnectedPlatform> rowMapper = (rs, rowNum) -> mapRow(rs);

    public ConnectedPlatformRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ConnectedPlatform save(ConnectedPlatform platform) {
        TenantGuard.assertSameTenant(platform.tenantId());
        String sql = """
                INSERT INTO social_connected_platform (id, tenant_id, user_id, platform_type, platform_user_id,
                    platform_username, status, binding_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                platform.id(), platform.tenantId(), platform.userId(), platform.platformType(),
                platform.platformUserId(), platform.platformUsername(), platform.status(),
                platform.bindingVersion(),
                platform.createdAt(), platform.updatedAt());
        return platform;
    }

    public List<ConnectedPlatform> findByTenantAndUser(String tenantId, String userId) {
        tenantId = TenantGuard.tenantOrDefault(tenantId);
        return jdbc.query(
                "SELECT * FROM social_connected_platform WHERE tenant_id = ? AND user_id = ? ORDER BY created_at DESC",
                rowMapper, tenantId, userId);
    }

    public Optional<ConnectedPlatform> findByTenantUserAndPlatform(String tenantId, String userId, String platformType) {
        List<ConnectedPlatform> results = jdbc.query(
                "SELECT * FROM social_connected_platform WHERE tenant_id = ? AND user_id = ? AND platform_type = ?",
                rowMapper, tenantId, userId, platformType);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<ConnectedPlatform> findById(String id) {
        String tenantId = TenantGuard.requireTenantId();
        List<ConnectedPlatform> results = jdbc.query(
                "SELECT * FROM social_connected_platform WHERE id = ? AND tenant_id = ?", rowMapper, id, tenantId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Held only during the short dispatch authorization transaction, never during provider IO. */
    public Optional<ConnectedPlatform> lockById(String id) {
        var rows = jdbc.query("SELECT * FROM social_connected_platform WHERE id=? AND tenant_id=? FOR UPDATE",
                rowMapper, id, TenantGuard.requireTenantId());
        return rows.stream().findFirst();
    }

    public List<SocialAccountReadModel> findPublicationAccounts(
            String tenantId, String actorId, String projectId) {
        return jdbc.query("""
                SELECT a.id, a.platform_username, a.platform_type, a.status, a.binding_version
                FROM social_connected_platform a
                WHERE a.tenant_id = ? AND a.user_id = ? AND a.status = 'ACTIVE'
                  AND EXISTS (
                      SELECT 1
                      FROM social_post p
                      WHERE p.tenant_id = a.tenant_id
                        AND p.user_id = a.user_id
                        AND p.project_id = ?
                        AND p.connected_platform_id = a.id
                        AND p.connected_platform_binding_version = a.binding_version
                        AND p.platform_type = a.platform_type
                  )
                ORDER BY a.created_at DESC, a.id ASC
                """, (rs, rowNum) -> mapPublicationAccount(rs), tenantId, actorId, projectId);
    }

    public Optional<SocialAccountReadModel> findPublicationAccount(
            String tenantId, String actorId, String projectId, String accountId,
            long bindingVersion) {
        List<SocialAccountReadModel> rows = jdbc.query("""
                SELECT a.id, a.platform_username, a.platform_type, a.status, a.binding_version
                FROM social_connected_platform a
                WHERE a.tenant_id = ? AND a.user_id = ? AND a.id = ?
                  AND a.binding_version = ? AND a.status = 'ACTIVE'
                  AND EXISTS (
                      SELECT 1
                      FROM social_post p
                      WHERE p.tenant_id = a.tenant_id
                        AND p.user_id = a.user_id
                        AND p.project_id = ?
                        AND p.connected_platform_id = a.id
                        AND p.connected_platform_binding_version = a.binding_version
                        AND p.platform_type = a.platform_type
                  )
                """, (rs, rowNum) -> mapPublicationAccount(rs),
                tenantId, actorId, accountId, bindingVersion, projectId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM social_connected_platform WHERE id = ?", id);
    }

    private ConnectedPlatform mapRow(ResultSet rs) throws SQLException {
        return new ConnectedPlatform(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("platform_type"),
                rs.getString("platform_user_id"),
                rs.getString("platform_username"),
                rs.getString("status"),
                rs.getLong("binding_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("credential_revision"),
                readCredentialExpiry(rs)
        );
    }

    private static Instant readCredentialExpiry(ResultSet rs) throws SQLException {
        var expiry = rs.getObject("token_expires_at", java.time.LocalDateTime.class);
        return expiry == null ? null : expiry.toInstant(java.time.ZoneOffset.UTC);
    }

    private SocialAccountReadModel mapPublicationAccount(ResultSet rs) throws SQLException {
        return new SocialAccountReadModel(
                rs.getString("id"), rs.getString("platform_username"),
                rs.getString("platform_type"), rs.getString("status"),
                rs.getLong("binding_version"));
    }
}
