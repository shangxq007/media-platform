package com.example.platform.entitlement.domain;

import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;
import java.util.Objects;

/** Complete canonical command envelope for generic and workspace grants. */
public record EntitlementGrantCommand(
        EntitlementCommandType commandType,
        PrincipalRef principal,
        String grantId,
        String bundleCode,
        String quotaProfileCode,
        String sourceType,
        String sourceRef,
        String idempotencyKey,
        String actor,
        String reason,
        String traceId,
        Instant effectiveAt,
        Instant expiresAt,
        long expectedVersion) {

    public EntitlementGrantCommand {
        Objects.requireNonNull(commandType, "commandType must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        grantId = required(grantId, "grantId");
        if (commandType == EntitlementCommandType.GRANT
                || commandType == EntitlementCommandType.WORKSPACE_GRANT) {
            bundleCode = required(bundleCode, "bundleCode");
        }
        if (commandType.name().startsWith("WORKSPACE_") && principal.workspaceId() == null) {
            throw new IllegalArgumentException("workspaceId is required for workspace grant commands");
        }
        sourceType = required(sourceType, "sourceType");
        sourceRef = required(sourceRef, "sourceRef");
        idempotencyKey = required(idempotencyKey, "idempotencyKey");
        actor = required(actor, "actor");
        reason = required(reason, "reason");
        traceId = required(traceId, "traceId");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
    }

    public String fingerprint() {
        return String.join("|", commandType.name(), principal.principalType().name(),
                principal.principalId(), value(principal.workspaceId()), value(principal.organizationId()),
                grantId, value(bundleCode), value(quotaProfileCode), sourceType, sourceRef,
                effectiveAt.toString(), value(expiresAt), Long.toString(expectedVersion));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be null/blank");
        return value;
    }

    private static String value(Object value) { return value == null ? "" : value.toString(); }
}
