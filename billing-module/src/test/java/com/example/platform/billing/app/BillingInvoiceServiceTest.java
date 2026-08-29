package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.billing.domain.BillingInvoice;
import com.example.platform.billing.domain.InvoiceCommand;
import com.example.platform.billing.domain.InvoiceStatus;
import com.example.platform.billing.domain.RatedUsageRecord;
import com.example.platform.billing.infrastructure.BillingInvoiceRepository;
import com.example.platform.billing.infrastructure.BillingLedgerJdbcRepository;
import com.example.platform.billing.infrastructure.RatedUsageJdbcRepository;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BillingInvoiceServiceTest {

    @Test
    void addRatedUsageRejectsAmountsThatDoNotMatchDurableRating() {
        BillingInvoiceRepository invoices = mock(BillingInvoiceRepository.class);
        BillingLedgerJdbcRepository ledger = mock(BillingLedgerJdbcRepository.class);
        RatedUsageJdbcRepository rated = mock(RatedUsageJdbcRepository.class);
        BillingInvoiceService service = new BillingInvoiceService(invoices, ledger, rated);
        PrincipalRef principal = PrincipalRef.tenantScoped(
                "tenant-a", PrincipalType.USER, "user-a");
        Instant now = Instant.parse("2026-08-29T10:00:00Z");
        BillingInvoice invoice = new BillingInvoice("invoice-a", principal, "contract",
                null, null, InvoiceStatus.OPEN, new Money(0, "USD"), new Money(0, "USD"),
                1, null, null, now, now);
        when(invoices.findByTenantAndId("tenant-a", "invoice-a")).thenReturn(Optional.of(invoice));
        when(rated.findByTenantAndId("tenant-a", "rated-a")).thenReturn(Optional.of(
                new RatedUsageRecord("rated-a", "tenant-a", "bill-a", "rule-a", 1,
                        2, new Money(10, "USD"), Map.of(), now, "trace", "rate-key", "fp")));
        InvoiceCommand tampered = InvoiceCommand.addRatedUsage(principal, "invoice-a",
                "line-a", "rated-a", 2, new Money(5, "USD"), new Money(11, "USD"),
                1, "line-key", "actor", "line", "trace", now);

        assertThrows(IllegalStateException.class, () -> service.execute(tampered));
        verifyNoInteractions(ledger);
    }

    @Test
    void invoicePrincipalScopeIncludesWorkspace() {
        BillingInvoiceRepository invoices = mock(BillingInvoiceRepository.class);
        BillingLedgerJdbcRepository ledger = mock(BillingLedgerJdbcRepository.class);
        RatedUsageJdbcRepository rated = mock(RatedUsageJdbcRepository.class);
        BillingInvoiceService service = new BillingInvoiceService(invoices, ledger, rated);
        Instant now = Instant.parse("2026-08-29T10:00:00Z");
        PrincipalRef owner = new PrincipalRef(
                "tenant-a", PrincipalType.USER, "user-a", "workspace-a", null);
        PrincipalRef otherWorkspace = new PrincipalRef(
                "tenant-a", PrincipalType.USER, "user-a", "workspace-b", null);
        when(invoices.findByTenantAndId("tenant-a", "invoice-a")).thenReturn(Optional.of(
                new BillingInvoice("invoice-a", owner, "contract", null, null,
                        InvoiceStatus.OPEN, new Money(0, "USD"), new Money(0, "USD"),
                        1, null, null, now, now)));
        when(rated.findByTenantAndId("tenant-a", "rated-a")).thenReturn(Optional.of(
                new RatedUsageRecord("rated-a", "tenant-a", "bill-a", "rule-a", 1,
                        1, new Money(5, "USD"), Map.of(), now, "trace", "rate-key", "fp")));
        InvoiceCommand command = InvoiceCommand.addRatedUsage(otherWorkspace, "invoice-a",
                "line-a", "rated-a", 1, new Money(5, "USD"), new Money(5, "USD"),
                1, "line-key", "actor", "line", "trace", now);

        assertThrows(IllegalStateException.class, () -> service.execute(command));
    }
}
