package com.example.platform.social.infrastructure.persistence;

import com.example.platform.shared.web.TenantGuard;
import com.example.platform.social.domain.PlatformType;
import com.example.platform.social.domain.PostStatus;
import com.example.platform.social.domain.SocialPost;
import com.example.platform.social.app.SocialPostReadModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class SocialPostRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<SocialPost> rowMapper = (rs, rowNum) -> mapRow(rs);

    public SocialPostRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SocialPost save(SocialPost post) {
        TenantGuard.assertSameTenant(post.tenantId());
        String sql = """
                INSERT INTO social_post (id, tenant_id, user_id, project_id, connected_platform_id,
                    connected_platform_binding_version, artifact_id,
                    content_text, media_urls, platform_type,
                    status, platform_post_id, platform_post_url, scheduled_at, published_at, failed_at,
                    error_code, error_message, retry_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                post.id(), post.tenantId(), post.userId(), post.projectId(), post.connectedPlatformId(),
                post.connectedPlatformBindingVersion(), post.artifactId(), post.contentText(),
                toJsonArray(post.mediaUrls()), post.platformType().name(),
                post.status().name(), post.platformPostId(), post.platformPostUrl(),
                utcTimestamp(post.scheduledAt()), utcTimestamp(post.publishedAt()), utcTimestamp(post.failedAt()),
                post.errorCode(), post.errorMessage(), post.retryCount(),
                utcTimestamp(post.createdAt()), utcTimestamp(post.updatedAt()));
        return post;
    }

    public Optional<SocialPost> findById(String id) {
        String tenantId = TenantGuard.requireTenantId();
        List<SocialPost> results = jdbc.query(
                "SELECT * FROM social_post WHERE id = ? AND tenant_id = ?", rowMapper, id, tenantId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<SocialPost> findByTenantAndUser(String tenantId, String userId, int offset, int limit) {
        tenantId = TenantGuard.tenantOrDefault(tenantId);
        return jdbc.query(
                "SELECT * FROM social_post WHERE tenant_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper, tenantId, userId, limit, offset);
    }

    public List<SocialPost> findByStatus(String tenantId, String userId, PostStatus status) {
        return jdbc.query(
                "SELECT * FROM social_post WHERE tenant_id = ? AND user_id = ? AND status = ? ORDER BY created_at DESC",
                rowMapper, tenantId, userId, status.name());
    }

    public List<SocialPost> findScheduledBefore(Instant before) {
        return jdbc.query(
                "SELECT * FROM social_post WHERE status = 'SCHEDULED' AND scheduled_at <= ?",
                rowMapper, utcTimestamp(before));
    }

    public long countByTenantAndUser(String tenantId, String userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM social_post WHERE tenant_id = ? AND user_id = ?",
                Long.class, tenantId, userId);
        return count != null ? count : 0;
    }

    /**
     * Safe first-slice query. Every identity and binding predicate is applied in SQL before
     * projection. The half-open range is the sourced planned time in {@code scheduled_at};
     * record creation time is never substituted. Unbound rows and rows without a plan
     * are excluded. The canonical {@code social_post.artifact_id} relationship is
     * selected; provider and operational fields remain excluded.
     */
    public List<SocialPostReadModel> findReadProjection(
            String tenantId,
            String actorId,
            String projectId,
            String connectedAccountId,
            long bindingVersion,
            Instant start,
            Instant end,
            int limit) {
        return jdbc.query("""
                SELECT p.id AS post_id, p.project_id, p.connected_platform_id,
                       p.connected_platform_binding_version, p.content_text,
                       p.artifact_id, p.platform_type, p.scheduled_at
                FROM social_post p
                JOIN social_connected_platform a
                  ON a.id = p.connected_platform_id
                 AND a.tenant_id = p.tenant_id
                 AND a.user_id = p.user_id
                 AND a.binding_version = p.connected_platform_binding_version
                 AND a.platform_type = p.platform_type
                 AND a.status = 'ACTIVE'
                WHERE p.tenant_id = ?
                  AND p.user_id = ?
                  AND p.project_id = ?
                  AND p.connected_platform_id = ?
                  AND p.connected_platform_binding_version = ?
                  AND p.scheduled_at >= ?
                  AND p.scheduled_at < ?
                ORDER BY p.scheduled_at ASC, p.id ASC
                LIMIT ?
                """, (rs, rowNum) -> mapReadRow(rs),
                tenantId, actorId, projectId, connectedAccountId, bindingVersion,
                utcTimestamp(start), utcTimestamp(end), limit);
    }

    public Optional<SocialPostReadModel> findReadProjectionById(
            String tenantId,
            String actorId,
            String projectId,
            String connectedAccountId,
            long bindingVersion,
            String postId) {
        List<SocialPostReadModel> rows = jdbc.query("""
                SELECT p.id AS post_id, p.project_id, p.connected_platform_id,
                       p.connected_platform_binding_version, p.content_text,
                       p.artifact_id, p.platform_type, p.scheduled_at
                FROM social_post p
                JOIN social_connected_platform a
                  ON a.id = p.connected_platform_id
                 AND a.tenant_id = p.tenant_id
                 AND a.user_id = p.user_id
                 AND a.binding_version = p.connected_platform_binding_version
                 AND a.platform_type = p.platform_type
                 AND a.status = 'ACTIVE'
                WHERE p.id = ?
                  AND p.tenant_id = ?
                  AND p.user_id = ?
                  AND p.project_id = ?
                  AND p.connected_platform_id = ?
                  AND p.connected_platform_binding_version = ?
                """, (rs, rowNum) -> mapReadRow(rs),
                postId, tenantId, actorId, projectId, connectedAccountId, bindingVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void updateStatus(String id, PostStatus status, Instant updatedAt) {
        jdbc.update("UPDATE social_post SET status = ?, updated_at = ? WHERE id = ?",
                status.name(), utcTimestamp(updatedAt), id);
    }

    public void updatePublishResult(String id, String platformPostId, String platformPostUrl,
                                     PostStatus status, Instant publishedAt, Instant updatedAt) {
        jdbc.update("UPDATE social_post SET platform_post_id = ?, platform_post_url = ?, status = ?, published_at = ?, updated_at = ? WHERE id = ?",
                platformPostId, platformPostUrl, status.name(), utcTimestamp(publishedAt), utcTimestamp(updatedAt), id);
    }

    public void updateFailure(String id, String errorCode, String errorMessage,
                               PostStatus status, Instant failedAt, int retryCount, Instant updatedAt) {
        jdbc.update("UPDATE social_post SET error_code = ?, error_message = ?, status = ?, failed_at = ?, retry_count = ?, updated_at = ? WHERE id = ?",
                errorCode, errorMessage, status.name(), utcTimestamp(failedAt), retryCount, utcTimestamp(updatedAt), id);
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM social_post WHERE id = ?", id);
    }

    private SocialPost mapRow(ResultSet rs) throws SQLException {
        return new SocialPost(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("project_id"),
                rs.getString("connected_platform_id"),
                rs.getObject("connected_platform_binding_version", Long.class),
                rs.getString("artifact_id"),
                rs.getString("content_text"),
                parseJsonArray(rs.getString("media_urls")),
                PlatformType.valueOf(rs.getString("platform_type")),
                PostStatus.valueOf(rs.getString("status")),
                rs.getString("platform_post_id"),
                rs.getString("platform_post_url"),
                readInstant(rs, "scheduled_at"),
                readInstant(rs, "published_at"),
                readInstant(rs, "failed_at"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getInt("retry_count"),
                readInstant(rs, "created_at"),
                readInstant(rs, "updated_at")
        );
    }

    private SocialPostReadModel mapReadRow(ResultSet rs) throws SQLException {
        return new SocialPostReadModel(
                rs.getString("post_id"),
                rs.getString("project_id"),
                rs.getString("connected_platform_id"),
                rs.getLong("connected_platform_binding_version"),
                rs.getString("content_text"),
                rs.getString("artifact_id"),
                rs.getString("platform_type"),
                readInstant(rs, "scheduled_at"));
    }

    private static LocalDateTime utcTimestamp(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant readInstant(ResultSet result, String column) throws SQLException {
        LocalDateTime timestamp = result.getObject(column, LocalDateTime.class);
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.isBlank()) return List.of();
        String[] parts = trimmed.split(",");
        List<String> result = new java.util.ArrayList<>();
        for (String part : parts) {
            String item = part.trim();
            if (item.startsWith("\"")) item = item.substring(1);
            if (item.endsWith("\"")) item = item.substring(0, item.length() - 1);
            if (!item.isBlank()) result.add(item);
        }
        return result;
    }
}
