package com.example.platform.billing.app;

import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.billing.infrastructure.BillingLedgerJdbcRepository;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BillingLedgerServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private BillingLedgerService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS billing_ledger_entry (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                principal_type varchar(32) not null,
                principal_id varchar(128) not null,
                workspace_id varchar(64),
                entry_type varchar(32) not null,
                amount_minor bigint not null,
                currency_code varchar(3) not null,
                reference_type varchar(64) not null,
                reference_id varchar(128) not null,
                description text not null,
                idempotency_key varchar(255) not null,
                payload_fingerprint varchar(64) not null,
                created_at timestamptz not null,
                unique (tenant_id, idempotency_key),
                unique (tenant_id, reference_type, reference_id, entry_type)
            )
        """);
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE billing_ledger_entry CASCADE");

        var repo = new BillingLedgerJdbcRepository(jdbc);
        service = new BillingLedgerService(Optional.of(repo));
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
        BillingLedgerEntry found = service.getEntry("t1", entry.entryId());
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
