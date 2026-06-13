package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.billing.infrastructure.CreditWalletJdbcRepository;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CreditWalletServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private CreditWalletService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS credit_wallet (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                workspace_id varchar(64),
                user_id varchar(128),
                balance_minor bigint not null default 0,
                currency_code varchar(8) not null,
                status varchar(32) not null,
                created_at timestamp not null,
                updated_at timestamp not null
            )
        """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS credit_transaction (
                id varchar(64) primary key,
                wallet_id varchar(64) not null,
                transaction_type varchar(32) not null,
                amount_minor bigint not null,
                balance_after_minor bigint not null,
                reference_type varchar(64),
                reference_id varchar(128),
                description text,
                created_at timestamp not null
            )
        """);
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE credit_transaction CASCADE");
        jdbc.execute("TRUNCATE TABLE credit_wallet CASCADE");

        var repo = new CreditWalletJdbcRepository(jdbc);
        service = new CreditWalletService(Optional.of(repo));
    }

    @Test
    void shouldCreateWallet() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        assertNotNull(wallet);
        assertNotNull(wallet.walletId());
        assertEquals(0, wallet.balanceMinor());
        assertEquals("USD", wallet.currencyCode());
        assertEquals("ACTIVE", wallet.status());
    }

    @Test
    void shouldGetWalletByTenant() {
        service.createWallet("t1", "ws-1", "u1", "USD");
        CreditWallet found = service.getWalletByTenant("t1", "u1");
        assertNotNull(found);
        assertEquals("t1", found.tenantId());
    }

    @Test
    void shouldCreditWallet() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        CreditWallet credited = service.credit(wallet.walletId(), 1000, "topup", "tp-1", "Top up");
        assertEquals(1000, credited.balanceMinor());
    }

    @Test
    void shouldDebitWallet() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        service.credit(wallet.walletId(), 1000, "topup", "tp-1", "Top up");
        CreditWallet debited = service.debit(wallet.walletId(), 300, "render", "job-1", "Render charge");
        assertEquals(700, debited.balanceMinor());
    }

    @Test
    void shouldThrowOnInsufficientBalance() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        assertThrows(IllegalStateException.class, () ->
                service.debit(wallet.walletId(), 100, "render", "job-1", "Charge"));
    }

    @Test
    void shouldThrowOnDebitUnknownWallet() {
        assertThrows(IllegalArgumentException.class, () ->
                service.debit("nonexistent", 100, "render", "job-1", "Charge"));
    }

    @Test
    void shouldReserveAndFinalize() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        service.credit(wallet.walletId(), 1000, "topup", "tp-1", "Top up");
        String reservationId = service.reserve(wallet.walletId(), 500, "render", "job-1", "Reserve");
        assertNotNull(reservationId);
        service.finalize(wallet.walletId(), reservationId, 400, "render", "job-1", "Finalize");
        CreditWallet updated = service.getWalletByTenant("t1", "u1");
        assertEquals(600, updated.balanceMinor());
    }

    @Test
    void shouldReserveAndRelease() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        service.credit(wallet.walletId(), 1000, "topup", "tp-1", "Top up");
        String reservationId = service.reserve(wallet.walletId(), 500, "render", "job-1", "Reserve");
        service.release(wallet.walletId(), reservationId, "render", "job-1", "Release");
        CreditWallet updated = service.getWalletByTenant("t1", "u1");
        assertEquals(1000, updated.balanceMinor());
    }

    @Test
    void shouldThrowOnInsufficientAvailableForReservation() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        service.credit(wallet.walletId(), 100, "topup", "tp-1", "Top up");
        assertThrows(IllegalStateException.class, () ->
                service.reserve(wallet.walletId(), 200, "render", "job-1", "Reserve"));
    }

    @Test
    void shouldGetTransactions() {
        CreditWallet wallet = service.createWallet("t1", "ws-1", "u1", "USD");
        service.credit(wallet.walletId(), 1000, "topup", "tp-1", "Top up");
        service.debit(wallet.walletId(), 300, "render", "job-1", "Charge");
        List<CreditTransaction> txns = service.getTransactions(wallet.walletId());
        assertEquals(2, txns.size());
    }

    @Test
    void shouldGetWalletsByTenant() {
        service.createWallet("t1", "ws-1", "u1", "USD");
        service.createWallet("t1", "ws-2", "u2", "USD");
        service.createWallet("t2", "ws-1", "u3", "USD");
        List<CreditWallet> t1Wallets = service.getWalletsByTenant("t1");
        assertEquals(2, t1Wallets.size());
    }
}
