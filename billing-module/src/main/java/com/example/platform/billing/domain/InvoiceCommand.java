package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public record InvoiceCommand(
        InvoiceCommandType commandType, PrincipalRef principal, String invoiceId,
        String contractId, String lineItemId, String ratedUsageId,
        long quantityBaseUnits, Money unitPrice, Money lineAmount,
        long expectedVersion, String idempotencyKey, String actor,
        String reason, String traceId, Instant occurredAt) {

    public InvoiceCommand {
        if (commandType == null) throw new IllegalArgumentException("commandType is required");
        if (principal == null) throw new IllegalArgumentException("principal is required");
        invoiceId = required(invoiceId, "invoiceId");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        actor = required(actor, "actor");
        reason = required(reason, "reason");
        traceId = required(traceId, "traceId");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        if (quantityBaseUnits < 0) throw new IllegalArgumentException("quantity must not be negative");
        if (commandType == InvoiceCommandType.CREATE && unitPrice == null) {
            throw new IllegalArgumentException("create currency money is required");
        }
        if (commandType == InvoiceCommandType.ADD_RATED_USAGE
                && (lineItemId == null || ratedUsageId == null || unitPrice == null || lineAmount == null)) {
            throw new IllegalArgumentException("rated usage line data is required");
        }
    }

    public static InvoiceCommand create(PrincipalRef principal, String invoiceId, String contractId,
                                        Money currencyMoney, long expectedVersion,
                                        String idempotencyKey, String actor, String reason,
                                        String traceId, Instant occurredAt) {
        return new InvoiceCommand(InvoiceCommandType.CREATE, principal, invoiceId, contractId,
                null, null, 0, currencyMoney, null, expectedVersion, idempotencyKey,
                actor, reason, traceId, occurredAt);
    }

    public static InvoiceCommand addRatedUsage(
            PrincipalRef principal, String invoiceId, String lineItemId, String ratedUsageId,
            long quantityBaseUnits, Money unitPrice, Money lineAmount, long expectedVersion,
            String idempotencyKey, String actor, String reason, String traceId, Instant occurredAt) {
        return new InvoiceCommand(InvoiceCommandType.ADD_RATED_USAGE, principal, invoiceId, null,
                lineItemId, ratedUsageId, quantityBaseUnits, unitPrice, lineAmount, expectedVersion,
                idempotencyKey, actor, reason, traceId, occurredAt);
    }

    public static InvoiceCommand finalizeInvoice(
            PrincipalRef principal, String invoiceId, long expectedVersion,
            String idempotencyKey, String actor, String reason, String traceId, Instant occurredAt) {
        return simple(InvoiceCommandType.FINALIZE, principal, invoiceId, expectedVersion,
                idempotencyKey, actor, reason, traceId, occurredAt);
    }

    public static InvoiceCommand markPaid(
            PrincipalRef principal, String invoiceId, long expectedVersion,
            String idempotencyKey, String actor, String reason, String traceId, Instant occurredAt) {
        return simple(InvoiceCommandType.MARK_PAID, principal, invoiceId, expectedVersion,
                idempotencyKey, actor, reason, traceId, occurredAt);
    }

    public static InvoiceCommand voidInvoice(
            PrincipalRef principal, String invoiceId, long expectedVersion,
            String idempotencyKey, String actor, String reason, String traceId, Instant occurredAt) {
        return simple(InvoiceCommandType.VOID, principal, invoiceId, expectedVersion,
                idempotencyKey, actor, reason, traceId, occurredAt);
    }

    private static InvoiceCommand simple(InvoiceCommandType type, PrincipalRef principal,
                                         String invoiceId, long expectedVersion,
                                         String idempotencyKey, String actor, String reason,
                                         String traceId, Instant occurredAt) {
        return new InvoiceCommand(type, principal, invoiceId, null, null, null, 0,
                null, null, expectedVersion, idempotencyKey, actor, reason, traceId, occurredAt);
    }

    public String fingerprint() {
        String payload = String.join("\u001f", commandType.name(), principal.tenantId(),
                principal.principalType().name(), principal.principalId(), invoiceId,
                value(contractId), value(lineItemId), value(ratedUsageId),
                Long.toString(quantityBaseUnits), money(unitPrice), money(lineAmount),
                Long.toString(expectedVersion), actor, reason, traceId, occurredAt.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String money(Money money) {
        return money == null ? "" : money.amountMinor() + ":" + money.currency();
    }
    private static String value(String value) { return value == null ? "" : value; }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
