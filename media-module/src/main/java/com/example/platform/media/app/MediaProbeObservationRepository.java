package com.example.platform.media.app;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.MediaProbeObservation;
import java.util.Optional;

/**
 * Raw probe observation persistence port (RAW_PROBE_RESULT_IS_NOT_CANONICAL_MEDIA_AUTHORITY_V1).
 */
public interface MediaProbeObservationRepository {

    void save(MediaAssetId mediaAssetId, String tenantId, String projectId, MediaProbeObservation observation);

    Optional<MediaProbeObservation> findLatest(MediaAssetId mediaAssetId);
}
