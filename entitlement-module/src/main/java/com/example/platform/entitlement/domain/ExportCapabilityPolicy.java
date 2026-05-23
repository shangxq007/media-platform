package com.example.platform.entitlement.domain;

import java.util.Set;

/**
 * Policy defining what export capabilities are available for a tier.
 */
public record ExportCapabilityPolicy(
        String policyId,
        String tier,
        Set<String> allowedFormats,
        Set<String> allowedPresets,
        int maxResolutionWidth,
        int maxResolutionHeight,
        boolean watermarkRequired,
        boolean gpuExportAllowed,
        boolean remoteExportAllowed,
        int maxConcurrentExports) {

    public static ExportCapabilityPolicy forTier(String tier) {
        return switch (tier.toUpperCase()) {
            case "FREE" -> new ExportCapabilityPolicy("ecp-free", "FREE",
                    Set.of("mp4", "webm"),
                    Set.of("free_720p_watermarked", "client_720p_watermarked", "default_720p", "preview_720p", "mobile_480p"),
                    1280, 720, true, false, false, 1);
            case "PRO" -> new ExportCapabilityPolicy("ecp-pro", "PRO",
                    Set.of("mp4", "webm", "mov"),
                    Set.of("default_1080p", "default_720p", "pro_1080p", "social_1080p", "social_720p",
                            "mobile_480p", "preview_720p", "hq_1080p", "h265", "vp9"),
                    1920, 1080, false, false, false, 3);
            case "TEAM" -> new ExportCapabilityPolicy("ecp-team", "TEAM",
                    Set.of("mp4", "webm", "mov", "dash", "hls"),
                    Set.of("default_1080p", "default_720p", "pro_1080p", "team_4k", "social_1080p",
                            "social_720p", "mobile_480p", "preview_720p", "hq_1080p", "h265", "vp9",
                            "ofx_1080p", "ofx_720p", "gpu_h264", "gpu_h265"),
                    3840, 2160, false, true, true, 10);
            case "ENTERPRISE" -> new ExportCapabilityPolicy("ecp-enterprise", "ENTERPRISE",
                    Set.of("mp4", "webm", "mov", "dash", "hls", "cmaf"),
                    Set.of("default_1080p", "default_720p", "pro_1080p", "team_4k", "social_1080p",
                            "social_720p", "mobile_480p", "preview_720p", "hq_1080p", "h265", "vp9",
                            "ofx_1080p", "ofx_720p", "enterprise_4k_ofx", "gpu_h264", "gpu_h265",
                            "experimental_all_providers"),
                    3840, 2160, false, true, true, 50);
            case "EXPERIMENTAL" -> new ExportCapabilityPolicy("ecp-experimental", "EXPERIMENTAL",
                    Set.of("mp4", "webm", "mov", "dash", "hls", "cmaf"),
                    Set.of("default_1080p", "default_720p", "pro_1080p", "team_4k", "social_1080p",
                            "social_720p", "mobile_480p", "preview_720p", "hq_1080p", "h265", "vp9",
                            "ofx_1080p", "ofx_720p", "enterprise_4k_ofx", "gpu_h264", "gpu_h265",
                            "experimental_all_providers"),
                    3840, 2160, false, true, true, 100);
            default -> forTier("FREE");
        };
    }

    public boolean isPresetAllowed(String preset) {
        return allowedPresets == null || allowedPresets.contains(preset);
    }

    public boolean isFormatAllowed(String format) {
        return allowedFormats == null || allowedFormats.contains(format);
    }
}
