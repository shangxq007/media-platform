package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.OffsetDateTime;

/** Technical/provider ExecutionCost record; never a CommercialPrice. */
public record CostLedgerEntry(
        String entryId, String tenantId, String renderJobId, String providerKey,
        Money estimatedCost, Money actualCost, String costType,
        OffsetDateTime recordedAt, String status) {
    public static final String TYPE_RENDER = "RENDER";
    public static final String TYPE_STORAGE = "STORAGE";
    public static final String TYPE_EGRESS = "EGRESS";
    public static final String TYPE_API = "API_CALL";
    public String currency() { return actualCost.currency(); }
}
