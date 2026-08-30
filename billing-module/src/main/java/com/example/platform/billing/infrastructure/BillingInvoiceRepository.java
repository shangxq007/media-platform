package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.BillingInvoice;
import com.example.platform.billing.domain.InvoiceCommand;
import com.example.platform.billing.domain.InvoiceCommandResult;
import com.example.platform.billing.domain.InvoiceLineItem;
import com.example.platform.billing.domain.InvoiceStatus;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sole physical writer for Billing invoices, lines, and command outcomes. */
@Repository
public class BillingInvoiceRepository {

    private final JdbcTemplate jdbc;

    public BillingInvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockCommand(String tenantId, String idempotencyKey) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                tenantId + ":" + idempotencyKey);
    }

    public Optional<StoredCommand> findCommand(String tenantId, String idempotencyKey) {
        return jdbc.query("""
                SELECT * FROM billing_invoice_command
                WHERE tenant_id = ? AND idempotency_key = ?
                """, (rs, row) -> new StoredCommand(rs.getString("payload_fingerprint"),
                new InvoiceCommandResult(rs.getString("invoice_id"),
                        InvoiceStatus.valueOf(rs.getString("result_status")),
                        rs.getLong("result_version"), new Money(rs.getLong("result_total_minor"),
                        rs.getString("result_currency")))), tenantId, idempotencyKey)
                .stream().findFirst();
    }

    public void saveCommand(InvoiceCommand command, InvoiceCommandResult result) {
        jdbc.update("""
                INSERT INTO billing_invoice_command
                (id, tenant_id, invoice_id, idempotency_key, command_type, payload_fingerprint,
                 result_version, result_status, result_total_minor, result_currency,
                 actor, reason, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "icm_" + command.fingerprint().substring(0, 24), command.principal().tenantId(),
                command.invoiceId(), command.idempotencyKey(), command.commandType().name(),
                command.fingerprint(), result.version(), result.status().name(),
                result.total().amountMinor(), result.total().currency(), command.actor(),
                command.reason(), command.traceId(), Timestamp.from(command.occurredAt()));
    }

    public BillingInvoice insertInvoice(InvoiceCommand command) {
        Money zero = new Money(0, command.unitPrice().currency());
        jdbc.update("""
                INSERT INTO billing_invoice
                (id, tenant_id, principal_type, principal_id, contract_id, provider_code,
                 external_invoice_ref, invoice_status, total_amount_minor, amount_paid_minor,
                 currency_code, version, issued_at, paid_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, NULL, NULL, 'OPEN', 0, 0, ?, 1, NULL, NULL, ?, ?)
                """, command.invoiceId(), command.principal().tenantId(),
                command.principal().principalType().name(), command.principal().principalId(),
                command.contractId(), zero.currency(), Timestamp.from(command.occurredAt()),
                Timestamp.from(command.occurredAt()));
        return findByTenantAndId(command.principal().tenantId(), command.invoiceId()).orElseThrow();
    }

    public BillingInvoice addLine(InvoiceCommand command) {
        try {
            jdbc.update("""
                    INSERT INTO invoice_line_item
                    (id, tenant_id, invoice_id, rated_usage_id, line_type, description,
                     quantity_base_units, unit_price_minor, amount_minor, currency_code,
                     period_start, period_end, created_at)
                    VALUES (?, ?, ?, ?, 'RATED_USAGE', ?, ?, ?, ?, ?, NULL, NULL, ?)
                    """, command.lineItemId(), command.principal().tenantId(), command.invoiceId(),
                    command.ratedUsageId(), "Rated usage " + command.ratedUsageId(),
                    command.quantityBaseUnits(), command.unitPrice().amountMinor(),
                    command.lineAmount().amountMinor(), command.lineAmount().currency(),
                    Timestamp.from(command.occurredAt()));
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalStateException("Rated usage or invoice line was already billed", duplicate);
        }
        int updated = jdbc.update("""
                UPDATE billing_invoice SET version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND invoice_status = 'OPEN'
                """, Timestamp.from(command.occurredAt()), command.principal().tenantId(),
                command.invoiceId(), command.expectedVersion());
        if (updated != 1) throw new IllegalStateException("Invoice line CAS or state transition rejected");
        return findByTenantAndId(command.principal().tenantId(), command.invoiceId()).orElseThrow();
    }

    public BillingInvoice transition(String tenantId, String invoiceId, long expectedVersion,
                                     InvoiceStatus expectedStatus, InvoiceStatus targetStatus,
                                     Money total, Instant at) {
        int updated = jdbc.update("""
                UPDATE billing_invoice SET invoice_status = ?, total_amount_minor = ?,
                    amount_paid_minor = CASE WHEN ? = 'PAID' THEN ? ELSE amount_paid_minor END,
                    version = version + 1,
                    issued_at = CASE WHEN ? = 'ISSUED' THEN ? ELSE issued_at END,
                    paid_at = CASE WHEN ? = 'PAID' THEN ? ELSE paid_at END,
                    updated_at = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND invoice_status = ?
                """, targetStatus.name(), total.amountMinor(), targetStatus.name(),
                total.amountMinor(), targetStatus.name(), Timestamp.from(at), targetStatus.name(),
                Timestamp.from(at), Timestamp.from(at), tenantId, invoiceId, expectedVersion,
                expectedStatus.name());
        if (updated != 1) throw new IllegalStateException("Invoice CAS or legal transition rejected");
        return findByTenantAndId(tenantId, invoiceId).orElseThrow();
    }

    public Optional<BillingInvoice> findByTenantAndId(String tenantId, String id) {
        return jdbc.query("SELECT * FROM billing_invoice WHERE tenant_id = ? AND id = ?",
                this::mapInvoice, tenantId, id).stream().findFirst();
    }

    public List<BillingInvoice> findByTenantAndContractId(String tenantId, String contractId) {
        return jdbc.query("""
                SELECT * FROM billing_invoice WHERE tenant_id = ? AND contract_id = ?
                ORDER BY created_at DESC
                """, this::mapInvoice, tenantId, contractId);
    }

    public List<InvoiceLineItem> findLines(String tenantId, String invoiceId) {
        return jdbc.query("""
                SELECT * FROM invoice_line_item WHERE tenant_id = ? AND invoice_id = ?
                ORDER BY created_at, id
                """, this::mapLine, tenantId, invoiceId);
    }

    private BillingInvoice mapInvoice(ResultSet rs, int row) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        PrincipalRef principal = PrincipalRef.tenantScoped(tenantId,
                PrincipalType.valueOf(rs.getString("principal_type")), rs.getString("principal_id"));
        String currency = rs.getString("currency_code");
        return new BillingInvoice(rs.getString("id"), principal, rs.getString("contract_id"),
                rs.getString("provider_code"), rs.getString("external_invoice_ref"),
                InvoiceStatus.valueOf(rs.getString("invoice_status")),
                new Money(rs.getLong("total_amount_minor"), currency),
                new Money(rs.getLong("amount_paid_minor"), currency), rs.getLong("version"),
                instant(rs.getTimestamp("issued_at")), instant(rs.getTimestamp("paid_at")),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
    }

    private InvoiceLineItem mapLine(ResultSet rs, int row) throws SQLException {
        String currency = rs.getString("currency_code");
        return new InvoiceLineItem(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("invoice_id"), rs.getString("rated_usage_id"),
                rs.getString("line_type"), rs.getString("description"),
                rs.getLong("quantity_base_units"),
                new Money(rs.getLong("unit_price_minor"), currency),
                new Money(rs.getLong("amount_minor"), currency),
                instant(rs.getTimestamp("period_start")), instant(rs.getTimestamp("period_end")),
                instant(rs.getTimestamp("created_at")));
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    public record StoredCommand(String fingerprint, InvoiceCommandResult result) {}
}
