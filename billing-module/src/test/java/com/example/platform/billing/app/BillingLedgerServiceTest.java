package com.example.platform.billing.app;

import com.example.platform.billing.domain.BillingLedgerEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BillingLedgerServiceTest {

    private BillingLedgerService service;

    @BeforeEach
    void setUp() {
        service = new BillingLedgerService();
    }

    @Test
    void shouldWriteEntry() {
        BillingLedgerEntry entry = service.writeEntry(
                "t1", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE,
                500, "USD", "render", "job-1", "Render job charge");
        assertNotNull(entry);
        assertNotNull(entry.entryId());
        assertEquals("t1", entry.tenantId());
        assertEquals(500, entry.amountMinor());
        assertEquals("USD", entry.currencyCode());
    }

    @Test
    void shouldGetEntry() {
        BillingLedgerEntry entry = service.writeEntry(
                "t1", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE,
                500, "USD", "render", "job-1", "Test");
        BillingLedgerEntry found = service.getEntry(entry.entryId());
        assertNotNull(found);
        assertEquals(entry.entryId(), found.entryId());
    }

    @Test
    void shouldGetLedgerByTenant() {
        service.writeEntry("t1", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE, 500, "USD", "render", "j1", "c1");
        service.writeEntry("t1", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE, 300, "USD", "render", "j2", "c2");
        service.writeEntry("t2", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE, 200, "USD", "render", "j3", "c3");
        List<BillingLedgerEntry> t1Ledger = service.getLedger("t1");
        assertEquals(2, t1Ledger.size());
    }

    @Test
    void shouldGetLedgerByType() {
        service.writeEntry("t1", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE, 500, "USD", "render", "j1", "c1");
        service.writeEntry("t1", "ws-1", "u1", BillingLedgerEntry.TYPE_REFUND, 100, "USD", "render", "j2", "c2");
        List<BillingLedgerEntry> charges = service.getLedgerByTenantAndType("t1", BillingLedgerEntry.TYPE_CHARGE);
        assertEquals(1, charges.size());
    }

    @Test
    void shouldCalculateBalance() {
        service.writeEntry("t1", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE, 500, "USD", "render", "j1", "c1");
        service.writeEntry("t1", "ws-1", "u1", BillingLedgerEntry.TYPE_CHARGE, 300, "USD", "render", "j2", "c2");
        service.writeEntry("t1", "ws-1", "u1", BillingLedgerEntry.TYPE_REFUND, 100, "USD", "render", "j3", "c3");
        long balance = service.getBalance("t1");
        assertEquals(700, balance);
    }

    @Test
    void shouldReturnZeroBalanceForUnknownTenant() {
        long balance = service.getBalance("nonexistent");
        assertEquals(0, balance);
    }
}
