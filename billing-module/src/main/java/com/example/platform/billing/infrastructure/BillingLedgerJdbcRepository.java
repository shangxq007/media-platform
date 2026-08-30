package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sole append-only Billing ledger writer. */
@Repository
public class BillingLedgerJdbcRepository {

    private final JdbcTemplate jdbc;

    public BillingLedgerJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public BillingLedgerEntry append(BillingLedgerEntry entry) {
        jdbc.update("""
                INSERT INTO billing_ledger_entry
                (id, tenant_id, principal_type, principal_id, workspace_id, entry_type,
                 amount_minor, currency_code, reference_type, reference_id, description,
                 idempotency_key, payload_fingerprint, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, entry.entryId(), entry.tenantId(), entry.principal().principalType().name(),
                entry.principal().principalId(), entry.workspaceId(), entry.entryType(),
                entry.amountMinor(), entry.currencyCode(), entry.referenceType(), entry.referenceId(),
                entry.description(), entry.idempotencyKey(), entry.payloadFingerprint(),
                Timestamp.from(entry.createdAt()));
        Optional<BillingLedgerEntry> idempotent = findByIdempotencyKey(
                entry.tenantId(), entry.idempotencyKey());
        if (idempotent.isEmpty()) {
            if (findByReference(entry.tenantId(), entry.referenceType(),
                    entry.referenceId(), entry.entryType()).isPresent()) {
                throw new IllegalStateException(
                        "Ledger reference reused under a different idempotency key");
            }
            throw new IllegalStateException("ledger conflict was not readable");
        }
        BillingLedgerEntry stored = idempotent.orElseThrow();
        if (!stored.payloadFingerprint().equals(entry.payloadFingerprint())) {
            throw new IllegalStateException("Ledger idempotency key or reference reused with different payload");
        }
        return stored;
    }

    public Optional<BillingLedgerEntry> findByIdempotencyKey(String tenantId, String key) {
        return jdbc.query("""
                SELECT * FROM billing_ledger_entry WHERE tenant_id = ? AND idempotency_key = ?
                """, this::mapEntry, tenantId, key).stream().findFirst();
    }

    public Optional<BillingLedgerEntry> findByReference(String tenantId, String type,
                                                        String id, String entryType) {
        return jdbc.query("""
                SELECT * FROM billing_ledger_entry
                WHERE tenant_id = ? AND reference_type = ? AND reference_id = ? AND entry_type = ?
                """, this::mapEntry, tenantId, type, id, entryType).stream().findFirst();
    }

    public Optional<BillingLedgerEntry> findByTenantAndId(String tenantId, String entryId) {
        return jdbc.query("SELECT * FROM billing_ledger_entry WHERE tenant_id = ? AND id = ?",
                this::mapEntry, tenantId, entryId).stream().findFirst();
    }

    public List<BillingLedgerEntry> findByTenant(String tenantId) {
        return jdbc.query("""
                SELECT * FROM billing_ledger_entry WHERE tenant_id = ? ORDER BY created_at DESC
                """, this::mapEntry, tenantId);
    }

    public List<BillingLedgerEntry> findByTenantAndType(String tenantId, String entryType) {
        return jdbc.query("""
                SELECT * FROM billing_ledger_entry
                WHERE tenant_id = ? AND entry_type = ? ORDER BY created_at DESC
                """, this::mapEntry, tenantId, entryType);
    }

    private BillingLedgerEntry mapEntry(ResultSet rs, int row) throws SQLException {
        PrincipalRef principal = new PrincipalRef(rs.getString("tenant_id"),
                PrincipalType.valueOf(rs.getString("principal_type")), rs.getString("principal_id"),
                rs.getString("workspace_id"), null);
        return new BillingLedgerEntry(rs.getString("id"), principal, rs.getString("entry_type"),
                new Money(rs.getLong("amount_minor"), rs.getString("currency_code")),
                rs.getString("reference_type"), rs.getString("reference_id"),
                rs.getString("description"), rs.getString("idempotency_key"),
                rs.getString("payload_fingerprint"), rs.getTimestamp("created_at").toInstant());
    }
}
