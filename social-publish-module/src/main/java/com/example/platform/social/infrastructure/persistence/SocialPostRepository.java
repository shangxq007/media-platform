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

    /** One durable owner; no lease expiry or automatic redispatch of interrupted attempts. */
    public record Attempt(SocialPost post, String token) {}

    public Optional<Attempt> claim(String tenant, String actor, String id, String token,
                                   PostStatus expected, Instant now) {
        TenantGuard.assertSameTenant(tenant);
        if (expected != PostStatus.DRAFT && expected != PostStatus.SCHEDULED && expected != PostStatus.FAILED)
            throw new IllegalArgumentException("Ineligible publication status");
        var rows = jdbc.query("""
                UPDATE social_post SET status='PUBLISHING', publication_attempt_id=?,
                    attempt_project_id=project_id, attempt_account_id=connected_platform_id,
                    attempt_binding_version=connected_platform_binding_version, updated_at=?
                WHERE id=? AND tenant_id=? AND user_id=? AND status=? AND dispatch_started_at IS NULL
                  AND (status <> 'SCHEDULED' OR scheduled_at <= ?)
                RETURNING *
                """, rowMapper, token, utcTimestamp(now), id, tenant, actor, expected.name(), utcTimestamp(now));
        return rows.stream().findFirst().map(post -> new Attempt(post, token));
    }

    private static final String OWNED = """
             WHERE id=? AND tenant_id=? AND user_id=? AND publication_attempt_id=? AND status='PUBLISHING'
               AND project_id IS NOT DISTINCT FROM attempt_project_id
               AND connected_platform_id IS NOT DISTINCT FROM attempt_account_id
               AND connected_platform_binding_version IS NOT DISTINCT FROM attempt_binding_version
            """;

    public boolean markDispatched(Attempt attempt, Instant now) {
        var p = attempt.post();
        TenantGuard.assertSameTenant(p.tenantId());
        return jdbc.update("UPDATE social_post SET dispatch_started_at=?, updated_at=? " + OWNED
                        + " AND dispatch_started_at IS NULL", utcTimestamp(now), utcTimestamp(now),
                p.id(), p.tenantId(), p.userId(), attempt.token()) == 1;
    }

    public boolean complete(Attempt attempt, String externalId, String externalUrl, Instant now) {
        var p = attempt.post();
        TenantGuard.assertSameTenant(p.tenantId());
        return jdbc.update("""
                UPDATE social_post SET status='PUBLISHED', platform_post_id=?, platform_post_url=?,
                    published_at=?, error_code=NULL, error_message=NULL, updated_at=?
                """ + OWNED + " AND dispatch_started_at IS NOT NULL", externalId, externalUrl,
                utcTimestamp(now), utcTimestamp(now), p.id(), p.tenantId(), p.userId(), attempt.token()) == 1;
    }

    public boolean failBeforeDispatch(Attempt attempt, Instant now) {
        var p = attempt.post();
        TenantGuard.assertSameTenant(p.tenantId());
        return jdbc.update("""
                UPDATE social_post SET status='FAILED', failed_at=?, error_code='PRE_DISPATCH_REJECTED',
                    error_message='Publication prerequisites rejected before dispatch', retry_count=retry_count+1, updated_at=?
                """ + OWNED + " AND dispatch_started_at IS NULL", utcTimestamp(now), utcTimestamp(now),
                p.id(), p.tenantId(), p.userId(), attempt.token()) == 1;
    }

    public boolean unresolved(Attempt attempt, Instant now) {
        var p = attempt.post();
        TenantGuard.assertSameTenant(p.tenantId());
        return jdbc.update("""
                UPDATE social_post SET status='UNRESOLVED', error_code='EXTERNAL_OUTCOME_UNKNOWN',
                    error_message='Dispatch may have produced an external effect; reconciliation required', updated_at=?
                """ + OWNED + " AND dispatch_started_at IS NOT NULL", utcTimestamp(now),
                p.id(), p.tenantId(), p.userId(), attempt.token()) == 1;
    }

    public boolean schedule(String tenant, String actor, String id, Instant schedule, Instant now) {
        TenantGuard.assertSameTenant(tenant);
        return jdbc.update("""
                UPDATE social_post SET status='SCHEDULED', scheduled_at=?, updated_at=?
                WHERE id=? AND tenant_id=? AND user_id=? AND dispatch_started_at IS NULL
                  AND status IN ('DRAFT','SCHEDULED','FAILED','CANCELLED')
                """, utcTimestamp(schedule), utcTimestamp(now), id, tenant, actor) == 1;
    }

    /** Cancellation fences late completions. Dispatched work remains unresolved, never retryable. */
    public boolean cancel(String tenant, String actor, String id, Instant now) {
        TenantGuard.assertSameTenant(tenant);
        return jdbc.update("""
                UPDATE social_post SET status=CASE WHEN dispatch_started_at IS NULL THEN 'CANCELLED' ELSE 'UNRESOLVED' END,
                    error_code=CASE WHEN dispatch_started_at IS NULL THEN NULL ELSE 'CANCELLED_AFTER_DISPATCH' END,
                    error_message=CASE WHEN dispatch_started_at IS NULL THEN NULL ELSE 'External request cannot be recalled' END,
                    updated_at=?
                WHERE id=? AND tenant_id=? AND user_id=? AND status IN ('DRAFT','SCHEDULED','FAILED','PUBLISHING')
                """, utcTimestamp(now), id, tenant, actor) == 1;
    }

    public boolean delete(String tenant, String actor, String id) {
        TenantGuard.assertSameTenant(tenant);
        return jdbc.update("""
                DELETE FROM social_post WHERE id=? AND tenant_id=? AND user_id=?
                  AND dispatch_started_at IS NULL AND status IN ('DRAFT','SCHEDULED','FAILED','CANCELLED')
                """, id, tenant, actor) == 1;
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
