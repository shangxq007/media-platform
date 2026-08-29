package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

/** Immutable append-only Billing ledger fact. */
public record BillingLedgerEntry(
        String entryId, PrincipalRef principal, String entryType, Money amount,
        String referenceType, String referenceId, String description,
        String idempotencyKey, String payloadFingerprint, Instant createdAt) {

    public static final String TYPE_CHARGE = "CHARGE";
    public static final String TYPE_REFUND = "REFUND";
    public static final String TYPE_ADJUSTMENT = "ADJUSTMENT";
    public static final String TYPE_CREDIT = "CREDIT";
    public static final String TYPE_DEBIT = "DEBIT";
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    public BillingLedgerEntry {
        if (entryId == null || entryId.isBlank()) throw new IllegalArgumentException("entryId is required");
        if (principal == null) throw new IllegalArgumentException("principal is required");
        if (entryType == null || entryType.isBlank()) throw new IllegalArgumentException("entryType is required");
        if (amount == null) throw new IllegalArgumentException("amount is required");
        if (!Set.of(TYPE_CHARGE, TYPE_REFUND, TYPE_ADJUSTMENT, TYPE_CREDIT,
                TYPE_DEBIT, TYPE_DISCOUNT).contains(entryType)) {
            throw new IllegalArgumentException("unknown Billing ledger entry type");
        }
        if (!TYPE_ADJUSTMENT.equals(entryType) && amount.amountMinor() < 0) {
            throw new IllegalArgumentException("only explicit adjustments may carry a negative amount");
        }
        if (referenceType == null || referenceType.isBlank() || referenceId == null || referenceId.isBlank()) {
            throw new IllegalArgumentException("ledger reference provenance is required");
        }
        if (description == null) description = "";
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
        String computed = fingerprint(principal, entryType, amount, referenceType,
                referenceId, description);
        if (payloadFingerprint == null) payloadFingerprint = computed;
        if (!payloadFingerprint.equals(computed)) throw new IllegalArgumentException("ledger fingerprint mismatch");
    }

    public static BillingLedgerEntry charge(
            String entryId, PrincipalRef principal, Money amount,
            String referenceType, String referenceId, String description,
            String idempotencyKey, Instant createdAt) {
        return new BillingLedgerEntry(entryId, principal, TYPE_CHARGE, amount,
                referenceType, referenceId, description, idempotencyKey, null, createdAt);
    }

    public String tenantId() { return principal.tenantId(); }
    public String workspaceId() { return principal.workspaceId(); }
    public String userId() { return principal.principalId(); }
    public long amountMinor() { return amount.amountMinor(); }
    public String currencyCode() { return amount.currency(); }

    private static String fingerprint(PrincipalRef principal, String entryType, Money amount,
                                      String referenceType, String referenceId,
                                      String description) {
        String payload = String.join("\u001f", principal.tenantId(),
                principal.principalType().name(), principal.principalId(),
                principal.workspaceId() == null ? "" : principal.workspaceId(), entryType,
                Long.toString(amount.amountMinor()), amount.currency(), referenceType,
                referenceId, description);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
