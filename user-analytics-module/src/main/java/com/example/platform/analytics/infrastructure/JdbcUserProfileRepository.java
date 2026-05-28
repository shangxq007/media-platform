package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserProfile;
import com.example.platform.shared.Jsons;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcUserProfileRepository implements UserProfileRepository {

    private static final TypeReference<Map<String, Integer>> INT_MAP = new TypeReference<>() {};
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public JdbcUserProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserProfile save(UserProfile profile) {
        int updated = jdbc.update("""
                UPDATE user_profile SET
                display_name = ?, preferred_languages_json = ?, feature_usage_counts_json = ?,
                action_counts_json = ?, total_sessions = ?, total_actions = ?,
                first_seen_at = ?, last_active_at = ?, updated_at = ?
                WHERE tenant_id = ? AND user_id = ?
                """,
                profile.displayName(),
                Jsons.toJson(profile.preferredLanguages()),
                Jsons.toJson(profile.featureUsageCounts()),
                Jsons.toJson(profile.actionCounts()),
                profile.totalSessions(),
                profile.totalActions(),
                profile.firstSeenAt() != null ? Timestamp.from(profile.firstSeenAt()) : null,
                profile.lastActiveAt() != null ? Timestamp.from(profile.lastActiveAt()) : null,
                Timestamp.from(profile.updatedAt()),
                profile.tenantId(),
                profile.userId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO user_profile
                    (profile_id, tenant_id, user_id, display_name, preferred_languages_json,
                     feature_usage_counts_json, action_counts_json, total_sessions, total_actions,
                     first_seen_at, last_active_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    profile.profileId(),
                    profile.tenantId(),
                    profile.userId(),
                    profile.displayName(),
                    Jsons.toJson(profile.preferredLanguages()),
                    Jsons.toJson(profile.featureUsageCounts()),
                    Jsons.toJson(profile.actionCounts()),
                    profile.totalSessions(),
                    profile.totalActions(),
                    profile.firstSeenAt() != null ? Timestamp.from(profile.firstSeenAt()) : null,
                    profile.lastActiveAt() != null ? Timestamp.from(profile.lastActiveAt()) : null,
                    Timestamp.from(profile.updatedAt()));
        }
        return profile;
    }

    @Override
    public Optional<UserProfile> findByTenantIdAndUserId(String tenantId, String userId) {
        List<UserProfile> rows = jdbc.query(
                "SELECT * FROM user_profile WHERE tenant_id = ? AND user_id = ?",
                this::map,
                tenantId,
                userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<UserProfile> findByTenantId(String tenantId, int limit) {
        return jdbc.query(
                "SELECT * FROM user_profile WHERE tenant_id = ? ORDER BY updated_at DESC LIMIT ?",
                this::map,
                tenantId,
                limit);
    }

    @Override
    public long countByTenantId(String tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_profile WHERE tenant_id = ?",
                Long.class,
                tenantId);
        return count != null ? count : 0L;
    }

    @Override
    public List<String> findAllDistinctTenantIds() {
        return jdbc.queryForList(
                "SELECT DISTINCT tenant_id FROM user_profile",
                String.class);
    }

    private UserProfile map(ResultSet rs, int rowNum) throws SQLException {
        return new UserProfile(
                rs.getString("profile_id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("display_name"),
                parseSet(rs.getString("preferred_languages_json")),
                parseIntMap(rs.getString("feature_usage_counts_json")),
                parseIntMap(rs.getString("action_counts_json")),
                rs.getInt("total_sessions"),
                rs.getInt("total_actions"),
                toInstant(rs.getTimestamp("first_seen_at")),
                toInstant(rs.getTimestamp("last_active_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private static Set<String> parseSet(String json) {
        if (json == null || json.isBlank()) return Set.of();
        return Jsons.fromJson(json, STRING_SET);
    }

    private static Map<String, Integer> parseIntMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        return Jsons.fromJson(json, INT_MAP);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }
}
