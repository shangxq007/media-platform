package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.BillingLedgerEntry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class BillingLedgerJdbcRepository {

    private final JdbcTemplate jdbc;

    public BillingLedgerJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveEntry(BillingLedgerEntry entry) {
        jdbc.update("""
                INSERT INTO billing_ledger_entry
                (id, tenant_id, workspace_id, user_id, entry_type, amount_minor, currency_code,
                 reference_type, reference_id, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.entryId(),
                entry.tenantId(),
                entry.workspaceId(),
                entry.userId(),
                entry.entryType(),
                entry.amountMinor(),
                entry.currencyCode(),
                entry.referenceType(),
                entry.referenceId(),
                entry.description(),
                Timestamp.from(entry.createdAt()));
    }

    public List<BillingLedgerEntry> loadAll() {
        return jdbc.query(
                "SELECT * FROM billing_ledger_entry ORDER BY created_at DESC",
                this::mapEntry);
    }

    private BillingLedgerEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        return new BillingLedgerEntry(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("workspace_id"),
                rs.getString("user_id"),
                rs.getString("entry_type"),
                rs.getLong("amount_minor"),
                rs.getString("currency_code"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getString("description"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }
}
