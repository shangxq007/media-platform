package com.example.platform.render.api;

import com.example.platform.media.domain.probe.NormalizedMediaProbe;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.media.domain.stream.StreamKind;
import com.example.platform.render.app.mediaprobe.MediaAssetProbeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Media probe API — returns the NORMALIZED structural model projection
 * (RAW_PROBE_RESULT_IS_NOT_CANONICAL_MEDIA_AUTHORITY_V1). Raw provider
 * observations are never exposed as canonical media truth.
 */
@RestController
@RequestMapping("/api/render/media-probe")
public class MediaProbeController {

    private final MediaAssetProbeService probeService;

    public MediaProbeController(MediaAssetProbeService probeService) {
        this.probeService = probeService;
    }

    @PostMapping
    public Map<String, Object> probeAsset(@RequestBody ProbeRequest request) {
        String effectiveTenant = com.example.platform.shared.web.TenantContext.get();
        if (effectiveTenant == null || effectiveTenant.isBlank()) {
            throw new IllegalArgumentException("Tenant context is required");
        }
        NormalizedMediaProbe normalized = probeService.probeAndPersist(
                effectiveTenant, request.projectId(), request.assetId(), request.assetUri());
        return toResponse(normalized);
    }

    @GetMapping("/{tenantId}/{assetId}")
    public Map<String, Object> getLatestProbe(
            @PathVariable String tenantId,
            @PathVariable String assetId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Tenant ID does not match authenticated tenant");
        }
        NormalizedMediaProbe normalized = probeService.getLatestProbe(tenantId, assetId);
        if (normalized == null) {
            return Map.of("found", false);
        }
        Map<String, Object> response = toResponse(normalized);
        response.put("found", true);
        return response;
    }

    private static Map<String, Object> toResponse(NormalizedMediaProbe normalized) {
        Map<String, Object> m = new HashMap<>();
        m.put("mediaAssetId", normalized.mediaAssetId().value());
        if (normalized.duration() != null) {
            m.put("duration", Map.of(
                    "numerator", normalized.duration().ticks(),
                    "denominator", normalized.duration().timeScale(),
                    "unit", "ticks/timeScale"));
        } else {
            m.put("duration", null);
        }
        m.put("container", normalized.container());
        m.put("isVfr", normalized.isVfr());
        m.put("clientExportCompatible", normalized.clientExportCompatible());
        m.put("normalizeRequired", normalized.normalizeRequired());
        m.put("streams", toStreamList(normalized.streams()));
        return m;
    }

    private static List<Map<String, Object>> toStreamList(List<MediaStream> streams) {
        return streams.stream().map(s -> {
            Map<String, Object> sm = new HashMap<>();
            sm.put("streamId", s.id().value());
            sm.put("streamIndex", s.streamIndex());
            sm.put("kind", s.kind().name());
            sm.put("codec", s.codec());
            sm.put("timeBase", Map.of("numerator", s.timeBase().numerator(), "denominator", s.timeBase().denominator()));
            if (s.nominalFrameRate() != null) {
                sm.put("nominalFrameRate", Map.of(
                        "numerator", s.nominalFrameRate().numerator(),
                        "denominator", s.nominalFrameRate().denominator()));
            }
            sm.put("isVfr", s.isVfr());
            if (s.kind() == StreamKind.VIDEO && s.video() != null) {
                sm.put("width", s.video().width());
                sm.put("height", s.video().height());
                sm.put("pixelFormat", s.video().pixelFormat());
            }
            if (s.kind() == StreamKind.AUDIO && s.audio() != null) {
                sm.put("sampleRate", s.audio().sampleRate());
                sm.put("channels", s.audio().channels());
                sm.put("channelLayout", s.audio().channelLayout());
            }
            if (s.color() != null) {
                sm.put("color", Map.of(
                        "primaries", s.color().primaries(),
                        "transfer", s.color().transfer(),
                        "matrix", s.color().matrix(),
                        "range", s.color().range()));
            }
            return sm;
        }).toList();
    }

    public record ProbeRequest(String projectId, String assetId, String assetUri) {}
}
