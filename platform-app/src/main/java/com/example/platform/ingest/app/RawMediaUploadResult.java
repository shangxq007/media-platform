package com.example.platform.ingest.app;

import java.time.Instant;

/** Ingest-local result for a raw media upload. */
public record RawMediaUploadResult(
        String productId,
        Instant createdAt
) {
}
