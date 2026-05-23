package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.CreditTransaction;
import com.example.platform.billing.domain.CreditWallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditWalletJdbcRepositoryTest {

    private CreditWalletJdbcRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-billing-h2.sql")
                .build();
        repository = new CreditWalletJdbcRepository(new JdbcTemplate(dataSource));
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
