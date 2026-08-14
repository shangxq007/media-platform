package com.example.platform.render.app.mediaprobe;

import com.example.platform.media.app.MediaProbeService;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.NormalizedMediaProbe;
import org.springframework.stereotype.Service;

/**
 * Render-side probe facade. The media-domain {@link MediaProbeService} owns
 * the normalization boundary; this facade adapts the ingest call shape
 * (tenant/project/assetId strings) to the canonical MediaAssetId world.
 *
 * <p>Re-probe NEVER changes the MediaAssetId (frozen identity authority).
 * No raw/approximate probe value is persisted as canonical authority here.
 */
@Service
public class MediaAssetProbeService {

    private final MediaProbeService mediaProbeService;

    public MediaAssetProbeService(MediaProbeService mediaProbeService) {
        this.mediaProbeService = mediaProbeService;
    }

    public NormalizedMediaProbe probeAndPersist(
            String tenantId, String projectId, String assetId, String assetUri) {
        MediaAssetId mediaAssetId = MediaAssetId.of(assetId);
        return mediaProbeService.probeAndPersist(mediaAssetId, tenantId, projectId, assetUri);
    }

    public NormalizedMediaProbe getLatestProbe(String tenantId, String assetId) {
        return mediaProbeService.latestNormalized(MediaAssetId.of(assetId)).orElse(null);
    }
}
