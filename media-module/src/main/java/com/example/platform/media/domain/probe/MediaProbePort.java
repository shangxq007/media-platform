package com.example.platform.media.domain.probe;

/**
 * Media probe capability port (INGEST_NORMALIZATION_BOUNDARY_V1).
 *
 * <p>Owned by the media domain (rehomed from render-module). The port
 * returns a RAW {@link MediaProbeObservation} only — provider-specific
 * observation data. Domain/application consumers never receive raw probe
 * DTOs as canonical media truth; the {@link MediaProbeNormalizer} is the
 * single normalization boundary.
 */
public interface MediaProbePort {

    MediaProbeObservation probe(String assetUri);
}
