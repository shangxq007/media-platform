package com.example.platform.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BillingLedgerEntryTest {

    private static final PrincipalRef PRINCIPAL = PrincipalRef.tenantScoped(
            "tenant-a", PrincipalType.USER, "user-a");

    @Test
    void rejectsUnknownEntryTypeAndNegativeCharge() {
        assertThrows(IllegalArgumentException.class, () -> entry("UNKNOWN", new Money(1, "USD")));
        assertThrows(IllegalArgumentException.class, () ->
                entry(BillingLedgerEntry.TYPE_CHARGE, new Money(-1, "USD")));
    }

    @Test
    void signedAdjustmentIsExplicitAndTyped() {
        BillingLedgerEntry adjustment = entry(
                BillingLedgerEntry.TYPE_ADJUSTMENT, new Money(-10, "USD"));
        assertEquals(-10, adjustment.amountMinor());
        assertEquals(BillingLedgerEntry.TYPE_ADJUSTMENT, adjustment.entryType());
    }

    private static BillingLedgerEntry entry(String type, Money amount) {
        return new BillingLedgerEntry("entry-1", PRINCIPAL, type, amount,
                "INVOICE", "invoice-1", "test", "ledger-test", null,
                Instant.parse("2026-08-29T10:00:00Z"));
    }
}
