package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserSegment;
import com.example.platform.shared.Jsons;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Primary

public class JdbcUserSegmentRepository implements UserSegmentRepository {

    private static final TypeReference<Map<String, String>> CRITERIA_MAP = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public JdbcUserSegmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserSegment save(UserSegment segment) {
        int updated = jdbc.update("""
                UPDATE user_segment SET
                name = ?, description = ?, criteria_json = ?, user_ids_json = ?,
                user_count = ?, computed_at = ?
                WHERE segment_id = ?
                """,
                segment.name(),
                segment.description(),
                Jsons.toJson(segment.criteria()),
                Jsons.toJson(segment.userIds()),
                segment.userCount(),
                Timestamp.from(segment.computedAt()),
                segment.segmentId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO user_segment
                    (segment_id, tenant_id, name, description, criteria_json, user_ids_json, user_count, computed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    segment.segmentId(),
                    segment.tenantId(),
                    segment.name(),
                    segment.description(),
                    Jsons.toJson(segment.criteria()),
                    Jsons.toJson(segment.userIds()),
                    segment.userCount(),
                    Timestamp.from(segment.computedAt()));
        }
        return segment;
    }

    @Override
    public Optional<UserSegment> findByTenantIdAndSegmentId(String tenantId, String segmentId) {
        List<UserSegment> rows = jdbc.query(
                "SELECT * FROM user_segment WHERE tenant_id = ? AND segment_id = ?",
                this::map,
                tenantId,
                segmentId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<UserSegment> findByTenantId(String tenantId) {
        return jdbc.query(
                "SELECT * FROM user_segment WHERE tenant_id = ? ORDER BY computed_at DESC",
                this::map,
                tenantId);
    }

    private UserSegment map(ResultSet rs, int rowNum) throws SQLException {
        return new UserSegment(
                rs.getString("segment_id"),
                rs.getString("tenant_id"),
                rs.getString("name"),
                rs.getString("description"),
                parseCriteria(rs.getString("criteria_json")),
                parseUserIds(rs.getString("user_ids_json")),
                rs.getInt("user_count"),
                rs.getTimestamp("computed_at").toInstant());
    }

    private static Map<String, String> parseCriteria(String json) {
        if (json == null || json.isBlank()) return Map.of();
        return Jsons.fromJson(json, CRITERIA_MAP);
    }

    private static List<String> parseUserIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        return Jsons.fromJson(json, STRING_LIST);
    }
}
