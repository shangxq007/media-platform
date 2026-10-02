package com.example.platform.media.app;

import com.example.platform.media.domain.probe.MediaProbeObservation;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Optional;

/**
 * Raw probe observation persistence port (RAW_PROBE_RESULT_IS_NOT_CANONICAL_MEDIA_AUTHORITY_V1).
 */
public interface MediaProbeObservationRepository {

    void save(ArtifactId artifactId, String tenantId, String projectId, MediaProbeObservation observation);

    Optional<MediaProbeObservation> findLatest(ArtifactId artifactId);
}
