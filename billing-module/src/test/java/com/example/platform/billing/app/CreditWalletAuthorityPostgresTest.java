package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.billing.domain.CreditWalletCommand;
import com.example.platform.billing.domain.CreditWalletCommandResult;
import com.example.platform.billing.infrastructure.CreditWalletJdbcRepository;
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

class CreditWalletAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");
    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static CreditWalletService service;
    private static CreditWalletJdbcRepository repository;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(CreditWalletService.class);
        repository = context.getBean(CreditWalletJdbcRepository.class);
        jdbc.execute("DROP TABLE IF EXISTS credit_wallet_command, credit_transaction, credit_reservation, credit_wallet CASCADE");
        jdbc.execute("""
                CREATE TABLE credit_wallet (
                    id varchar(64) not null, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    workspace_id varchar(64), balance_minor bigint not null,
                    currency_code varchar(3) not null, status varchar(32) not null,
                    version bigint not null, created_at timestamptz not null,
                    updated_at timestamptz not null, primary key (tenant_id, id),
                    unique (tenant_id, principal_type, principal_id, workspace_id, currency_code),
                    check (balance_minor >= 0), check (version > 0)
                )
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX uq_credit_wallet_principal_test ON credit_wallet(
                    tenant_id, principal_type, principal_id, coalesce(workspace_id, ''), currency_code)
                """);
        jdbc.execute("""
                CREATE TABLE credit_reservation (
                    id varchar(64) not null, tenant_id varchar(64) not null,
                    wallet_id varchar(64) not null, amount_minor bigint not null,
                    currency_code varchar(3) not null, status varchar(32) not null,
                    version bigint not null, reference_type varchar(64) not null,
                    reference_id varchar(128) not null, created_at timestamptz not null,
                    updated_at timestamptz not null, primary key (tenant_id, id),
                    foreign key (tenant_id, wallet_id) references credit_wallet(tenant_id, id),
                    check (amount_minor > 0), check (version > 0)
                )
                """);
        jdbc.execute("""
                CREATE TABLE credit_transaction (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    wallet_id varchar(64) not null, reservation_id varchar(64),
                    transaction_type varchar(32) not null, amount_minor bigint not null,
                    currency_code varchar(3) not null, balance_after_minor bigint not null,
                    reference_type varchar(64) not null, reference_id varchar(128) not null,
                    description text not null, idempotency_key varchar(255) not null,
                    payload_fingerprint varchar(64) not null, created_at timestamptz not null,
                    unique (tenant_id, idempotency_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE credit_wallet_command (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    wallet_id varchar(64) not null, idempotency_key varchar(255) not null,
                    command_type varchar(32) not null, payload_fingerprint varchar(64) not null,
                    result_balance_minor bigint not null, result_currency varchar(3) not null,
                    result_wallet_version bigint not null, result_reservation_id varchar(64),
                    result_reservation_status varchar(32), actor varchar(128) not null,
                    reason varchar(512) not null, trace_id varchar(128) not null,
                    created_at timestamptz not null, unique (tenant_id, idempotency_key)
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
        jdbc.execute("DROP TRIGGER IF EXISTS fail_credit_transaction ON credit_transaction");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_credit_transaction_for_test()");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_credit_command ON credit_wallet_command");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_credit_command_for_test()");
        jdbc.execute("TRUNCATE credit_wallet_command, credit_transaction, credit_reservation, credit_wallet");
    }

    @Test
    void creditDebitReplayMismatchCurrencyAndInsufficientFundsAreEnforced() {
        createWallet("wallet-1", "tenant-a", "USD");
        CreditWalletCommand credit = CreditWalletCommand.credit(principal("tenant-a"), "wallet-1",
                new Money(100, "USD"), 1, "TOPUP", "topup-1", "credit",
                "credit-key", "actor", "credit", "trace", NOW);
        assertEquals(new Money(100, "USD"), service.execute(credit).wallet().balance());
        assertEquals(service.execute(credit), service.execute(credit));
        assertThrows(IllegalStateException.class, () -> service.execute(
                CreditWalletCommand.credit(principal("tenant-a"), "wallet-1",
                        new Money(99, "USD"), 1, "TOPUP", "topup-1", "different",
                        "credit-key", "actor", "credit", "trace", NOW)));
        assertThrows(IllegalStateException.class, () -> service.execute(
                CreditWalletCommand.debit(principal("tenant-a"), "wallet-1",
                        new Money(1, "EUR"), 2, "USAGE", "usage-eur", "debit",
                        "eur-key", "actor", "debit", "trace", NOW)));
        assertThrows(IllegalStateException.class, () -> service.execute(
                CreditWalletCommand.debit(principal("tenant-a"), "wallet-1",
                        new Money(101, "USD"), 2, "USAGE", "usage-big", "debit",
                        "big-key", "actor", "debit", "trace", NOW)));
        assertEquals(new Money(70, "USD"), service.execute(
                CreditWalletCommand.debit(principal("tenant-a"), "wallet-1",
                        new Money(30, "USD"), 2, "USAGE", "usage-1", "debit",
                        "debit-key", "actor", "debit", "trace", NOW)).wallet().balance());
    }

    @Test
    void reserveFinalizeReleaseAreWalletScopedReplaySafeAndRestartSafe() {
        createWallet("wallet-a", "tenant-a", "USD");
        createWallet("wallet-b", "tenant-a", "USD", "user-2");
        service.execute(CreditWalletCommand.credit(principal("tenant-a"), "wallet-a",
                new Money(100, "USD"), 1, "TOPUP", "a", "credit",
                "credit-a", "actor", "credit", "trace", NOW));
        service.execute(CreditWalletCommand.credit(principal("tenant-a", "user-2"), "wallet-b",
                new Money(100, "USD"), 1, "TOPUP", "b", "credit",
                "credit-b", "actor", "credit", "trace", NOW));
        CreditWalletCommand reserve = CreditWalletCommand.reserve(principal("tenant-a"), "wallet-a",
                "reservation-a", new Money(80, "USD"), 2, "RENDER", "job-a", "reserve",
                "reserve-a", "actor", "reserve", "trace", NOW);
        assertEquals("reservation-a", service.execute(reserve).reservationId());
        assertThrows(IllegalStateException.class, () -> service.execute(
                CreditWalletCommand.reserve(principal("tenant-a"), "wallet-a", "reservation-too-big",
                        new Money(21, "USD"), 3, "RENDER", "job-big", "reserve",
                        "reserve-big", "actor", "reserve", "trace", NOW)));

        CreditWalletService restarted = new CreditWalletService(repository);
        CreditWalletCommandResult finalized = restarted.execute(CreditWalletCommand.finalizeReservation(
                principal("tenant-a"), "wallet-a", "reservation-a", new Money(70, "USD"), 3,
                "RENDER", "job-a", "finalize", "finalize-a", "actor", "finalize", "trace", NOW));
        assertEquals(new Money(30, "USD"), finalized.wallet().balance());
        assertEquals(finalized, restarted.execute(CreditWalletCommand.finalizeReservation(
                principal("tenant-a"), "wallet-a", "reservation-a", new Money(70, "USD"), 3,
                "RENDER", "job-a", "finalize", "finalize-a", "actor", "finalize", "trace", NOW)));

        CreditWalletCommand reserveB = CreditWalletCommand.reserve(principal("tenant-a", "user-2"),
                "wallet-b", "reservation-b", new Money(50, "USD"), 2,
                "RENDER", "job-b", "reserve", "reserve-b", "actor", "reserve", "trace", NOW);
        service.execute(reserveB);
        assertEquals("RELEASED", service.execute(CreditWalletCommand.releaseReservation(
                principal("tenant-a", "user-2"), "wallet-b", "reservation-b", 3,
                "RENDER", "job-b", "release", "release-b", "actor", "release", "trace", NOW))
                .reservationStatus());
    }

    @Test
    void tenantIsolationFailsClosed() {
        createWallet("wallet-tenant", "tenant-a", "USD");
        assertThrows(IllegalStateException.class, () -> service.execute(
                CreditWalletCommand.create(principal("tenant-a"), "wallet-duplicate", "USD",
                        "different-create-key", "actor", "create", "trace", NOW)));
        assertThrows(IllegalStateException.class, () -> service.execute(
                CreditWalletCommand.credit(principal("tenant-b"), "wallet-tenant",
                        new Money(1, "USD"), 1, "TOPUP", "x", "credit",
                        "cross-tenant", "actor", "credit", "trace", NOW)));
        assertEquals(0, repository.findWalletsByTenant("tenant-b").size());
    }

    @Test
    void concurrentDebitsAndReservationsNeverOverspend() throws Exception {
        createWallet("wallet-concurrent", "tenant-a", "USD");
        service.execute(CreditWalletCommand.credit(principal("tenant-a"), "wallet-concurrent",
                new Money(100, "USD"), 1, "TOPUP", "c", "credit",
                "credit-c", "actor", "credit", "trace", NOW));
        List<Outcome> debits = concurrently(2, index -> () -> attempt(() -> service.execute(
                CreditWalletCommand.debit(principal("tenant-a"), "wallet-concurrent",
                        new Money(80, "USD"), 2, "USAGE", "debit-" + index, "debit",
                        "debit-c-" + index, "actor", "debit", "trace", NOW))));
        assertEquals(1, debits.stream().filter(Outcome::success).count());
        assertEquals(20L, repository.findWalletForUpdate("tenant-a", "wallet-concurrent")
                .orElseThrow().balanceMinor());

        createWallet("wallet-reserve", "tenant-a", "USD", "user-2");
        service.execute(CreditWalletCommand.credit(principal("tenant-a", "user-2"), "wallet-reserve",
                new Money(100, "USD"), 1, "TOPUP", "r", "credit",
                "credit-r", "actor", "credit", "trace", NOW));
        List<Outcome> reserves = concurrently(2, index -> () -> attempt(() -> service.execute(
                CreditWalletCommand.reserve(principal("tenant-a", "user-2"), "wallet-reserve",
                        "reservation-" + index, new Money(80, "USD"), 2,
                        "RENDER", "job-" + index, "reserve", "reserve-c-" + index,
                        "actor", "reserve", "trace", NOW))));
        assertEquals(1, reserves.stream().filter(Outcome::success).count());
    }

    @Test
    void concurrentFinalizeExactReplayHasOneCorrectTransaction() throws Exception {
        createWallet("wallet-finalize", "tenant-a", "USD");
        service.execute(CreditWalletCommand.credit(principal("tenant-a"), "wallet-finalize",
                new Money(100, "USD"), 1, "TOPUP", "f", "credit",
                "credit-f", "actor", "credit", "trace", NOW));
        service.execute(CreditWalletCommand.reserve(principal("tenant-a"), "wallet-finalize",
                "reservation-finalize", new Money(60, "USD"), 2, "RENDER", "job-f", "reserve",
                "reserve-f", "actor", "reserve", "trace", NOW));
        CreditWalletCommand command = CreditWalletCommand.finalizeReservation(
                principal("tenant-a"), "wallet-finalize", "reservation-finalize",
                new Money(50, "USD"), 3, "RENDER", "job-f", "finalize",
                "finalize-f", "actor", "finalize", "trace", NOW);

        List<CreditWalletCommandResult> results = concurrently(10, ignored -> () -> service.execute(command));

        assertEquals(1, results.stream().distinct().count());
        assertEquals(new Money(50, "USD"), results.get(0).wallet().balance());
        assertEquals(3, repository.findTransactions("tenant-a", "wallet-finalize").size());
    }

    @Test
    void transactionAndCommandAuditFailuresRollBackWalletAndReservation() {
        createWallet("wallet-rollback", "tenant-a", "USD");
        jdbc.execute("""
                CREATE FUNCTION fail_credit_transaction_for_test() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'transaction failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_credit_transaction BEFORE INSERT ON credit_transaction
                FOR EACH ROW EXECUTE FUNCTION fail_credit_transaction_for_test()
                """);
        assertThrows(RuntimeException.class, () -> service.execute(
                CreditWalletCommand.credit(principal("tenant-a"), "wallet-rollback",
                        new Money(100, "USD"), 1, "TOPUP", "rollback", "credit",
                        "transaction-fail", "actor", "credit", "trace", NOW)));
        assertEquals(0L, repository.findWalletForUpdate("tenant-a", "wallet-rollback")
                .orElseThrow().balanceMinor());
        jdbc.execute("DROP TRIGGER fail_credit_transaction ON credit_transaction");
        jdbc.execute("DROP FUNCTION fail_credit_transaction_for_test()");

        jdbc.execute("""
                CREATE FUNCTION fail_credit_command_for_test() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'audit failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_credit_command BEFORE INSERT ON credit_wallet_command
                FOR EACH ROW EXECUTE FUNCTION fail_credit_command_for_test()
                """);
        assertThrows(RuntimeException.class, () -> service.execute(
                CreditWalletCommand.credit(principal("tenant-a"), "wallet-rollback",
                        new Money(100, "USD"), 1, "TOPUP", "audit", "credit",
                        "audit-fail", "actor", "credit", "trace", NOW)));
        assertEquals(0L, repository.findWalletForUpdate("tenant-a", "wallet-rollback")
                .orElseThrow().balanceMinor());
        assertEquals(0, repository.findTransactions("tenant-a", "wallet-rollback").size());
    }

    private static void createWallet(String walletId, String tenant, String currency) {
        createWallet(walletId, tenant, currency, "user-1");
    }

    private static void createWallet(String walletId, String tenant, String currency, String user) {
        service.execute(CreditWalletCommand.create(principal(tenant, user), walletId,
                currency, "create-" + walletId, "actor", "create", "trace", NOW));
    }

    private static PrincipalRef principal(String tenant) { return principal(tenant, "user-1"); }
    private static PrincipalRef principal(String tenant, String user) {
        return PrincipalRef.tenantScoped(tenant, PrincipalType.USER, user);
    }

    private static Outcome attempt(Runnable action) {
        try { action.run(); return new Outcome(true); }
        catch (IllegalStateException expected) { return new Outcome(false); }
    }

    private static <T> List<T> concurrently(int workers,
            java.util.function.IntFunction<java.util.concurrent.Callable<T>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new java.util.ArrayList<>();
            for (int index = 0; index < workers; index++) {
                var action = actions.apply(index);
                futures.add(pool.submit(() -> {
                    ready.countDown(); start.await(10, TimeUnit.SECONDS); return action.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS); start.countDown();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); }
    }

    record Outcome(boolean success) {}

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        static DataSource dataSource;
        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate() { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean CreditWalletJdbcRepository repository(JdbcTemplate jdbc) {
            return new CreditWalletJdbcRepository(jdbc);
        }
        @Bean CreditWalletService service(CreditWalletJdbcRepository repository) {
            return new CreditWalletService(repository);
        }
    }
}
