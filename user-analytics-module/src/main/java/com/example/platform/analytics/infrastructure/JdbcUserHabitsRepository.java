package com.example.platform.analytics.infrastructure;

import com.example.platform.analytics.domain.UserHabits;
import com.example.platform.shared.Jsons;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

public class JdbcUserHabitsRepository implements UserHabitsRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserHabitsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserHabits save(UserHabits habits) {
        int updated = jdbc.update("""
                UPDATE user_habits SET habits_json = ?, computed_at = ?
                WHERE tenant_id = ? AND user_id = ?
                """,
                Jsons.toJson(HabitsPayload.from(habits)),
                Timestamp.from(habits.computedAt()),
                habits.tenantId(),
                habits.userId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO user_habits (tenant_id, user_id, habits_json, computed_at)
                    VALUES (?, ?, ?, ?)
                    """,
                    habits.tenantId(),
                    habits.userId(),
                    Jsons.toJson(HabitsPayload.from(habits)),
                    Timestamp.from(habits.computedAt()));
        }
        return habits;
    }

    @Override
    public Optional<UserHabits> findByTenantIdAndUserId(String tenantId, String userId) {
        List<UserHabits> rows = jdbc.query(
                "SELECT * FROM user_habits WHERE tenant_id = ? AND user_id = ?",
                this::map,
                tenantId,
                userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<UserHabits> findByTenantId(String tenantId, int limit) {
        return jdbc.query(
                "SELECT * FROM user_habits WHERE tenant_id = ? ORDER BY computed_at DESC LIMIT ?",
                this::map,
                tenantId,
                limit);
    }

    private UserHabits map(ResultSet rs, int rowNum) throws SQLException {
        HabitsPayload payload = Jsons.fromJson(rs.getString("habits_json"), HabitsPayload.class);
        return payload.toHabits(rs.getString("tenant_id"), rs.getString("user_id"), rs.getTimestamp("computed_at").toInstant());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HabitsPayload(
            Map<String, Integer> dailyActivityBuckets,
            Map<String, Integer> weeklyActivityPattern,
            List<String> mostUsedFeatures,
            List<String> mostUsedActions,
            double averageSessionDepth,
            String peakActivityHour,
            String peakActivityDay,
            int retentionDays) {

        static HabitsPayload from(UserHabits h) {
            return new HabitsPayload(
                    h.dailyActivityBuckets(),
                    h.weeklyActivityPattern(),
                    h.mostUsedFeatures(),
                    h.mostUsedActions(),
                    h.averageSessionDepth(),
                    h.peakActivityHour(),
                    h.peakActivityDay(),
                    h.retentionDays());
        }

        UserHabits toHabits(String tenantId, String userId, Instant computedAt) {
            return new UserHabits(
                    tenantId,
                    userId,
                    dailyActivityBuckets != null ? dailyActivityBuckets : Map.of(),
                    weeklyActivityPattern != null ? weeklyActivityPattern : Map.of(),
                    mostUsedFeatures != null ? mostUsedFeatures : List.of(),
                    mostUsedActions != null ? mostUsedActions : List.of(),
                    averageSessionDepth,
                    peakActivityHour != null ? peakActivityHour : "unknown",
                    peakActivityDay != null ? peakActivityDay : "unknown",
                    retentionDays,
                    computedAt);
        }
    }
}
