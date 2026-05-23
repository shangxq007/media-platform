package com.example.platform.entitlement.infrastructure;

import com.example.platform.shared.Ids;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class QuotaUsageJdbcRepository {

    private final JdbcTemplate jdbc;

    public QuotaUsageJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long getUsage(String subjectId, String featureCode) {
        List<Long> rows = jdbc.query(
                "SELECT usage_value FROM quota_usage WHERE tenant_id = ? AND feature_code = ?",
                (rs, rowNum) -> rs.getLong("usage_value"),
                subjectId,
                featureCode);
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    public void setUsage(String subjectId, String featureCode, long value) {
        Instant now = Instant.now();
        int updated = jdbc.update("""
                UPDATE quota_usage SET usage_value = ?, updated_at = ?
                WHERE tenant_id = ? AND feature_code = ?
                """,
                value,
                Timestamp.from(now),
                subjectId,
                featureCode);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO quota_usage (id, tenant_id, feature_code, usage_value, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    Ids.newId("qu"),
                    subjectId,
                    featureCode,
                    value,
                    Timestamp.from(now),
                    Timestamp.from(now));
        }
    }

    public List<Map.Entry<String, String>> loadSubjectFeatures() {
        return jdbc.query(
                "SELECT tenant_id, feature_code FROM quota_usage",
                (rs, rowNum) -> Map.entry(rs.getString("tenant_id"), rs.getString("feature_code")));
    }

    public List<UsageRow> loadAll() {
        return jdbc.query(
                "SELECT tenant_id, feature_code, usage_value FROM quota_usage",
                (rs, rowNum) -> new UsageRow(
                        rs.getString("tenant_id"),
                        rs.getString("feature_code"),
                        rs.getLong("usage_value")));
    }

    public record UsageRow(String subjectId, String featureCode, long usageValue) {}
}
