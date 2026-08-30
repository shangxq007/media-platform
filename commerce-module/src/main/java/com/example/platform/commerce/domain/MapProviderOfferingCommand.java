package com.example.platform.commerce.domain;

import java.time.Instant;

public record MapProviderOfferingCommand(CatalogActor actor, String mappingId, String providerCode,
        String externalProductReference, String externalPriceReference, String productId, String offeringId,
        long offeringVersion, long expectedVersion, String idempotencyKey, String source, String reason,
        String traceId, Instant occurredAt) {
    public MapProviderOfferingCommand {
        if (actor == null || mappingId == null || mappingId.isBlank() || providerCode == null || providerCode.isBlank()
                || externalProductReference == null || externalProductReference.isBlank() || productId == null || productId.isBlank()
                || offeringId == null || offeringId.isBlank() || offeringVersion < 1 || expectedVersion != 0
                || idempotencyKey == null || idempotencyKey.isBlank() || source == null || source.isBlank()
                || reason == null || reason.isBlank() || traceId == null || traceId.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("complete provider mapping command is required");
        }
    }
}
