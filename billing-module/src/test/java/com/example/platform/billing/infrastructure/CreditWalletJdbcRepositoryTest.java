package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.CreditTransaction;
import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditWalletJdbcRepositoryTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private CreditWalletJdbcRepository repository;

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

        repository = new CreditWalletJdbcRepository(jdbc);
    }

    @Test
    void shouldPersistWalletAndTransaction() {
        Instant now = Instant.now();
        CreditWallet wallet = new CreditWallet("wlt-1", "t1", "ws1", "u1", 100L, "USD", "ACTIVE", now, now);
        repository.saveWallet(wallet);

        CreditTransaction txn = new CreditTransaction(
                "ctx-1", "wlt-1", CreditTransaction.TYPE_CREDIT, 100L, 100L,
                "TEST", "ref-1", "top-up", now);
        repository.saveTransaction(txn);

        assertEquals(1, repository.loadAllWallets().size());
        assertEquals(1, repository.loadAllTransactions().size());
        assertTrue(repository.findWalletByTenantAndUser("t1", "u1").isPresent());
    }
}
