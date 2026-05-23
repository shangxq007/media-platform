package com.example.platform.social.infrastructure.persistence;

import com.example.platform.shared.web.TenantGuard;
import com.example.platform.social.domain.ConnectedPlatform;
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
                    platform_username, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                platform.id(), platform.tenantId(), platform.userId(), platform.platformType(),
                platform.platformUserId(), platform.platformUsername(), platform.status(),
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
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
