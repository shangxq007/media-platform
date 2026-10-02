package com.example.platform.media.domain.probe;

import com.example.platform.shared.identity.ArtifactId;

/**
 * INGEST_NORMALIZATION_BOUNDARY_V1 — single normalization boundary.
 *
 * <p>Raw provider observation → normalization → canonical source media
 * structural model. Re-probing never changes the {@link ArtifactId};
 * normalization failure yields absent canonical fields plus the retained raw
 * observation, never sentinel numeric semantics.
 */
public interface MediaProbeNormalizer {

    NormalizedMediaProbe normalize(MediaProbeObservation observation, ArtifactId artifactId);
}
