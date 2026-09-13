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
public class MediaProbeService implements com.example.platform.media.api.MediaProbes {

    private final MediaAssetRepository assets;
    private final MediaAuthorization authorization;

    private final MediaProbePort probePort;
    private final MediaProbeNormalizer normalizer;
    private final MediaStreamRepository streamRepository;
    private final MediaProbeObservationRepository observationRepository;

    public MediaProbeService(MediaProbePort probePort,
                             MediaProbeNormalizer normalizer,
                             MediaStreamRepository streamRepository,
                             MediaProbeObservationRepository observationRepository, MediaAssetRepository assets, MediaAuthorization authorization) {
        this.authorization = authorization;
        this.assets = assets;
        this.probePort = probePort;
        this.normalizer = normalizer;
        this.streamRepository = streamRepository;
        this.observationRepository = observationRepository;
    }

    @Transactional
    public NormalizedMediaProbe probeAndPersist(
            MediaAssetId mediaAssetId, String tenantId, String projectId, String assetUri) {
        var asset = requireAsset(tenantId, mediaAssetId);
        if (!asset.projectId().equals(projectId)) throw new IllegalArgumentException("media project mismatch");
        authorization.require(tenantId, projectId, true);
        MediaProbeObservation observation = probePort.probe(assetUri);
        if (!observation.valid()) throw new IllegalStateException("Media probe rejected: " + observation.error());
        observationRepository.save(mediaAssetId, tenantId, projectId, observation);
        NormalizedMediaProbe normalized = normalizer.normalize(observation, mediaAssetId);
        streamRepository.deleteByMediaAssetId(mediaAssetId);
        if (normalized.streams() != null && !normalized.streams().isEmpty()) {
            streamRepository.saveAll(mediaAssetId, normalized.streams());
        }
        return normalized;
    }

    public Optional<NormalizedMediaProbe> latestNormalized(String tenantId, MediaAssetId mediaAssetId) {
        var asset = requireAsset(tenantId, mediaAssetId);
        authorization.require(tenantId, asset.projectId(), false);
        return observationRepository.findLatest(mediaAssetId)
                .map(o -> normalizer.normalize(o, mediaAssetId));
    }

    private com.example.platform.media.domain.media.MediaAsset requireAsset(String tenantId, MediaAssetId id) {
        com.example.platform.shared.web.TenantGuard.assertSameTenant(tenantId);
        return assets.findById(id).filter(a -> a.tenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("media asset not found in tenant"));
    }

    public Optional<MediaProbeObservation> latestObservation(String tenantId, MediaAssetId mediaAssetId) {
        var asset = requireAsset(tenantId, mediaAssetId);
        authorization.require(tenantId, asset.projectId(), false);
        return observationRepository.findLatest(mediaAssetId);
    }
}
