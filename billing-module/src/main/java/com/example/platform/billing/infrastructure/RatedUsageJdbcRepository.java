package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.shared.commercial.Money;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sole append-only durable writer for immutable rated usage. */
@Repository
public class RatedUsageJdbcRepository {

    private final JdbcTemplate jdbc;

    public RatedUsageJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AppendResult append(RatedUsageRecord record) {
        int inserted = jdbc.update("""
                INSERT INTO rated_usage_record
                (id, tenant_id, billable_usage_id, pricing_rule_id, pricing_rule_version,
                 quantity_base_units, rated_amount_minor, currency_code, rating_details,
                 rated_at, trace_id, idempotency_key, payload_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, record.ratedUsageId(), record.tenantId(), record.billableUsageId(),
                record.pricingRuleId(), record.pricingRuleVersion(), record.quantityBaseUnits(),
                record.ratedAmountMinor(), record.currencyCode(), encodeDetails(record.ratingDetails()),
                Timestamp.from(record.ratedAt()), record.traceId(), record.idempotencyKey(),
                record.payloadFingerprint());
        RatedUsageRecord stored = findByIdempotencyKey(record.tenantId(), record.idempotencyKey())
                .or(() -> findByLineage(record.tenantId(), record.billableUsageId(),
                        record.pricingRuleId(), record.pricingRuleVersion()))
                .orElseThrow(() -> new IllegalStateException("rated usage conflict was not readable"));
        if (!stored.payloadFingerprint().equals(record.payloadFingerprint())) {
            throw new IllegalStateException("Idempotency key or rating lineage reused with different payload");
        }
        return new AppendResult(stored, inserted == 1);
    }

    public Optional<RatedUsageRecord> findByIdempotencyKey(String tenantId, String key) {
        return jdbc.query("""
                SELECT * FROM rated_usage_record WHERE tenant_id = ? AND idempotency_key = ?
                """, this::map, tenantId, key).stream().findFirst();
    }

    public Optional<RatedUsageRecord> findByLineage(String tenantId, String billableUsageId,
                                                    String ruleId, long version) {
        return jdbc.query("""
                SELECT * FROM rated_usage_record
                WHERE tenant_id = ? AND billable_usage_id = ?
                  AND pricing_rule_id = ? AND pricing_rule_version = ?
                """, this::map, tenantId, billableUsageId, ruleId, version).stream().findFirst();
    }

    public Optional<RatedUsageRecord> findByTenantAndId(String tenantId, String id) {
        return jdbc.query("SELECT * FROM rated_usage_record WHERE tenant_id = ? AND id = ?",
                this::map, tenantId, id).stream().findFirst();
    }

    public long countByTenant(String tenantId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM rated_usage_record WHERE tenant_id = ?", Long.class, tenantId);
        return count == null ? 0 : count;
    }

    private RatedUsageRecord map(ResultSet rs, int row) throws SQLException {
        return new RatedUsageRecord(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("billable_usage_id"), rs.getString("pricing_rule_id"),
                rs.getLong("pricing_rule_version"), rs.getLong("quantity_base_units"),
                new Money(rs.getLong("rated_amount_minor"), rs.getString("currency_code")),
                decodeDetails(rs.getString("rating_details")), rs.getTimestamp("rated_at").toInstant(),
                rs.getString("trace_id"), rs.getString("idempotency_key"),
                rs.getString("payload_fingerprint"));
    }

    private static String encodeDetails(Map<String, String> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static Map<String, String> decodeDetails(String value) {
        if (value == null || value.isBlank()) return Map.of();
        java.util.TreeMap<String, String> result = new java.util.TreeMap<>();
        for (String line : value.split("\n")) {
            int separator = line.indexOf('=');
            if (separator > 0) result.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return Map.copyOf(result);
    }

    public record AppendResult(RatedUsageRecord record, boolean inserted) {}
}
