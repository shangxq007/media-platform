package com.example.platform.entitlement.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class TenantTierJdbcRepository {

    private final JdbcTemplate jdbc;

    public TenantTierJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(String tenantId, String tier) {
        int updated = jdbc.update("""
                UPDATE tenant_entitlement_tier SET tier = ?, updated_at = ?
                WHERE tenant_id = ?
                """,
                tier,
                Timestamp.from(Instant.now()),
                tenantId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO tenant_entitlement_tier (tenant_id, tier, updated_at)
                    VALUES (?, ?, ?)
                    """,
                    tenantId,
                    tier,
                    Timestamp.from(Instant.now()));
        }
    }

    public Optional<String> findTier(String tenantId) {
        List<String> rows = jdbc.query(
                "SELECT tier FROM tenant_entitlement_tier WHERE tenant_id = ?",
                (rs, rowNum) -> rs.getString("tier"),
                tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<Map.Entry<String, String>> loadAll() {
        return jdbc.query(
                "SELECT tenant_id, tier FROM tenant_entitlement_tier",
                (rs, rowNum) -> Map.entry(rs.getString("tenant_id"), rs.getString("tier")));
    }
}
