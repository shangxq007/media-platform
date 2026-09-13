package com.example.platform.media.api;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.probe.NormalizedMediaProbe;
import java.util.Optional;
public interface MediaProbes {
    NormalizedMediaProbe probeAndPersist(MediaAssetId assetId, String tenantId, String projectId, String assetUri);
    Optional<NormalizedMediaProbe> latestNormalized(String tenantId, MediaAssetId assetId);
}
