package com.example.platform.billing.app;

import com.example.platform.billing.domain.CreditReservation;
import com.example.platform.billing.domain.CreditTransaction;
import com.example.platform.billing.domain.CreditWallet;
import com.example.platform.billing.domain.CreditWalletCommand;
import com.example.platform.billing.domain.CreditWalletCommandResult;
import com.example.platform.billing.infrastructure.CreditWalletJdbcRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sole durable credit wallet command authority. */
@Service
public class CreditWalletService {

    private final CreditWalletJdbcRepository repository;

    public CreditWalletService(CreditWalletJdbcRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CreditWalletCommandResult execute(CreditWalletCommand command) {
        String tenantId = command.principal().tenantId();
        repository.lockCommand(tenantId, command.idempotencyKey());
        CreditWalletJdbcRepository.StoredCommand prior = repository
                .findCommand(tenantId, command.idempotencyKey()).orElse(null);
        if (prior != null) {
            if (!prior.fingerprint().equals(command.fingerprint())) {
                throw new IllegalStateException("Idempotency key reused with different wallet command payload");
            }
            return prior.result();
        }

        CreditWalletCommandResult result = switch (command.commandType()) {
            case CREATE -> create(command);
            case CREDIT -> creditCommand(command);
            case DEBIT -> debitCommand(command);
            case RESERVE -> reserveCommand(command);
            case FINALIZE -> finalizeCommand(command);
            case RELEASE -> releaseCommand(command);
        };
        repository.saveCommand(command, result);
        return result;
    }

    private CreditWalletCommandResult create(CreditWalletCommand command) {
        if (command.expectedVersion() != 0) throw new IllegalStateException("wallet create version must be zero");
        CreditWallet wallet = repository.insertWallet(command);
        return new CreditWalletCommandResult(wallet, null, null);
    }

    private CreditWalletCommandResult creditCommand(CreditWalletCommand command) {
        CreditWallet wallet = requireWallet(command);
        requireVersionCurrency(wallet, command);
        Money next = wallet.balance().add(command.amount());
        CreditWallet updated = repository.updateWallet(wallet, next, command.expectedVersion(),
                command.occurredAt());
        appendTransaction(command, updated, CreditTransaction.TYPE_CREDIT, null, command.amount());
        return new CreditWalletCommandResult(updated, null, null);
    }

    private CreditWalletCommandResult debitCommand(CreditWalletCommand command) {
        CreditWallet wallet = requireWallet(command);
        requireVersionCurrency(wallet, command);
        long reserved = repository.activeReservedMinor(wallet.tenantId(), wallet.walletId());
        long available = Math.subtractExact(wallet.balanceMinor(), reserved);
        if (available < command.amount().amountMinor()) {
            throw new IllegalStateException("Insufficient available wallet balance");
        }
        Money next = wallet.balance().subtract(command.amount());
        CreditWallet updated = repository.updateWallet(wallet, next, command.expectedVersion(),
                command.occurredAt());
        appendTransaction(command, updated, CreditTransaction.TYPE_DEBIT, null, command.amount());
        return new CreditWalletCommandResult(updated, null, null);
    }

    private CreditWalletCommandResult reserveCommand(CreditWalletCommand command) {
        CreditWallet wallet = requireWallet(command);
        requireVersionCurrency(wallet, command);
        long reserved = repository.activeReservedMinor(wallet.tenantId(), wallet.walletId());
        long available = Math.subtractExact(wallet.balanceMinor(), reserved);
        if (available < command.amount().amountMinor()) {
            throw new IllegalStateException("Insufficient available wallet balance for reservation");
        }
        CreditReservation reservation = repository.insertReservation(command);
        CreditWallet updated = repository.updateWallet(wallet, wallet.balance(),
                command.expectedVersion(), command.occurredAt());
        appendTransaction(command, updated, CreditTransaction.TYPE_RESERVE,
                reservation.reservationId(), reservation.amount());
        return new CreditWalletCommandResult(updated, reservation.reservationId(), reservation.status());
    }

    private CreditWalletCommandResult finalizeCommand(CreditWalletCommand command) {
        CreditWallet wallet = requireWallet(command);
        requireVersionCurrency(wallet, command);
        CreditReservation reservation = requireActiveReservation(command);
        if (!reservation.amount().currency().equals(wallet.currencyCode())) {
            throw new IllegalStateException("Reservation currency mismatch");
        }
        if (!reservation.amount().currency().equals(command.amount().currency())) {
            throw new IllegalStateException("Reservation currency mismatch");
        }
        if (command.amount().amountMinor() > reservation.amount().amountMinor()) {
            throw new IllegalStateException("Final amount exceeds reserved amount");
        }
        CreditReservation finalized = repository.transitionReservation(
                reservation, "FINALIZED", command.occurredAt());
        Money next = wallet.balance().subtract(command.amount());
        CreditWallet updated = repository.updateWallet(wallet, next, command.expectedVersion(),
                command.occurredAt());
        appendTransaction(command, updated, CreditTransaction.TYPE_FINALIZE,
                finalized.reservationId(), command.amount());
        return new CreditWalletCommandResult(updated, finalized.reservationId(), finalized.status());
    }

    private CreditWalletCommandResult releaseCommand(CreditWalletCommand command) {
        CreditWallet wallet = requireWallet(command);
        if (wallet.version() != command.expectedVersion()) throw new IllegalStateException("Wallet CAS rejected");
        CreditReservation reservation = requireActiveReservation(command);
        if (!reservation.amount().currency().equals(wallet.currencyCode())) {
            throw new IllegalStateException("Reservation currency mismatch");
        }
        CreditReservation released = repository.transitionReservation(
                reservation, "RELEASED", command.occurredAt());
        CreditWallet updated = repository.updateWallet(wallet, wallet.balance(),
                command.expectedVersion(), command.occurredAt());
        appendTransaction(command, updated, CreditTransaction.TYPE_RELEASE,
                released.reservationId(), released.amount());
        return new CreditWalletCommandResult(updated, released.reservationId(), released.status());
    }

    private CreditWallet requireWallet(CreditWalletCommand command) {
        CreditWallet wallet = repository.findWalletForUpdate(
                        command.principal().tenantId(), command.walletId())
                .orElseThrow(() -> new IllegalStateException("Wallet not found in tenant"));
        if (!wallet.principal().equals(command.principal())) {
            throw new IllegalStateException("Wallet principal mismatch");
        }
        return wallet;
    }

    private CreditReservation requireActiveReservation(CreditWalletCommand command) {
        CreditReservation reservation = repository.findReservationForUpdate(
                        command.principal().tenantId(), command.walletId(), command.reservationId())
                .orElseThrow(() -> new IllegalStateException("Reservation not found in wallet"));
        if (!"ACTIVE".equals(reservation.status())) {
            throw new IllegalStateException("Reservation is not active");
        }
        return reservation;
    }

    private static void requireVersionCurrency(CreditWallet wallet, CreditWalletCommand command) {
        if (wallet.version() != command.expectedVersion()) throw new IllegalStateException("Wallet CAS rejected");
        if (!wallet.currencyCode().equals(command.amount().currency())) {
            throw new IllegalStateException("Wallet command currency mismatch");
        }
    }

    private void appendTransaction(CreditWalletCommand command, CreditWallet wallet,
                                   String type, String reservationId, Money amount) {
        repository.appendTransaction(new CreditTransaction(
                "ctx_" + command.fingerprint().substring(0, 24), command.principal().tenantId(),
                command.walletId(), reservationId, type, amount, wallet.balance(),
                command.referenceType(), command.referenceId(), command.description(),
                command.idempotencyKey(), command.fingerprint(), command.occurredAt()));
    }

    @Transactional
    public CreditWallet createWallet(String tenantId, String workspaceId, String userId,
                                     String currencyCode) {
        PrincipalRef principal = new PrincipalRef(tenantId, PrincipalType.USER, userId,
                workspaceId, null);
        String walletId = Ids.newId("wlt");
        return execute(CreditWalletCommand.create(principal, walletId, currencyCode,
                "wallet:create:" + tenantId + ":" + userId + ":"
                        + (workspaceId == null ? "" : workspaceId) + ":" + currencyCode,
                "billing", "create wallet", "wallet-create", Instant.now())).wallet();
    }

    @Transactional(readOnly = true)
    public CreditWallet getWalletByTenant(String tenantId, String userId) {
        return repository.findWalletByPrincipal(
                PrincipalRef.tenantScoped(tenantId, PrincipalType.USER, userId), "USD").orElse(null);
    }

    @Transactional
    public CreditWallet credit(String tenantId, String walletId, long amountMinor,
                               String referenceType, String referenceId, String description) {
        CreditWallet wallet = repository.findWalletForUpdate(tenantId, walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found in tenant"));
        return execute(CreditWalletCommand.credit(wallet.principal(), walletId,
                new Money(amountMinor, wallet.currencyCode()), wallet.version(), referenceType,
                referenceId, description, "wallet:credit:" + tenantId + ":" + referenceType
                        + ":" + referenceId, "billing", description, "wallet-credit", Instant.now()))
                .wallet();
    }

    @Transactional
    public CreditWallet debit(String tenantId, String walletId, long amountMinor,
                              String referenceType, String referenceId, String description) {
        CreditWallet wallet = repository.findWalletForUpdate(tenantId, walletId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found in tenant"));
        return execute(CreditWalletCommand.debit(wallet.principal(), walletId,
                new Money(amountMinor, wallet.currencyCode()), wallet.version(), referenceType,
                referenceId, description, "wallet:debit:" + tenantId + ":" + referenceType
                        + ":" + referenceId, "billing", description, "wallet-debit", Instant.now()))
                .wallet();
    }

    @Transactional(readOnly = true)
    public List<CreditTransaction> getTransactions(String tenantId, String walletId) {
        return repository.findTransactions(tenantId, walletId);
    }

    @Transactional(readOnly = true)
    public List<CreditWallet> getWalletsByTenant(String tenantId) {
        return repository.findWalletsByTenant(tenantId);
    }
}
