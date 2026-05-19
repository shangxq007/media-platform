package com.example.platform.billing.domain;

import java.time.OffsetDateTime;

/**
 * Cost ledger entry linking to render cost records.
 */
public record CostLedgerEntry(
        String entryId,
        String tenantId,
        String renderJobId,
        String providerKey,
        double estimatedCost,
        double actualCost,
        String currency,
        String costType,
        OffsetDateTime recordedAt,
        String status) {

    public static final String TYPE_RENDER = "RENDER";
    public static final String TYPE_STORAGE = "STORAGE";
    public static final String TYPE_EGRESS = "EGRESS";
    public static final String TYPE_API = "API_CALL";
}
