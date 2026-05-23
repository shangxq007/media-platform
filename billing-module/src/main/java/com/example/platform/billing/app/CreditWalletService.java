package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.billing.infrastructure.CreditWalletJdbcRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CreditWalletService {

    private static final Logger log = LoggerFactory.getLogger(CreditWalletService.class);

    private final ConcurrentHashMap<String, CreditWallet> wallets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CreditTransaction> transactions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> reservations = new ConcurrentHashMap<>();
    private final Optional<CreditWalletJdbcRepository> jdbcRepository;

    public CreditWalletService() {
        this(Optional.empty());
    }

    @Autowired
    public CreditWalletService(Optional<CreditWalletJdbcRepository> jdbcRepository) {
        this.jdbcRepository = jdbcRepository != null ? jdbcRepository : Optional.empty();
    }

    public void hydrateWallet(CreditWallet wallet) {
        wallets.put(wallet.walletId(), wallet);
    }

    public void hydrateTransaction(CreditTransaction txn) {
        transactions.put(txn.transactionId(), txn);
    }

    public CreditWallet createWallet(String tenantId, String workspaceId, String userId,
                                      String currencyCode) {
        String walletId = Ids.newId("wlt");
        CreditWallet wallet = new CreditWallet(
                walletId, tenantId, workspaceId, userId,
                0L, currencyCode, "ACTIVE", Instant.now(), Instant.now());
        persistWallet(wallet);
        log.info("CreditWalletService: created wallet {} tenant={}", walletId, tenantId);
        return wallet;
    }

    public CreditWallet getWallet(String walletId) {
        return wallets.get(walletId);
    }

    public CreditWallet getWalletByTenant(String tenantId, String userId) {
        CreditWallet cached = wallets.values().stream()
                .filter(w -> tenantId.equals(w.tenantId()) && userId.equals(w.userId()))
                .findFirst()
                .orElse(null);
        if (cached != null) {
            return cached;
        }
        return jdbcRepository.flatMap(r -> r.findWalletByTenantAndUser(tenantId, userId))
                .map(w -> {
                    hydrateWallet(w);
                    return w;
                })
                .orElse(null);
    }

    public CreditWallet credit(String walletId, long amountMinor, String referenceType,
                                String referenceId, String description) {
        CreditWallet existing = wallets.get(walletId);
        if (existing == null) {
            throw new IllegalArgumentException("Wallet not found: " + walletId);
        }
        long newBalance = existing.balanceMinor() + amountMinor;
        CreditWallet updated = new CreditWallet(
                existing.walletId(), existing.tenantId(), existing.workspaceId(),
                existing.userId(), newBalance, existing.currencyCode(),
                existing.status(), existing.createdAt(), Instant.now());
        persistWallet(updated);
        recordTransaction(walletId, CreditTransaction.TYPE_CREDIT, amountMinor,
                newBalance, referenceType, referenceId, description);
        log.info("CreditWalletService: credited wallet {} amount={} newBalance={}",
                walletId, amountMinor, newBalance);
        return updated;
    }

    public CreditWallet debit(String walletId, long amountMinor, String referenceType,
                               String referenceId, String description) {
        CreditWallet existing = wallets.get(walletId);
        if (existing == null) {
            throw new IllegalArgumentException("Wallet not found: " + walletId);
        }
        if (existing.balanceMinor() < amountMinor) {
            throw new IllegalStateException("Insufficient balance in wallet: " + walletId);
        }
        long newBalance = existing.balanceMinor() - amountMinor;
        CreditWallet updated = new CreditWallet(
                existing.walletId(), existing.tenantId(), existing.workspaceId(),
                existing.userId(), newBalance, existing.currencyCode(),
                existing.status(), existing.createdAt(), Instant.now());
        persistWallet(updated);
        recordTransaction(walletId, CreditTransaction.TYPE_DEBIT, amountMinor,
                newBalance, referenceType, referenceId, description);
        log.info("CreditWalletService: debited wallet {} amount={} newBalance={}",
                walletId, amountMinor, newBalance);
        return updated;
    }

    public String reserve(String walletId, long amountMinor, String referenceType,
                           String referenceId, String description) {
        String reservationId = Ids.newId("res");
        synchronized (this) {
            CreditWallet existing = wallets.get(walletId);
            if (existing == null) {
                throw new IllegalArgumentException("Wallet not found: " + walletId);
            }
            long reservedTotal = reservations.values().stream().mapToLong(Long::longValue).sum();
            long available = existing.balanceMinor() - reservedTotal;
            if (available < amountMinor) {
                throw new IllegalStateException("Insufficient available balance for reservation");
            }
            reservations.put(reservationId, amountMinor);
        }
        recordTransaction(walletId, CreditTransaction.TYPE_RESERVE, amountMinor,
                getWallet(walletId).balanceMinor(), referenceType, referenceId, description);
        log.info("CreditWalletService: reserved {} from wallet {} reservation={}",
                amountMinor, walletId, reservationId);
        return reservationId;
    }

    public void finalize(String walletId, String reservationId, long actualAmountMinor,
                          String referenceType, String referenceId, String description) {
        Long reservedAmount = reservations.remove(reservationId);
        if (reservedAmount == null) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }
        CreditWallet existing = wallets.get(walletId);
        if (existing == null) {
            throw new IllegalArgumentException("Wallet not found: " + walletId);
        }

        long newBalance = existing.balanceMinor() - actualAmountMinor;

        CreditWallet updated = new CreditWallet(
                existing.walletId(), existing.tenantId(), existing.workspaceId(),
                existing.userId(), newBalance, existing.currencyCode(),
                existing.status(), existing.createdAt(), Instant.now());
        persistWallet(updated);
        recordTransaction(walletId, CreditTransaction.TYPE_FINALIZE, actualAmountMinor,
                newBalance, referenceType, referenceId, description);
        log.info("CreditWalletService: finalized reservation {} actual={} newBalance={}",
                reservationId, actualAmountMinor, newBalance);
    }

    public void release(String walletId, String reservationId, String referenceType,
                         String referenceId, String description) {
        Long reservedAmount = reservations.remove(reservationId);
        if (reservedAmount == null) {
            throw new IllegalArgumentException("Reservation not found: " + reservationId);
        }
        CreditWallet existing = wallets.get(walletId);
        recordTransaction(walletId, CreditTransaction.TYPE_RELEASE, reservedAmount,
                existing.balanceMinor(), referenceType, referenceId, description);
        log.info("CreditWalletService: released reservation {} amount={}",
                reservationId, reservedAmount);
    }

    private void recordTransaction(String walletId, String type, long amountMinor,
                                    long balanceAfterMinor, String referenceType,
                                    String referenceId, String description) {
        String txnId = Ids.newId("ctx");
        CreditTransaction txn = new CreditTransaction(
                txnId, walletId, type, amountMinor, balanceAfterMinor,
                referenceType, referenceId, description, Instant.now());
        transactions.put(txnId, txn);
        jdbcRepository.ifPresent(r -> r.saveTransaction(txn));
    }

    private void persistWallet(CreditWallet wallet) {
        wallets.put(wallet.walletId(), wallet);
        jdbcRepository.ifPresent(r -> r.saveWallet(wallet));
    }

    public List<CreditTransaction> getTransactions(String walletId) {
        return transactions.values().stream()
                .filter(t -> walletId.equals(t.walletId()))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    public List<CreditWallet> getWalletsByTenant(String tenantId) {
        return wallets.values().stream()
                .filter(w -> tenantId.equals(w.tenantId()))
                .toList();
    }
}
