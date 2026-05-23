package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserBehaviorEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcUserBehaviorEventRepository implements UserBehaviorEventRepository {

    private static final TypeReference<Map<String, String>> METADATA_MAP = new TypeReference<>() {};

    private final JdbcTemplate jdbc;

    public JdbcUserBehaviorEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserBehaviorEvent save(UserBehaviorEvent event) {
        jdbc.update("""
                INSERT INTO user_behavior_event
                (event_id, tenant_id, user_id, event_type, action, resource_type, resource_id, metadata_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                event.eventId(),
                event.tenantId(),
                event.userId(),
                event.eventType(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                Jsons.toJson(event.metadata()),
                Timestamp.from(event.occurredAt()));
        return event;
    }

    @Override
    public Optional<UserBehaviorEvent> findByEventId(String eventId) {
        List<UserBehaviorEvent> rows = jdbc.query(
                "SELECT * FROM user_behavior_event WHERE event_id = ?",
                this::map,
                eventId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<UserBehaviorEvent> findByTenantId(String tenantId, int limit) {
        return jdbc.query(
                "SELECT * FROM user_behavior_event WHERE tenant_id = ? ORDER BY occurred_at DESC LIMIT ?",
                this::map,
                tenantId,
                limit);
    }

    @Override
    public List<UserBehaviorEvent> findByTenantIdAndUserId(String tenantId, String userId, int limit) {
        return jdbc.query(
                "SELECT * FROM user_behavior_event WHERE tenant_id = ? AND user_id = ? ORDER BY occurred_at DESC LIMIT ?",
                this::map,
                tenantId,
                userId,
                limit);
    }

    @Override
    public List<UserBehaviorEvent> findByTenantIdAndEventType(String tenantId, String eventType, int limit) {
        return jdbc.query(
                "SELECT * FROM user_behavior_event WHERE tenant_id = ? AND event_type = ? ORDER BY occurred_at DESC LIMIT ?",
                this::map,
                tenantId,
                eventType,
                limit);
    }

    @Override
    public long countByTenantId(String tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_behavior_event WHERE tenant_id = ?",
                Long.class,
                tenantId);
        return count != null ? count : 0L;
    }

    private UserBehaviorEvent map(ResultSet rs, int rowNum) throws SQLException {
        String metaRaw = rs.getString("metadata_json");
        Map<String, String> metadata = metaRaw != null && !metaRaw.isBlank()
                ? Jsons.fromJson(metaRaw, METADATA_MAP)
                : Map.of();
        Timestamp occurred = rs.getTimestamp("occurred_at");
        return new UserBehaviorEvent(
                rs.getString("event_id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("event_type"),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                metadata,
                occurred != null ? occurred.toInstant() : Instant.now());
    }
}
