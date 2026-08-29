package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import java.util.Objects;

/** Canonical, tenant-scoped command accepted by the sole Subscription writer. */
public record SubscriptionCommand(
        SubscriptionCommandType commandType,
        PrincipalRef principal,
        String contractId,
        String planKey,
        String productCode,
        int periodDays,
        SubscriptionContractRole contractRole,
        long expectedVersion,
        String idempotencyKey,
        String actor,
        String reason,
        String traceId,
        Instant effectiveAt) {

    public SubscriptionCommand {
        Objects.requireNonNull(commandType, "commandType must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        contractId = required(contractId, "contractId");
        if (commandType != SubscriptionCommandType.CANCEL) planKey = required(planKey, "planKey");
        if (commandType != SubscriptionCommandType.CANCEL && periodDays <= 0) {
            throw new IllegalArgumentException("periodDays must be positive");
        }
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        contractRole = contractRole == null ? SubscriptionContractRole.BASE : contractRole;
        productCode = productCode == null || productCode.isBlank() ? planKey : productCode;
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        actor = required(actor, "actor");
        reason = required(reason, "reason");
        traceId = required(traceId, "traceId");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
    }

    public String fingerprint() {
        return String.join("|", commandType.name(), principal.principalType().name(),
                principal.principalId(), contractId, value(planKey), value(productCode),
                Integer.toString(periodDays), contractRole.name(), Long.toString(expectedVersion),
                effectiveAt.toString());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }

    private static String value(String value) { return value == null ? "" : value; }
}
