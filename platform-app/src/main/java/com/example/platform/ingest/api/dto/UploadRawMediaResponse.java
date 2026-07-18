package com.example.platform.ingest.api.dto;

import java.time.Instant;

/**
 * Response DTO for raw media upload.
 *
 * <p>Matches the frontend contract defined in
 * {@code frontend/src/contracts/app/upload.ts}:
 * <pre>{@code
 * {
 *   productId: string,
 *   status: 'SUCCESS' | 'FAILED',
 *   createdAt: string (ISO 8601)
 * }
 * }</pre>
 *
 * <p>Safety rules: no storage references, no signed URLs, no internal paths.
 */
public record UploadRawMediaResponse(
        String productId,
        String status,
        String createdAt
) {
    public static UploadRawMediaResponse success(String productId, Instant createdAt) {
        return new UploadRawMediaResponse(
                productId,
                "SUCCESS",
                createdAt != null ? createdAt.toString() : Instant.now().toString()
        );
    }

    public static UploadRawMediaResponse failed() {
        return new UploadRawMediaResponse(null, "FAILED", Instant.now().toString());
    }
}
