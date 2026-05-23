package com.example.platform.entitlement.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * JSON shape stored in {@code entitlement_override.override_payload} for {@code CUSTOM_POLICY}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomPolicyPayload(
        String tier,
        Integer maxResolutionWidth,
        Integer maxResolutionHeight,
        Long monthlyRenderMinutes,
        Boolean watermark,
        List<String> allowedProviders,
        Boolean gpuAllowed,
        Boolean remoteWorkerAllowed,
        Integer maxSubtitleTracks,
        Boolean customFontsAllowed,
        List<String> effectPacksAllowed,
        List<String> exportFormats,
        Integer maxConcurrentJobs,
        Map<String, String> extra) {}
