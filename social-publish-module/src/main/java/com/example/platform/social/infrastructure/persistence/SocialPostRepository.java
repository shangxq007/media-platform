package com.example.platform.social.infrastructure.persistence;

import com.example.platform.shared.web.TenantGuard;
import com.example.platform.social.domain.PlatformType;
import com.example.platform.social.domain.PostStatus;
import com.example.platform.social.domain.SocialPost;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
                INSERT INTO social_post (id, tenant_id, user_id, content_text, media_urls, platform_type,
                    status, platform_post_id, platform_post_url, scheduled_at, published_at, failed_at,
                    error_code, error_message, retry_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql,
                post.id(), post.tenantId(), post.userId(), post.contentText(),
                toJsonArray(post.mediaUrls()), post.platformType().name(),
                post.status().name(), post.platformPostId(), post.platformPostUrl(),
                post.scheduledAt(), post.publishedAt(), post.failedAt(),
                post.errorCode(), post.errorMessage(), post.retryCount(),
                post.createdAt(), post.updatedAt());
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
                rowMapper, before);
    }

    public long countByTenantAndUser(String tenantId, String userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM social_post WHERE tenant_id = ? AND user_id = ?", Long.class, tenantId, userId);
        return count != null ? count : 0;
    }

    public void updateStatus(String id, PostStatus status, Instant updatedAt) {
        jdbc.update("UPDATE social_post SET status = ?, updated_at = ? WHERE id = ?",
                status.name(), updatedAt, id);
    }

    public void updatePublishResult(String id, String platformPostId, String platformPostUrl,
                                     PostStatus status, Instant publishedAt, Instant updatedAt) {
        jdbc.update("UPDATE social_post SET platform_post_id = ?, platform_post_url = ?, status = ?, published_at = ?, updated_at = ? WHERE id = ?",
                platformPostId, platformPostUrl, status.name(), publishedAt, updatedAt, id);
    }

    public void updateFailure(String id, String errorCode, String errorMessage,
                               PostStatus status, Instant failedAt, int retryCount, Instant updatedAt) {
        jdbc.update("UPDATE social_post SET error_code = ?, error_message = ?, status = ?, failed_at = ?, retry_count = ?, updated_at = ? WHERE id = ?",
                errorCode, errorMessage, status.name(), failedAt, retryCount, updatedAt, id);
    }

    public void deleteById(String id) {
        jdbc.update("DELETE FROM social_post WHERE id = ?", id);
    }

    private SocialPost mapRow(ResultSet rs) throws SQLException {
        return new SocialPost(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("content_text"),
                parseJsonArray(rs.getString("media_urls")),
                PlatformType.valueOf(rs.getString("platform_type")),
                PostStatus.valueOf(rs.getString("status")),
                rs.getString("platform_post_id"),
                rs.getString("platform_post_url"),
                rs.getTimestamp("scheduled_at") != null ? rs.getTimestamp("scheduled_at").toInstant() : null,
                rs.getTimestamp("published_at") != null ? rs.getTimestamp("published_at").toInstant() : null,
                rs.getTimestamp("failed_at") != null ? rs.getTimestamp("failed_at").toInstant() : null,
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getInt("retry_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
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
