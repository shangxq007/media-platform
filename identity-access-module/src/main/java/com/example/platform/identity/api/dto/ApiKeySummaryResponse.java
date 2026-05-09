package com.example.platform.identity.api.dto;

import com.example.platform.identity.app.ApiKeyRecord;

public record ApiKeySummaryResponse(
        String id,
        String fingerprint,
        String principal,
        java.time.Instant createdAt,
        boolean revoked) {

    public static ApiKeySummaryResponse from(ApiKeyRecord record) {
        return new ApiKeySummaryResponse(record.id(), record.fingerprint(),
                record.principal(), record.createdAt(), record.isRevoked());
    }
}
