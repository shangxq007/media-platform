package com.example.platform.render.infrastructure.billing.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System for managing promotional, referral, and community credits.
 * 
 * <p>Supports:
 * <ul>
 *   <li>Promotional credits</li>
 *   <li>Referral credits</li>
 *   <li>Community reward credits</li>
 *   <li>Expiration timestamps</li>
 *   <li>Usage deduction rules</li>
 * </ul>
 */
@Service
public class CreditSystem {

    private static final Logger log = LoggerFactory.getLogger(CreditSystem.class);

    private final Map<String, CreditAccount> accounts = new ConcurrentHashMap<>();
    private final Map<String, CreditGrant> grants = new ConcurrentHashMap<>();

    /**
     * Get or create a credit account for a tenant.
     */
    public CreditAccount getAccount(String tenantId) {
        return accounts.computeIfAbsent(tenantId, id -> new CreditAccount(
                id, id, 0.0, 0.0, List.of(), Instant.now(), Instant.now()
        ));
    }

    /**
     * Grant credits to an account.
     */
    public CreditGrant grantCredits(String tenantId, double amount, CreditType type,
                                      String reason, Instant expiresAt) {
        String grantId = "grant-" + System.currentTimeMillis();
        CreditGrant grant = new CreditGrant(
                grantId, tenantId, amount, amount, type, reason,
                Instant.now(), expiresAt, GrantStatus.ACTIVE
        );
        grants.put(grantId, grant);

        // Update account balance
        CreditAccount account = getAccount(tenantId);
        CreditAccount updated = account.addGrant(grant);
        accounts.put(tenantId, updated);

        log.info("Granted {} credits to tenant {}: {} ({})", 
                amount, tenantId, reason, type);
        return grant;
    }

    /**
     * Deduct credits from an account.
     */
    public CreditDeductionResult deductCredits(String tenantId, double amount, String reason) {
        CreditAccount account = getAccount(tenantId);

        if (account.availableBalance() < amount) {
            return new CreditDeductionResult(
                    false, 0, account.availableBalance(),
                    "Insufficient credits: need " + amount + ", have " + account.availableBalance()
            );
        }

        // Deduct from grants (FIFO by expiration)
        double[] remaining = {amount};
        List<CreditGrant> updatedGrants = account.grants().stream()
                .sorted((a, b) -> {
                    // Sort by expiration (soonest first), nulls last
                    if (a.expiresAt() == null) return 1;
                    if (b.expiresAt() == null) return -1;
                    return a.expiresAt().compareTo(b.expiresAt());
                })
                .map(grant -> {
                    if (remaining[0] <= 0 || grant.status() != GrantStatus.ACTIVE) {
                        return grant;
                    }
                    double deduct = Math.min(remaining[0], grant.remainingAmount());
                    remaining[0] -= deduct;
                    return grant.deduct(deduct);
                })
                .toList();

        CreditAccount updated = account.withGrants(updatedGrants);
        accounts.put(tenantId, updated);

        log.info("Deducted {} credits from tenant {}: {}", amount, tenantId, reason);
        return new CreditDeductionResult(true, amount, updated.availableBalance(), null);
    }

    /**
     * Check if an account has sufficient credits.
     */
    public boolean hasSufficientCredits(String tenantId, double amount) {
        CreditAccount account = getAccount(tenantId);
        return account.availableBalance() >= amount;
    }

    /**
     * Get available credit balance.
     */
    public double getAvailableBalance(String tenantId) {
        CreditAccount account = getAccount(tenantId);
        return account.availableBalance();
    }

    /**
     * Expire grants that have passed their expiration date.
     */
    public int expireGrants() {
        int[] expired = {0};
        Instant now = Instant.now();

        for (CreditAccount account : accounts.values()) {
            List<CreditGrant> updatedGrants = account.grants().stream()
                    .map(grant -> {
                        if (grant.status() == GrantStatus.ACTIVE
                                && grant.expiresAt() != null
                                && grant.expiresAt().isBefore(now)) {
                            expired[0]++;
                            return grant.expire();
                        }
                        return grant;
                    })
                    .toList();

            CreditAccount updated = account.withGrants(updatedGrants);
            accounts.put(account.tenantId(), updated);
        }

        if (expired[0] > 0) {
            log.info("Expired {} credit grants", expired[0]);
        }
        return expired[0];
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    /**
     * Credit account for a tenant.
     */
    public record CreditAccount(
            String accountId,
            String tenantId,
            double totalGranted,
            double totalDeducted,
            List<CreditGrant> grants,
            Instant createdAt,
            Instant updatedAt
    ) {
        public double availableBalance() {
            return grants.stream()
                    .filter(g -> g.status() == GrantStatus.ACTIVE)
                    .mapToDouble(CreditGrant::remainingAmount)
                    .sum();
        }

        public CreditAccount addGrant(CreditGrant grant) {
            List<CreditGrant> newGrants = new java.util.ArrayList<>(grants);
            newGrants.add(grant);
            return new CreditAccount(
                    accountId, tenantId,
                    totalGranted + grant.originalAmount(),
                    totalDeducted,
                    List.copyOf(newGrants),
                    createdAt,
                    Instant.now()
            );
        }

        public CreditAccount withGrants(List<CreditGrant> newGrants) {
            double totalDeducted = newGrants.stream()
                    .mapToDouble(g -> g.originalAmount() - g.remainingAmount())
                    .sum();
            return new CreditAccount(
                    accountId, tenantId,
                    totalGranted, totalDeducted,
                    newGrants, createdAt, Instant.now()
            );
        }
    }

    /**
     * Credit grant record.
     */
    public record CreditGrant(
            String grantId,
            String tenantId,
            double originalAmount,
            double remainingAmount,
            CreditType type,
            String reason,
            Instant grantedAt,
            Instant expiresAt,
            GrantStatus status
    ) {
        public CreditGrant deduct(double amount) {
            double newRemaining = Math.max(0, remainingAmount - amount);
            GrantStatus newStatus = newRemaining <= 0 ? GrantStatus.DEPLETED : status;
            return new CreditGrant(
                    grantId, tenantId, originalAmount, newRemaining,
                    type, reason, grantedAt, expiresAt, newStatus
            );
        }

        public CreditGrant expire() {
            return new CreditGrant(
                    grantId, tenantId, originalAmount, 0,
                    type, reason, grantedAt, expiresAt, GrantStatus.EXPIRED
            );
        }

        public boolean isActive() {
            return status == GrantStatus.ACTIVE
                    && remainingAmount > 0
                    && (expiresAt == null || expiresAt.isAfter(Instant.now()));
        }
    }

    /**
     * Credit deduction result.
     */
    public record CreditDeductionResult(
            boolean success,
            double amountDeducted,
            double remainingBalance,
            String error
    ) {}

    /**
     * Credit types.
     */
    public enum CreditType {
        PROMOTIONAL,
        REFERRAL,
        COMMUNITY_REWARD,
        COMPENSATION,
        TRIAL
    }

    /**
     * Grant status.
     */
    public enum GrantStatus {
        ACTIVE,
        DEPLETED,
        EXPIRED,
        REVOKED
    }
}
