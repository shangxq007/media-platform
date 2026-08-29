package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public record CreditWalletCommand(
        CreditWalletCommandType commandType, PrincipalRef principal, String walletId,
        String reservationId, Money amount, String createCurrency, long expectedVersion,
        String referenceType, String referenceId, String description,
        String idempotencyKey, String actor, String reason, String traceId, Instant occurredAt) {

    public CreditWalletCommand {
        if (commandType == null || principal == null) throw new IllegalArgumentException("command and principal are required");
        walletId = required(walletId, "walletId");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        actor = required(actor, "actor");
        reason = required(reason, "reason");
        traceId = required(traceId, "traceId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        if (commandType == CreditWalletCommandType.CREATE) {
            createCurrency = required(createCurrency, "createCurrency");
        } else {
            referenceType = required(referenceType, "referenceType");
            referenceId = required(referenceId, "referenceId");
            description = description == null ? "" : description;
        }
        if (commandType != CreditWalletCommandType.CREATE
                && commandType != CreditWalletCommandType.RELEASE
                && (amount == null || amount.amountMinor() <= 0)) {
            throw new IllegalArgumentException("positive command amount is required");
        }
        if ((commandType == CreditWalletCommandType.RESERVE
                || commandType == CreditWalletCommandType.FINALIZE
                || commandType == CreditWalletCommandType.RELEASE)
                && (reservationId == null || reservationId.isBlank())) {
            throw new IllegalArgumentException("reservationId is required");
        }
    }

    public static CreditWalletCommand create(PrincipalRef principal, String walletId, String currency,
            String key, String actor, String reason, String trace, Instant at) {
        return new CreditWalletCommand(CreditWalletCommandType.CREATE, principal, walletId, null,
                null, currency, 0, null, null, null, key, actor, reason, trace, at);
    }
    public static CreditWalletCommand credit(PrincipalRef principal, String walletId, Money amount,
            long version, String refType, String refId, String description, String key,
            String actor, String reason, String trace, Instant at) {
        return money(CreditWalletCommandType.CREDIT, principal, walletId, null, amount, version,
                refType, refId, description, key, actor, reason, trace, at);
    }
    public static CreditWalletCommand debit(PrincipalRef principal, String walletId, Money amount,
            long version, String refType, String refId, String description, String key,
            String actor, String reason, String trace, Instant at) {
        return money(CreditWalletCommandType.DEBIT, principal, walletId, null, amount, version,
                refType, refId, description, key, actor, reason, trace, at);
    }
    public static CreditWalletCommand reserve(PrincipalRef principal, String walletId,
            String reservationId, Money amount, long version, String refType, String refId,
            String description, String key, String actor, String reason, String trace, Instant at) {
        return money(CreditWalletCommandType.RESERVE, principal, walletId, reservationId, amount,
                version, refType, refId, description, key, actor, reason, trace, at);
    }
    public static CreditWalletCommand finalizeReservation(PrincipalRef principal, String walletId,
            String reservationId, Money amount, long version, String refType, String refId,
            String description, String key, String actor, String reason, String trace, Instant at) {
        return money(CreditWalletCommandType.FINALIZE, principal, walletId, reservationId, amount,
                version, refType, refId, description, key, actor, reason, trace, at);
    }
    public static CreditWalletCommand releaseReservation(PrincipalRef principal, String walletId,
            String reservationId, long version, String refType, String refId, String description,
            String key, String actor, String reason, String trace, Instant at) {
        return new CreditWalletCommand(CreditWalletCommandType.RELEASE, principal, walletId,
                reservationId, null, null, version, refType, refId, description,
                key, actor, reason, trace, at);
    }

    private static CreditWalletCommand money(CreditWalletCommandType type, PrincipalRef principal,
            String walletId, String reservationId, Money amount, long version,
            String refType, String refId, String description, String key,
            String actor, String reason, String trace, Instant at) {
        return new CreditWalletCommand(type, principal, walletId, reservationId, amount, null,
                version, refType, refId, description, key, actor, reason, trace, at);
    }

    public String fingerprint() {
        String payload = String.join("\u001f", commandType.name(), principal.tenantId(),
                principal.principalType().name(), principal.principalId(), walletId,
                value(reservationId), money(amount), value(createCurrency),
                Long.toString(expectedVersion), value(referenceType), value(referenceId),
                value(description), actor, reason, traceId);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String money(Money value) {
        return value == null ? "" : value.amountMinor() + ":" + value.currency();
    }
    private static String value(String value) { return value == null ? "" : value; }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
