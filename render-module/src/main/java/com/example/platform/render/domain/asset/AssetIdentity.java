package com.example.platform.render.domain.asset;

/**
 * Platform asset identity fields.
 *
 * <p>Expresses asset identity within the platform — does not resolve storage paths
 * and is not equivalent to a storage URI.</p>
 *
 * @param assetId      globally unique asset identifier
 * @param assetVersion version string (e.g., "v7")
 * @param entityRef    OpenAssetIO entity reference (future resolution)
 * @param xmpUri       reference to XMP sidecar metadata envelope
 */
public record AssetIdentity(
        String assetId,
        String assetVersion,
        String entityRef,
        String xmpUri) {
}
