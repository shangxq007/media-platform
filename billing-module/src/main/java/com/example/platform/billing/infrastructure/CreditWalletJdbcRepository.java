package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.CreditTransaction;
import com.example.platform.billing.domain.CreditWallet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class CreditWalletJdbcRepository {

    private final JdbcTemplate jdbc;

    public CreditWalletJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveWallet(CreditWallet wallet) {
        int updated = jdbc.update("""
                UPDATE credit_wallet SET
                tenant_id = ?, workspace_id = ?, user_id = ?, balance_minor = ?,
                currency_code = ?, status = ?, updated_at = ?
                WHERE id = ?
                """,
                wallet.tenantId(),
                wallet.workspaceId(),
                wallet.userId(),
                wallet.balanceMinor(),
                wallet.currencyCode(),
                wallet.status(),
                Timestamp.from(wallet.updatedAt()),
                wallet.walletId());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO credit_wallet
                    (id, tenant_id, workspace_id, user_id, balance_minor, currency_code, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    wallet.walletId(),
                    wallet.tenantId(),
                    wallet.workspaceId(),
                    wallet.userId(),
                    wallet.balanceMinor(),
                    wallet.currencyCode(),
                    wallet.status(),
                    Timestamp.from(wallet.createdAt()),
                    Timestamp.from(wallet.updatedAt()));
        }
    }

    public void saveTransaction(CreditTransaction txn) {
        jdbc.update("""
                INSERT INTO credit_transaction
                (id, wallet_id, transaction_type, amount_minor, balance_after_minor,
                 reference_type, reference_id, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                txn.transactionId(),
                txn.walletId(),
                txn.transactionType(),
                txn.amountMinor(),
                txn.balanceAfterMinor(),
                txn.referenceType(),
                txn.referenceId(),
                txn.description(),
                Timestamp.from(txn.createdAt()));
    }

    public List<CreditWallet> loadAllWallets() {
        return jdbc.query("SELECT * FROM credit_wallet ORDER BY created_at", this::mapWallet);
    }

    public List<CreditTransaction> loadAllTransactions() {
        return jdbc.query("SELECT * FROM credit_transaction ORDER BY created_at", this::mapTransaction);
    }

    public Optional<CreditWallet> findWalletByTenantAndUser(String tenantId, String userId) {
        List<CreditWallet> rows = jdbc.query(
                "SELECT * FROM credit_wallet WHERE tenant_id = ? AND user_id = ? LIMIT 1",
                this::mapWallet,
                tenantId,
                userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private CreditWallet mapWallet(ResultSet rs, int rowNum) throws SQLException {
        return new CreditWallet(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("workspace_id"),
                rs.getString("user_id"),
                rs.getLong("balance_minor"),
                rs.getString("currency_code"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private CreditTransaction mapTransaction(ResultSet rs, int rowNum) throws SQLException {
        return new CreditTransaction(
                rs.getString("id"),
                rs.getString("wallet_id"),
                rs.getString("transaction_type"),
                rs.getLong("amount_minor"),
                rs.getLong("balance_after_minor"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getString("description"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : Instant.now();
    }
}
