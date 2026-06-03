package com.example.platform.shared.asset;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Port interface for generating signed download URLs for project assets.
 *
 * <p>Implementations are provided by the storage module and wrap the underlying
 * cloud storage presign capability (e.g., S3 presigned URLs).
 *
 * <p>Security rules:
 * <ul>
 *   <li>Signed URLs must have a finite expiration time (TTL).</li>
 *   <li>Storage references (bucket, key) are never exposed in the URL itself.</li>
 *   <li>URLs are revocable by the issuing tenant.</li>
 * </ul>
 */
public interface AssetDownloadUrlPort {

    /**
     * Generate a signed download URL for an asset.
     *
     * @param assetId     asset identifier
     * @param storageUri  storage URI in format {@code provider://bucket/key}
     * @param ttl         time-to-live for the signed URL
     * @return signed URL string, or empty if the asset is not available
     */
    Optional<String> generateSignedUrl(String assetId, String storageUri, Duration ttl);

    /**
     * Generate signed download URLs for multiple assets.
     *
     * @param assets  map of assetId → storageUri
     * @param ttl     time-to-live for all signed URLs
     * @return map of assetId → signed URL (only for successfully generated URLs)
     */
    Map<String, String> generateSignedUrls(Map<String, String> assets, Duration ttl);

    /**
     * Check if signed URL generation is available (e.g., storage backend supports it).
     */
    boolean isAvailable();
}
