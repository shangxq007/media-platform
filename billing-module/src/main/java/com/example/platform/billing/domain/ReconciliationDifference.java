package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.OffsetDateTime;

public record ReconciliationDifference(
        String differenceId, String runId, String tenantId, String recordType,
        String internalRecordId, String externalRecordId,
        Money internalAmount, Money externalAmount, Money differenceAmount,
        String status, String resolution, OffsetDateTime detectedAt, OffsetDateTime resolvedAt) {
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";
    public String currency() { return differenceAmount.currency(); }

    public ReconciliationDifference accept(String value) { return resolved(STATUS_ACCEPTED, value); }
    public ReconciliationDifference reject(String value) { return resolved(STATUS_REJECTED, value); }
    public ReconciliationDifference markForReview(String value) { return resolved(STATUS_NEEDS_REVIEW, value); }

    private ReconciliationDifference resolved(String nextStatus, String value) {
        return new ReconciliationDifference(differenceId, runId, tenantId, recordType,
                internalRecordId, externalRecordId, internalAmount, externalAmount,
                differenceAmount, nextStatus, value, detectedAt, OffsetDateTime.now());
    }
}
