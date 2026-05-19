package com.example.platform.billing.domain;

import java.time.OffsetDateTime;

/**
 * Difference found during reconciliation.
 */
public record ReconciliationDifference(
        String differenceId,
        String runId,
        String tenantId,
        String recordType,
        String internalRecordId,
        String externalRecordId,
        double internalAmount,
        double externalAmount,
        double differenceAmount,
        String currency,
        String status,
        String resolution,
        OffsetDateTime detectedAt,
        OffsetDateTime resolvedAt) {

    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";

    public ReconciliationDifference accept(String resolution) {
        return new ReconciliationDifference(differenceId, runId, tenantId, recordType,
                internalRecordId, externalRecordId, internalAmount, externalAmount,
                differenceAmount, currency, STATUS_ACCEPTED, resolution,
                detectedAt, OffsetDateTime.now());
    }

    public ReconciliationDifference reject(String resolution) {
        return new ReconciliationDifference(differenceId, runId, tenantId, recordType,
                internalRecordId, externalRecordId, internalAmount, externalAmount,
                differenceAmount, currency, STATUS_REJECTED, resolution,
                detectedAt, OffsetDateTime.now());
    }

    public ReconciliationDifference markForReview(String resolution) {
        return new ReconciliationDifference(differenceId, runId, tenantId, recordType,
                internalRecordId, externalRecordId, internalAmount, externalAmount,
                differenceAmount, currency, STATUS_NEEDS_REVIEW, resolution,
                detectedAt, OffsetDateTime.now());
    }
}
