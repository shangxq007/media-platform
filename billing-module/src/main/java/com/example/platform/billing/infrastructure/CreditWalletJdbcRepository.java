package com.example.platform.billing.infrastructure;

import com.example.platform.billing.domain.CreditReservation;
import com.example.platform.billing.domain.CreditTransaction;
import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.billing.domain.CreditWalletCommand;
import com.example.platform.billing.domain.CreditWalletCommandResult;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sole durable wallet, reservation, transaction, and wallet-command writer. */
@Repository
public class CreditWalletJdbcRepository {

    private final JdbcTemplate jdbc;

    public CreditWalletJdbcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lockCommand(String tenantId, String key) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                tenantId + ":credit:" + key);
    }

    public Optional<StoredCommand> findCommand(String tenantId, String key) {
        return jdbc.query("""
                SELECT c.*, w.principal_type, w.principal_id, w.workspace_id,
                       w.status, w.created_at AS wallet_created_at
                FROM credit_wallet_command c
                JOIN credit_wallet w ON w.tenant_id = c.tenant_id AND w.id = c.wallet_id
                WHERE c.tenant_id = ? AND c.idempotency_key = ?
                """, (rs, row) -> {
                    PrincipalRef principal = new PrincipalRef(rs.getString("tenant_id"),
                            PrincipalType.valueOf(rs.getString("principal_type")),
                            rs.getString("principal_id"), rs.getString("workspace_id"), null);
                    Money balance = new Money(rs.getLong("result_balance_minor"),
                            rs.getString("result_currency"));
                    CreditWallet wallet = new CreditWallet(rs.getString("wallet_id"), principal,
                            balance, rs.getString("status"), rs.getLong("result_wallet_version"),
                            rs.getTimestamp("wallet_created_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant());
                    return new StoredCommand(rs.getString("payload_fingerprint"),
                            new CreditWalletCommandResult(wallet,
                                    rs.getString("result_reservation_id"),
                                    rs.getString("result_reservation_status")));
                }, tenantId, key).stream().findFirst();
    }

    public void saveCommand(CreditWalletCommand command, CreditWalletCommandResult result) {
        jdbc.update("""
                INSERT INTO credit_wallet_command
                (id, tenant_id, wallet_id, idempotency_key, command_type, payload_fingerprint,
                 result_balance_minor, result_currency, result_wallet_version,
                 result_reservation_id, result_reservation_status,
                 actor, reason, trace_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "cwc_" + command.fingerprint().substring(0, 24), command.principal().tenantId(),
                command.walletId(), command.idempotencyKey(), command.commandType().name(),
                command.fingerprint(), result.wallet().balanceMinor(), result.wallet().currencyCode(),
                result.wallet().version(), result.reservationId(), result.reservationStatus(),
                command.actor(), command.reason(), command.traceId(), Timestamp.from(command.occurredAt()));
    }

    public CreditWallet insertWallet(CreditWalletCommand command) {
        try {
            jdbc.update("""
                    INSERT INTO credit_wallet
                    (id, tenant_id, principal_type, principal_id, workspace_id, balance_minor,
                     currency_code, status, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 0, ?, 'ACTIVE', 1, ?, ?)
                    """, command.walletId(), command.principal().tenantId(),
                    command.principal().principalType().name(), command.principal().principalId(),
                    command.principal().workspaceId(), command.createCurrency(),
                    Timestamp.from(command.occurredAt()), Timestamp.from(command.occurredAt()));
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalStateException("Wallet identity already exists", duplicate);
        }
        return findWalletForUpdate(command.principal().tenantId(), command.walletId()).orElseThrow();
    }

    public Optional<CreditWallet> findWalletForUpdate(String tenantId, String walletId) {
        return jdbc.query("""
                SELECT * FROM credit_wallet WHERE tenant_id = ? AND id = ? FOR UPDATE
                """, this::mapWallet, tenantId, walletId).stream().findFirst();
    }

    public Optional<CreditWallet> findWalletByPrincipal(PrincipalRef principal, String currency) {
        return jdbc.query("""
                SELECT * FROM credit_wallet
                WHERE tenant_id = ? AND principal_type = ? AND principal_id = ?
                  AND currency_code = ? AND (workspace_id = ? OR (workspace_id IS NULL AND ? IS NULL))
                """, this::mapWallet, principal.tenantId(), principal.principalType().name(),
                principal.principalId(), currency, principal.workspaceId(), principal.workspaceId())
                .stream().findFirst();
    }

    public List<CreditWallet> findWalletsByTenant(String tenantId) {
        return jdbc.query("SELECT * FROM credit_wallet WHERE tenant_id = ? ORDER BY created_at",
                this::mapWallet, tenantId);
    }

    public CreditWallet updateWallet(CreditWallet wallet, Money balance,
                                     long expectedVersion, Instant at) {
        int updated = jdbc.update("""
                UPDATE credit_wallet SET balance_minor = ?, version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND id = ? AND version = ? AND status = 'ACTIVE'
                """, balance.amountMinor(), Timestamp.from(at), wallet.tenantId(), wallet.walletId(),
                expectedVersion);
        if (updated != 1) throw new IllegalStateException("Wallet CAS rejected");
        return findWalletForUpdate(wallet.tenantId(), wallet.walletId()).orElseThrow();
    }

    public long activeReservedMinor(String tenantId, String walletId) {
        Long total = jdbc.queryForObject("""
                SELECT COALESCE(sum(amount_minor), 0) FROM credit_reservation
                WHERE tenant_id = ? AND wallet_id = ? AND status = 'ACTIVE'
                """, Long.class, tenantId, walletId);
        return total == null ? 0 : total;
    }

    public CreditReservation insertReservation(CreditWalletCommand command) {
        try {
            jdbc.update("""
                    INSERT INTO credit_reservation
                    (id, tenant_id, wallet_id, amount_minor, currency_code, status, version,
                     reference_type, reference_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?, ?, ?)
                    """, command.reservationId(), command.principal().tenantId(), command.walletId(),
                    command.amount().amountMinor(), command.amount().currency(), command.referenceType(),
                    command.referenceId(), Timestamp.from(command.occurredAt()),
                    Timestamp.from(command.occurredAt()));
        } catch (DuplicateKeyException duplicate) {
            throw new IllegalStateException("Reservation identity already exists", duplicate);
        }
        return findReservationForUpdate(command.principal().tenantId(), command.walletId(),
                command.reservationId()).orElseThrow();
    }

    public Optional<CreditReservation> findReservationForUpdate(
            String tenantId, String walletId, String reservationId) {
        return jdbc.query("""
                SELECT * FROM credit_reservation
                WHERE tenant_id = ? AND wallet_id = ? AND id = ? FOR UPDATE
                """, this::mapReservation, tenantId, walletId, reservationId).stream().findFirst();
    }

    public CreditReservation transitionReservation(CreditReservation reservation,
                                                   String target, Instant at) {
        int updated = jdbc.update("""
                UPDATE credit_reservation SET status = ?, version = version + 1, updated_at = ?
                WHERE tenant_id = ? AND wallet_id = ? AND id = ?
                  AND version = ? AND status = 'ACTIVE'
                """, target, Timestamp.from(at), reservation.tenantId(), reservation.walletId(),
                reservation.reservationId(), reservation.version());
        if (updated != 1) throw new IllegalStateException("Reservation transition rejected");
        return findReservationForUpdate(reservation.tenantId(), reservation.walletId(),
                reservation.reservationId()).orElseThrow();
    }

    public void appendTransaction(CreditTransaction transaction) {
        jdbc.update("""
                INSERT INTO credit_transaction
                (id, tenant_id, wallet_id, reservation_id, transaction_type,
                 amount_minor, currency_code, balance_after_minor, reference_type,
                 reference_id, description, idempotency_key, payload_fingerprint, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, transaction.transactionId(), transaction.tenantId(), transaction.walletId(),
                transaction.reservationId(), transaction.transactionType(), transaction.amountMinor(),
                transaction.amount().currency(), transaction.balanceAfterMinor(),
                transaction.referenceType(), transaction.referenceId(), transaction.description(),
                transaction.idempotencyKey(), transaction.payloadFingerprint(),
                Timestamp.from(transaction.createdAt()));
    }

    public List<CreditTransaction> findTransactions(String tenantId, String walletId) {
        return jdbc.query("""
                SELECT * FROM credit_transaction
                WHERE tenant_id = ? AND wallet_id = ? ORDER BY created_at, id
                """, this::mapTransaction, tenantId, walletId);
    }

    private CreditWallet mapWallet(ResultSet rs, int row) throws SQLException {
        PrincipalRef principal = new PrincipalRef(rs.getString("tenant_id"),
                PrincipalType.valueOf(rs.getString("principal_type")), rs.getString("principal_id"),
                rs.getString("workspace_id"), null);
        return new CreditWallet(rs.getString("id"), principal,
                new Money(rs.getLong("balance_minor"), rs.getString("currency_code")),
                rs.getString("status"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private CreditReservation mapReservation(ResultSet rs, int row) throws SQLException {
        return new CreditReservation(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("wallet_id"), new Money(rs.getLong("amount_minor"),
                rs.getString("currency_code")), rs.getString("status"), rs.getLong("version"),
                rs.getString("reference_type"), rs.getString("reference_id"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private CreditTransaction mapTransaction(ResultSet rs, int row) throws SQLException {
        String currency = rs.getString("currency_code");
        return new CreditTransaction(rs.getString("id"), rs.getString("tenant_id"),
                rs.getString("wallet_id"), rs.getString("reservation_id"),
                rs.getString("transaction_type"), new Money(rs.getLong("amount_minor"), currency),
                new Money(rs.getLong("balance_after_minor"), currency),
                rs.getString("reference_type"), rs.getString("reference_id"),
                rs.getString("description"), rs.getString("idempotency_key"),
                rs.getString("payload_fingerprint"), rs.getTimestamp("created_at").toInstant());
    }

    public record StoredCommand(String fingerprint, CreditWalletCommandResult result) {}
}
