package com.example.platform.media.domain.probe;

import com.example.platform.media.domain.identity.MediaAssetId;

/**
 * INGEST_NORMALIZATION_BOUNDARY_V1 — single normalization boundary.
 *
 * <p>Raw provider observation → normalization → canonical source media
 * structural model. Re-probing never changes the {@link MediaAssetId};
 * normalization failure yields absent canonical fields plus the retained raw
 * observation, never sentinel numeric semantics.
 */
public interface MediaProbeNormalizer {

    NormalizedMediaProbe normalize(MediaProbeObservation observation, MediaAssetId mediaAssetId);
}
