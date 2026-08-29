package com.example.platform.billing.app;

import com.example.platform.billing.domain.BillingInvoice;
import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.billing.domain.InvoiceCommand;
import com.example.platform.billing.domain.InvoiceCommandResult;
import com.example.platform.billing.domain.InvoiceCommandType;
import com.example.platform.billing.domain.InvoiceLineItem;
import com.example.platform.billing.domain.InvoiceStatus;
import com.example.platform.billing.infrastructure.BillingInvoiceRepository;
import com.example.platform.billing.infrastructure.BillingLedgerJdbcRepository;
import com.example.platform.billing.infrastructure.RatedUsageJdbcRepository;
import com.example.platform.shared.commercial.Money;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The sole Billing invoice command authority. */
@Service
public class BillingInvoiceService {

    private final BillingInvoiceRepository invoices;
    private final BillingLedgerJdbcRepository ledger;
    private final RatedUsageJdbcRepository ratedUsage;

    public BillingInvoiceService(BillingInvoiceRepository invoices,
                                 BillingLedgerJdbcRepository ledger,
                                 RatedUsageJdbcRepository ratedUsage) {
        this.invoices = invoices;
        this.ledger = ledger;
        this.ratedUsage = ratedUsage;
    }

    @Transactional
    public InvoiceCommandResult execute(InvoiceCommand command) {
        String tenantId = command.principal().tenantId();
        invoices.lockCommand(tenantId, command.idempotencyKey());
        BillingInvoiceRepository.StoredCommand prior = invoices
                .findCommand(tenantId, command.idempotencyKey()).orElse(null);
        if (prior != null) {
            if (!prior.fingerprint().equals(command.fingerprint())) {
                throw new IllegalStateException("Idempotency key reused with different invoice command payload");
            }
            return prior.result();
        }

        BillingInvoice invoice = switch (command.commandType()) {
            case CREATE -> create(command);
            case ADD_RATED_USAGE -> addRatedUsage(command);
            case FINALIZE -> finalizeInvoice(command);
            case MARK_PAID -> markPaid(command);
            case VOID -> voidInvoice(command);
        };
        InvoiceCommandResult result = new InvoiceCommandResult(invoice.invoiceId(),
                invoice.status(), invoice.version(), invoice.total());
        invoices.saveCommand(command, result);
        return result;
    }

    private BillingInvoice create(InvoiceCommand command) {
        if (command.expectedVersion() != 0) {
            throw new IllegalStateException("Invoice create expectedVersion must be zero");
        }
        if (command.unitPrice().amountMinor() != 0) {
            throw new IllegalStateException("Invoice create money selects currency and must be zero");
        }
        return invoices.insertInvoice(command);
    }

    private BillingInvoice addRatedUsage(InvoiceCommand command) {
        BillingInvoice current = requireInvoice(command);
        requireVersionAndState(current, command.expectedVersion(), InvoiceStatus.OPEN);
        if (!current.total().currency().equals(command.unitPrice().currency())
                || !current.total().currency().equals(command.lineAmount().currency())) {
            throw new IllegalStateException("Invoice line currency must match invoice currency");
        }
        var rated = ratedUsage.findByTenantAndId(
                        command.principal().tenantId(), command.ratedUsageId())
                .orElseThrow(() -> new IllegalStateException("Rated usage not found in tenant"));
        if (rated.quantityBaseUnits() != command.quantityBaseUnits()
                || !rated.amount().equals(command.lineAmount())) {
            throw new IllegalStateException("Invoice line must exactly match durable rated usage");
        }
        return invoices.addLine(command);
    }

    private BillingInvoice finalizeInvoice(InvoiceCommand command) {
        BillingInvoice current = requireInvoice(command);
        requireVersionAndState(current, command.expectedVersion(), InvoiceStatus.OPEN);
        List<InvoiceLineItem> lines = invoices.findLines(command.principal().tenantId(),
                command.invoiceId());
        if (lines.isEmpty()) throw new IllegalStateException("Cannot finalize an invoice without lines");
        Money total = new Money(0, current.total().currency());
        for (InvoiceLineItem line : lines) total = total.add(line.amount());
        BillingInvoice issued = invoices.transition(command.principal().tenantId(), command.invoiceId(),
                command.expectedVersion(), InvoiceStatus.OPEN, InvoiceStatus.ISSUED,
                total, command.occurredAt());
        String entryId = "ble_" + command.fingerprint().substring(0, 24);
        ledger.append(BillingLedgerEntry.charge(entryId, command.principal(), total,
                "INVOICE", command.invoiceId(), "Invoice issued",
                "invoice:issue:" + command.invoiceId(), command.occurredAt()));
        return issued;
    }

    private BillingInvoice markPaid(InvoiceCommand command) {
        BillingInvoice current = requireInvoice(command);
        requireVersionAndState(current, command.expectedVersion(), InvoiceStatus.ISSUED);
        return invoices.transition(command.principal().tenantId(), command.invoiceId(),
                command.expectedVersion(), InvoiceStatus.ISSUED, InvoiceStatus.PAID,
                current.total(), command.occurredAt());
    }

    private BillingInvoice voidInvoice(InvoiceCommand command) {
        BillingInvoice current = requireInvoice(command);
        if (current.version() != command.expectedVersion()
                || (current.status() != InvoiceStatus.OPEN && current.status() != InvoiceStatus.ISSUED)) {
            throw new IllegalStateException("Invoice void CAS or legal transition rejected");
        }
        BillingInvoice voided = invoices.transition(command.principal().tenantId(), command.invoiceId(),
                command.expectedVersion(), current.status(), InvoiceStatus.VOID,
                current.total(), command.occurredAt());
        if (current.status() == InvoiceStatus.ISSUED) {
            Money reversal = current.total().negate();
            ledger.append(new BillingLedgerEntry(
                    "ble_" + command.fingerprint().substring(0, 24), command.principal(),
                    BillingLedgerEntry.TYPE_ADJUSTMENT, reversal, "INVOICE_VOID", command.invoiceId(),
                    "Invoice void reversal", "invoice:void:" + command.invoiceId(), null,
                    command.occurredAt()));
        }
        return voided;
    }

    private BillingInvoice requireInvoice(InvoiceCommand command) {
        BillingInvoice current = invoices.findByTenantAndId(
                        command.principal().tenantId(), command.invoiceId())
                .orElseThrow(() -> new IllegalStateException("Invoice not found in tenant"));
        if (!current.principal().equals(command.principal())) {
            throw new IllegalStateException("Invoice principal mismatch");
        }
        return current;
    }

    private static void requireVersionAndState(BillingInvoice invoice, long expectedVersion,
                                               InvoiceStatus expectedStatus) {
        if (invoice.version() != expectedVersion || invoice.status() != expectedStatus) {
            throw new IllegalStateException("Invoice CAS or legal transition rejected");
        }
    }
}
