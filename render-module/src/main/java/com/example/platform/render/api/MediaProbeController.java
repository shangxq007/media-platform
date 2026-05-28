package com.example.platform.render.api;

import com.example.platform.render.app.mediaprobe.MediaAssetProbeService;
import com.example.platform.render.app.mediaprobe.MediaAssetProbeService.ProbeAndPersistResult;
import com.example.platform.shared.media.MediaProbePort.MediaProbeResult;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/render/media-probe")
public class MediaProbeController {

    private final MediaAssetProbeService probeService;

    public MediaProbeController(MediaAssetProbeService probeService) {
        this.probeService = probeService;
    }

    @PostMapping
    public Map<String, Object> probeAsset(
            @RequestBody ProbeRequest request) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }

        ProbeAndPersistResult result = probeService.probeAndPersist(
                effectiveTenant, request.projectId(), request.assetId(), request.assetUri());

        return toResponse(result.probeResult(), result.metadataId(), result.assetId());
    }

    @GetMapping("/{tenantId}/{assetId}")
    public Map<String, Object> getLatestProbe(
            @PathVariable String tenantId,
            @PathVariable String assetId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Tenant ID does not match authenticated tenant");
        }
        MediaProbeResult probe = probeService.getLatestProbe(tenantId, assetId);
        if (probe == null) {
            return Map.of("found", false);
        }
        return toResponse(probe, null, assetId);
    }

    private Map<String, Object> toResponse(MediaProbeResult probe, String metadataId, String assetId) {
        Map<String, Object> m = new HashMap<>();
        if (metadataId != null) m.put("metadataId", metadataId);
        m.put("assetId", assetId);
        m.put("found", true);
        m.put("valid", probe.valid());
        m.put("container", probe.container() != null ? probe.container() : "");
        m.put("durationMs", probe.durationMs());
        m.put("width", probe.width());
        m.put("height", probe.height());
        m.put("fps", probe.fps());
        m.put("videoCodec", probe.videoCodec() != null ? probe.videoCodec() : "");
        m.put("audioCodec", probe.audioCodec() != null ? probe.audioCodec() : "");
        m.put("hasAudioStream", probe.hasAudioStream());
        m.put("hasUsableAudio", probe.hasUsableAudio());
        m.put("rotation", probe.rotation());
        m.put("bitrate", probe.bitrate());
        m.put("isVfr", probe.isVfr());
        m.put("clientExportCompatible", probe.clientExportCompatible());
        m.put("normalizeRequired", probe.normalizeRequired());
        m.put("warnings", probe.warnings());
        return m;
    }

    public record ProbeRequest(String projectId, String assetId, String assetUri) {}
}
