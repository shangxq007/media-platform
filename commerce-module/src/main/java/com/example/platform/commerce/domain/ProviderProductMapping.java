package com.example.platform.commerce.domain;

import java.time.Instant;

public record ProviderProductMapping(String mappingId, String providerCode, String externalProductReference,
        String externalPriceReference, String productId, String offeringId, long offeringVersion,
        long version, Instant createdAt, Instant updatedAt) {}
