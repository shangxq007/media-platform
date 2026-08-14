package com.example.platform.media.app;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.MediaProbeObservation;
import com.example.platform.media.domain.probe.MediaProbeNormalizer;
import com.example.platform.media.domain.probe.MediaProbePort;
import com.example.platform.media.domain.probe.NormalizedMediaProbe;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Media-domain probe orchestration (INGEST_NORMALIZATION_BOUNDARY_V1).
 *
 * <p>raw observation → normalization → canonical structural model. Re-probe
 * NEVER changes the MediaAssetId; observation rows are appended (latest wins),
 * structural rows are replaced by the normalized result.
 */
@Service
public class MediaProbeService {

    private final MediaProbePort probePort;
    private final MediaProbeNormalizer normalizer;
    private final MediaStreamRepository streamRepository;
    private final MediaProbeObservationRepository observationRepository;

    public MediaProbeService(MediaProbePort probePort,
                             MediaProbeNormalizer normalizer,
                             MediaStreamRepository streamRepository,
                             MediaProbeObservationRepository observationRepository) {
        this.probePort = probePort;
        this.normalizer = normalizer;
        this.streamRepository = streamRepository;
        this.observationRepository = observationRepository;
    }

    @Transactional
    public NormalizedMediaProbe probeAndPersist(
            MediaAssetId mediaAssetId, String tenantId, String projectId, String assetUri) {
        MediaProbeObservation observation = probePort.probe(assetUri);
        observationRepository.save(mediaAssetId, tenantId, projectId, observation);
        NormalizedMediaProbe normalized = normalizer.normalize(observation, mediaAssetId);
        streamRepository.deleteByMediaAssetId(mediaAssetId);
        if (normalized.streams() != null && !normalized.streams().isEmpty()) {
            streamRepository.saveAll(mediaAssetId, normalized.streams());
        }
        return normalized;
    }

    public Optional<NormalizedMediaProbe> latestNormalized(MediaAssetId mediaAssetId) {
        return observationRepository.findLatest(mediaAssetId)
                .map(o -> normalizer.normalize(o, mediaAssetId));
    }

    public Optional<MediaProbeObservation> latestObservation(MediaAssetId mediaAssetId) {
        return observationRepository.findLatest(mediaAssetId);
    }
}
