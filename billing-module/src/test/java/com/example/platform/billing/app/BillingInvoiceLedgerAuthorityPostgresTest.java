package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.billing.domain.BillingInvoice;
import com.example.platform.billing.domain.BillingLedgerEntry;
import com.example.platform.billing.domain.InvoiceCommand;
import com.example.platform.billing.domain.InvoiceCommandResult;
import com.example.platform.billing.domain.InvoiceStatus;
import com.example.platform.billing.infrastructure.BillingInvoiceRepository;
import com.example.platform.billing.infrastructure.BillingLedgerJdbcRepository;
import com.example.platform.billing.infrastructure.RatedUsageJdbcRepository;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class BillingInvoiceLedgerAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-29T09:00:00Z");
    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static BillingInvoiceService service;
    private static BillingInvoiceRepository invoices;
    private static BillingLedgerJdbcRepository ledger;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(BillingInvoiceService.class);
        invoices = context.getBean(BillingInvoiceRepository.class);
        ledger = context.getBean(BillingLedgerJdbcRepository.class);
        jdbc.execute("DROP TABLE IF EXISTS billing_invoice_command, billing_ledger_entry, invoice_line_item, billing_invoice, rated_usage_record CASCADE");
        jdbc.execute("""
                CREATE TABLE billing_invoice (
                    id varchar(64) not null, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    contract_id varchar(64), provider_code varchar(64), external_invoice_ref varchar(255),
                    invoice_status varchar(32) not null, total_amount_minor bigint not null,
                    amount_paid_minor bigint not null, currency_code varchar(3) not null,
                    version bigint not null, issued_at timestamptz, paid_at timestamptz,
                    created_at timestamptz not null, updated_at timestamptz not null,
                    primary key (tenant_id, id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE invoice_line_item (
                    id varchar(64) not null, tenant_id varchar(64) not null,
                    invoice_id varchar(64) not null, rated_usage_id varchar(64),
                    line_type varchar(32) not null, description text not null,
                    quantity_base_units bigint not null, unit_price_minor bigint not null,
                    amount_minor bigint not null, currency_code varchar(3) not null,
                    period_start timestamptz, period_end timestamptz, created_at timestamptz not null,
                    primary key (tenant_id, id),
                    unique (tenant_id, rated_usage_id),
                    foreign key (tenant_id, invoice_id) references billing_invoice(tenant_id, id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE billing_invoice_command (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    invoice_id varchar(64) not null, idempotency_key varchar(255) not null,
                    command_type varchar(32) not null, payload_fingerprint varchar(64) not null,
                    result_version bigint not null, result_status varchar(32) not null,
                    result_total_minor bigint not null, result_currency varchar(3) not null,
                    actor varchar(128) not null, reason varchar(512) not null,
                    trace_id varchar(128) not null, created_at timestamptz not null,
                    unique (tenant_id, idempotency_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE billing_ledger_entry (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    workspace_id varchar(64), entry_type varchar(32) not null,
                    amount_minor bigint not null, currency_code varchar(3) not null,
                    reference_type varchar(64) not null, reference_id varchar(128) not null,
                    description text not null, idempotency_key varchar(255) not null,
                    payload_fingerprint varchar(64) not null, created_at timestamptz not null,
                    unique (tenant_id, idempotency_key),
                    unique (tenant_id, reference_type, reference_id, entry_type),
                    check (entry_type in ('CHARGE', 'REFUND', 'ADJUSTMENT', 'CREDIT', 'DEBIT', 'DISCOUNT')),
                    check (entry_type = 'ADJUSTMENT' or amount_minor >= 0)
                )
                """);
        jdbc.execute("""
                CREATE TABLE rated_usage_record (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    billable_usage_id varchar(64) not null, pricing_rule_id varchar(64) not null,
                    pricing_rule_version bigint not null, quantity_base_units bigint not null,
                    rated_amount_minor bigint not null, currency_code varchar(3) not null,
                    rating_details text not null, rated_at timestamptz not null,
                    trace_id varchar(128) not null, idempotency_key varchar(255) not null,
                    payload_fingerprint varchar(64) not null, unique (tenant_id, idempotency_key),
                    unique (tenant_id, billable_usage_id, pricing_rule_id, pricing_rule_version)
                )
                """);
    }

    @AfterAll
    static void closeDatabase() {
        if (context != null) context.close();
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_invoice_ledger_insert ON billing_ledger_entry");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_invoice_ledger_for_test()");
        jdbc.execute("TRUNCATE billing_invoice_command, billing_ledger_entry, invoice_line_item, billing_invoice, rated_usage_record");
        persistRated("rated-1", 2, 10, "USD");
        persistRated("rated-issue", 2, 10, "USD");
        persistRated("rated-concurrent", 1, 7, "USD");
        persistRated("rated-rollback", 1, 9, "USD");
        persistRated("rated-paid", 1, 6, "USD");
        persistRated("rated-void", 1, 8, "USD");
    }

    @Test
    void legalTransitionsUseCasAndExactReplayWhileMismatchRejects() {
        InvoiceCommand create = InvoiceCommand.create(principal("tenant-a"), "invoice-1",
                "contract-1", new Money(0, "USD"), 0, "create-key", "actor", "create", "trace", NOW);
        InvoiceCommandResult first = service.execute(create);
        InvoiceCommandResult replay = service.execute(create);

        assertEquals(first, replay);
        assertEquals(InvoiceStatus.OPEN, first.status());
        assertEquals(1L, first.version());
        assertThrows(IllegalStateException.class, () -> service.execute(
                InvoiceCommand.create(principal("tenant-a"), "different", "contract-1",
                        new Money(0, "USD"), 0, "create-key", "actor", "create", "trace", NOW)));
        assertThrows(IllegalStateException.class, () -> service.execute(
                InvoiceCommand.finalizeInvoice(principal("tenant-a"), "invoice-1", 0,
                        "bad-cas", "actor", "finalize", "trace", NOW)));
    }

    @Test
    void lineMoneyMustMatchInvoiceCurrencyAndRatedUsageCannotBeBilledTwice() {
        create("tenant-a", "invoice-a");
        create("tenant-a", "invoice-b");
        service.execute(InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-a",
                "line-a", "rated-1", 2, new Money(5, "USD"), new Money(10, "USD"),
                1, "line-key", "actor", "line", "trace", NOW));

        assertThrows(IllegalStateException.class, () -> service.execute(
                InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-a",
                        "line-eur", "rated-eur", 1, new Money(5, "EUR"), new Money(5, "EUR"),
                        2, "line-eur-key", "actor", "line", "trace", NOW)));
        assertThrows(IllegalStateException.class, () -> service.execute(
                InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-b",
                        "line-b", "rated-1", 2, new Money(5, "USD"), new Money(10, "USD"),
                        1, "duplicate-rated", "actor", "line", "trace", NOW)));
    }

    @Test
    void finalizationIsAtomicIdempotentAndPostsOneExactLedgerCharge() {
        create("tenant-a", "invoice-issue");
        service.execute(InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-issue",
                "line-issue", "rated-issue", 2, new Money(5, "USD"), new Money(10, "USD"),
                1, "add-issue", "actor", "line", "trace", NOW));
        InvoiceCommand finalize = InvoiceCommand.finalizeInvoice(principal("tenant-a"),
                "invoice-issue", 2, "finalize-key", "actor", "issue", "trace", NOW);

        InvoiceCommandResult first = service.execute(finalize);
        InvoiceCommandResult replay = service.execute(finalize);

        assertEquals(first, replay);
        assertEquals(InvoiceStatus.ISSUED, first.status());
        assertEquals(new Money(10, "USD"), first.total());
        assertEquals(1, ledger.findByTenant("tenant-a").size());
    }

    @Test
    void markPaidIsProjectionOnlyAndVoidUsesLegalAppendOnlyReversal() {
        create("tenant-a", "invoice-paid");
        service.execute(InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-paid",
                "line-paid", "rated-paid", 1, new Money(6, "USD"), new Money(6, "USD"),
                1, "add-paid", "actor", "line", "trace", NOW));
        service.execute(InvoiceCommand.finalizeInvoice(principal("tenant-a"), "invoice-paid", 2,
                "issue-paid", "actor", "issue", "trace", NOW));
        InvoiceCommandResult paid = service.execute(InvoiceCommand.markPaid(
                principal("tenant-a"), "invoice-paid", 3, "mark-paid", "actor",
                "payment projection", "trace", NOW));
        assertEquals(InvoiceStatus.PAID, paid.status());
        assertThrows(IllegalStateException.class, () -> service.execute(InvoiceCommand.voidInvoice(
                principal("tenant-a"), "invoice-paid", 4, "void-paid", "actor",
                "illegal void", "trace", NOW)));

        create("tenant-a", "invoice-void");
        service.execute(InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-void",
                "line-void", "rated-void", 1, new Money(8, "USD"), new Money(8, "USD"),
                1, "add-void", "actor", "line", "trace", NOW));
        service.execute(InvoiceCommand.finalizeInvoice(principal("tenant-a"), "invoice-void", 2,
                "issue-void", "actor", "issue", "trace", NOW));
        InvoiceCommandResult voided = service.execute(InvoiceCommand.voidInvoice(
                principal("tenant-a"), "invoice-void", 3, "void-issued", "actor",
                "void", "trace", NOW));
        assertEquals(InvoiceStatus.VOID, voided.status());
        assertEquals(3, ledger.findByTenant("tenant-a").size());
    }

    @Test
    void concurrentFinalizeHasOneCorrectResult() throws Exception {
        create("tenant-a", "invoice-concurrent");
        service.execute(InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-concurrent",
                "line-concurrent", "rated-concurrent", 1, new Money(7, "USD"),
                new Money(7, "USD"), 1, "add-concurrent", "actor", "line", "trace", NOW));
        InvoiceCommand command = InvoiceCommand.finalizeInvoice(principal("tenant-a"),
                "invoice-concurrent", 2, "same-finalize", "actor", "issue", "trace", NOW);

        List<InvoiceCommandResult> results = concurrently(10, () -> service.execute(command));

        assertEquals(1, results.stream().distinct().count());
        assertEquals(1, ledger.findByTenant("tenant-a").size());
        assertEquals(InvoiceStatus.ISSUED,
                invoices.findByTenantAndId("tenant-a", "invoice-concurrent").orElseThrow().status());
    }

    @Test
    void ledgerFailureRollsBackInvoiceFinalization() {
        create("tenant-a", "invoice-rollback");
        service.execute(InvoiceCommand.addRatedUsage(principal("tenant-a"), "invoice-rollback",
                "line-rollback", "rated-rollback", 1, new Money(9, "USD"),
                new Money(9, "USD"), 1, "add-rollback", "actor", "line", "trace", NOW));
        jdbc.execute("""
                CREATE FUNCTION fail_invoice_ledger_for_test() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'ledger failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_invoice_ledger_insert BEFORE INSERT ON billing_ledger_entry
                FOR EACH ROW EXECUTE FUNCTION fail_invoice_ledger_for_test()
                """);

        assertThrows(RuntimeException.class, () -> service.execute(
                InvoiceCommand.finalizeInvoice(principal("tenant-a"), "invoice-rollback", 2,
                        "rollback-finalize", "actor", "issue", "trace", NOW)));

        BillingInvoice invoice = invoices.findByTenantAndId("tenant-a", "invoice-rollback").orElseThrow();
        assertEquals(InvoiceStatus.OPEN, invoice.status());
        assertEquals(2L, invoice.version());
        assertEquals(0, ledger.findByTenant("tenant-a").size());
    }

    @Test
    void ledgerReplayMismatchTenantReadAndDuplicateReferenceAreEnforced() {
        BillingLedgerEntry entry = BillingLedgerEntry.charge("ledger-1", principal("tenant-a"),
                new Money(11, "USD"), "INVOICE", "invoice-ledger", "charge",
                "ledger-key", NOW);
        assertEquals(entry, ledger.append(entry));
        assertEquals(entry, ledger.append(entry));
        assertEquals(1, ledger.findByTenant("tenant-a").size());
        assertEquals(0, ledger.findByTenant("tenant-b").size());
        assertThrows(IllegalStateException.class, () -> ledger.append(
                BillingLedgerEntry.charge("ledger-2", principal("tenant-a"),
                        new Money(12, "USD"), "INVOICE", "invoice-ledger", "different",
                        "ledger-key", NOW)));
        assertThrows(IllegalStateException.class, () -> ledger.append(
                BillingLedgerEntry.charge("ledger-3", principal("tenant-a"),
                        new Money(11, "USD"), "INVOICE", "invoice-ledger", "charge",
                        "different-key", NOW)));
    }

    private static InvoiceCommandResult create(String tenant, String invoice) {
        return service.execute(InvoiceCommand.create(principal(tenant), invoice, "contract",
                new Money(0, "USD"), 0, "create-" + invoice,
                "actor", "create", "trace", NOW));
    }

    private static void persistRated(String id, long quantity, long amount, String currency) {
        jdbc.update("""
                INSERT INTO rated_usage_record
                (id, tenant_id, billable_usage_id, pricing_rule_id, pricing_rule_version,
                 quantity_base_units, rated_amount_minor, currency_code, rating_details,
                 rated_at, trace_id, idempotency_key, payload_fingerprint)
                VALUES (?, 'tenant-a', ?, 'rule', 1, ?, ?, ?, '', ?, 'trace', ?, ?)
                """, id, "bill-" + id, quantity, amount, currency,
                java.sql.Timestamp.from(NOW), "rate-" + id, "fp-" + id);
    }

    private static PrincipalRef principal(String tenant) {
        return PrincipalRef.tenantScoped(tenant, PrincipalType.USER, "user-1");
    }

    private static <T> List<T> concurrently(int workers, java.util.concurrent.Callable<T> action)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < workers; index++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        static DataSource dataSource;
        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate() { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean BillingInvoiceRepository invoices(JdbcTemplate jdbc) {
            return new BillingInvoiceRepository(jdbc);
        }
        @Bean BillingLedgerJdbcRepository ledger(JdbcTemplate jdbc) {
            return new BillingLedgerJdbcRepository(jdbc);
        }
        @Bean BillingInvoiceService service(BillingInvoiceRepository invoices,
                                            BillingLedgerJdbcRepository ledger,
                                            RatedUsageJdbcRepository rated) {
            return new BillingInvoiceService(invoices, ledger, rated);
        }
        @Bean RatedUsageJdbcRepository rated(JdbcTemplate jdbc) {
            return new RatedUsageJdbcRepository(jdbc);
        }
    }
}
