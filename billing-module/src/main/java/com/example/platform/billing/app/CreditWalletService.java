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

    private final ConcurrentHashMap<String, CreditWallet> walletCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> reservations = new ConcurrentHashMap<>();
    private final Optional<CreditWalletJdbcRepository> jdbcRepository;

    public CreditWalletService() {
        this(Optional.empty());
    }

    @Autowired
    public CreditWalletService(Optional<CreditWalletJdbcRepository> jdbcRepository) {
        this.jdbcRepository = jdbcRepository != null ? jdbcRepository : Optional.empty();
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
        CreditWallet cached = walletCache.get(walletId);
        if (cached != null) return cached;

        if (jdbcRepository.isPresent()) {
            // Load from DB by iterating tenant wallets (no direct ID query in repo)
            // This is a fallback — prefer getWalletByTenant
            return null;
        }
        return null;
    }

    public CreditWallet getWalletByTenant(String tenantId, String userId) {
        // Check cache first
        CreditWallet cached = walletCache.values().stream()
                .filter(w -> tenantId.equals(w.tenantId()) && userId.equals(w.userId()))
                .findFirst()
                .orElse(null);
        if (cached != null) return cached;

        // Query DB
        return jdbcRepository.flatMap(r -> r.findWalletByTenantAndUser(tenantId, userId))
                .map(w -> {
                    walletCache.put(w.walletId(), w);
                    return w;
                })
                .orElse(null);
    }

    public CreditWallet credit(String walletId, long amountMinor, String referenceType,
                                String referenceId, String description) {
        CreditWallet existing = loadWallet(walletId);
        long newBalance = existing.balanceMinor() + amountMinor;
        CreditWallet updated = withBalance(existing, newBalance);
        persistWallet(updated);
        recordTransaction(walletId, CreditTransaction.TYPE_CREDIT, amountMinor,
                newBalance, referenceType, referenceId, description);
        log.info("CreditWalletService: credited wallet {} amount={} newBalance={}",
                walletId, amountMinor, newBalance);
        return updated;
    }

    public CreditWallet debit(String walletId, long amountMinor, String referenceType,
                               String referenceId, String description) {
        CreditWallet existing = loadWallet(walletId);
        if (existing.balanceMinor() < amountMinor) {
            throw new IllegalStateException("Insufficient balance in wallet: " + walletId);
        }
        long newBalance = existing.balanceMinor() - amountMinor;
        CreditWallet updated = withBalance(existing, newBalance);
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
            CreditWallet existing = loadWallet(walletId);
            long reservedTotal = reservations.values().stream().mapToLong(Long::longValue).sum();
            long available = existing.balanceMinor() - reservedTotal;
            if (available < amountMinor) {
                throw new IllegalStateException("Insufficient available balance for reservation");
            }
            reservations.put(reservationId, amountMinor);
        }
        recordTransaction(walletId, CreditTransaction.TYPE_RESERVE, amountMinor,
                loadWallet(walletId).balanceMinor(), referenceType, referenceId, description);
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
        CreditWallet existing = loadWallet(walletId);
        long newBalance = existing.balanceMinor() - actualAmountMinor;
        CreditWallet updated = withBalance(existing, newBalance);
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
        CreditWallet existing = loadWallet(walletId);
        recordTransaction(walletId, CreditTransaction.TYPE_RELEASE, reservedAmount,
                existing.balanceMinor(), referenceType, referenceId, description);
        log.info("CreditWalletService: released reservation {} amount={}",
                reservationId, reservedAmount);
    }

    public List<CreditTransaction> getTransactions(String walletId) {
        if (jdbcRepository.isPresent()) {
            return jdbcRepository.get().loadAllTransactions().stream()
                    .filter(t -> walletId.equals(t.walletId()))
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .toList();
        }
        return List.of();
    }

    public List<CreditWallet> getWalletsByTenant(String tenantId) {
        if (jdbcRepository.isPresent()) {
            return jdbcRepository.get().loadAllWallets().stream()
                    .filter(w -> tenantId.equals(w.tenantId()))
                    .toList();
        }
        return walletCache.values().stream()
                .filter(w -> tenantId.equals(w.tenantId()))
                .toList();
    }

    private CreditWallet loadWallet(String walletId) {
        CreditWallet cached = walletCache.get(walletId);
        if (cached != null) return cached;
        if (jdbcRepository.isPresent()) {
            return jdbcRepository.get().loadAllWallets().stream()
                    .filter(w -> walletId.equals(w.walletId()))
                    .findFirst()
                    .map(w -> { walletCache.put(w.walletId(), w); return w; })
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found: " + walletId));
        }
        throw new IllegalArgumentException("Wallet not found: " + walletId);
    }

    private CreditWallet withBalance(CreditWallet w, long newBalance) {
        return new CreditWallet(w.walletId(), w.tenantId(), w.workspaceId(),
                w.userId(), newBalance, w.currencyCode(), w.status(), w.createdAt(), Instant.now());
    }

    private void recordTransaction(String walletId, String type, long amountMinor,
                                    long balanceAfterMinor, String referenceType,
                                    String referenceId, String description) {
        String txnId = Ids.newId("ctx");
        CreditTransaction txn = new CreditTransaction(
                txnId, walletId, type, amountMinor, balanceAfterMinor,
                referenceType, referenceId, description, Instant.now());
        jdbcRepository.ifPresent(r -> r.saveTransaction(txn));
    }

    private void persistWallet(CreditWallet wallet) {
        walletCache.put(wallet.walletId(), wallet);
        jdbcRepository.ifPresent(r -> r.saveWallet(wallet));
    }
}
