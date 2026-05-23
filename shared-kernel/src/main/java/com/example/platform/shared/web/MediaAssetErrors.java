package com.example.platform.shared.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured errors for timeline assets, catalog artifacts, and blob storage.
 */
public final class MediaAssetErrors {

    public static final String ASSET_NOT_FOUND = "ASSET-404-001";
    public static final String ASSET_STILL_REFERENCED = "ASSET-409-001";
    public static final String ASSET_TOMBSTONED = "ASSET-410-001";
    public static final String STORAGE_NOT_FOUND = "STORAGE-404-001";
    public static final String STORAGE_PURGE_FORBIDDEN = "STORAGE-403-001";
    public static final String ARTIFACT_NOT_FOUND = "ARTIFACT-404-001";
    public static final String ARTIFACT_STILL_REFERENCED = "ARTIFACT-409-001";
    public static final String ARTIFACT_TOMBSTONED = "ARTIFACT-410-001";

    private MediaAssetErrors() {}

    public static PlatformException assetNotFound(ErrorCodeRegistry registry, String assetId) {
        return with(registry, ASSET_NOT_FOUND, "assetId", assetId);
    }

    public static PlatformException assetTombstoned(ErrorCodeRegistry registry, String assetId) {
        return with(registry, ASSET_TOMBSTONED, "assetId", assetId);
    }

    public static PlatformException assetStillReferenced(ErrorCodeRegistry registry, String assetId) {
        return with(registry, ASSET_STILL_REFERENCED, "assetId", assetId);
    }

    public static PlatformException storageNotFound(ErrorCodeRegistry registry, String storageUri) {
        return with(registry, STORAGE_NOT_FOUND, "storageUri", storageUri);
    }

    public static PlatformException storagePurgeForbidden(ErrorCodeRegistry registry, String reason) {
        return with(registry, STORAGE_PURGE_FORBIDDEN, "reason", reason);
    }

    public static PlatformException artifactNotFound(ErrorCodeRegistry registry, String artifactId) {
        return with(registry, ARTIFACT_NOT_FOUND, "artifactId", artifactId);
    }

    public static PlatformException artifactTombstoned(ErrorCodeRegistry registry, String artifactId) {
        return with(registry, ARTIFACT_TOMBSTONED, "artifactId", artifactId);
    }

    public static PlatformException artifactStillReferenced(ErrorCodeRegistry registry, String artifactId) {
        return with(registry, ARTIFACT_STILL_REFERENCED, "artifactId", artifactId);
    }

    private static PlatformException with(ErrorCodeRegistry registry, String code,
                                          String detailKey, String detailValue) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(detailKey, detailValue);
        return new PlatformException(
                registry.getRequiredErrorCode(code),
                detailValue,
                details,
                "en");
    }
}
