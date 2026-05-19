package com.example.platform.entitlement.domain;

import java.util.Map;
import java.util.Set;

/**
 * Entitlement policy defining what a user/tenant can access at a given tier.
 */
public record EntitlementPolicy(
        String policyId,
        String tier,
        int maxResolutionWidth,
        int maxResolutionHeight,
        long monthlyRenderMinutes,
        boolean watermark,
        Set<String> allowedProviders,
        boolean gpuAllowed,
        boolean remoteWorkerAllowed,
        int maxSubtitleTracks,
        boolean customFontsAllowed,
        Set<String> effectPacksAllowed,
        Set<String> exportFormats,
        int maxConcurrentJobs,
        Map<String, String> extra) {

    public static EntitlementPolicy freeTier() {
        return new EntitlementPolicy("policy-free", "FREE", 1280, 720, 60,
                true, Set.of("javacv", "mlt", "gstreamer"), false, false,
                2, false, Set.of("basic"), Set.of("mp4", "webm"), 1, Map.of());
    }

    public static EntitlementPolicy proTier() {
        return new EntitlementPolicy("policy-pro", "PRO", 1920, 1080, 300,
                false, Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac"), false, false,
                5, true, Set.of("basic", "pro"), Set.of("mp4", "webm", "mov"), 3, Map.of());
    }

    public static EntitlementPolicy teamTier() {
        return new EntitlementPolicy("policy-team", "TEAM", 3840, 2160, 1200,
                false, Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac"), true, true,
                10, true, Set.of("basic", "pro", "team"), Set.of("mp4", "webm", "mov", "dash", "hls"), 10, Map.of());
    }

    public static EntitlementPolicy enterpriseTier() {
        return new EntitlementPolicy("policy-enterprise", "ENTERPRISE", 3840, 2160, 6000,
                false, Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac", "remote-javacv"), true, true,
                20, true, Set.of("basic", "pro", "team", "enterprise"), Set.of("mp4", "webm", "mov", "dash", "hls", "cmaf"), 50, Map.of());
    }

    public static EntitlementPolicy experimentalTier() {
        return new EntitlementPolicy("policy-experimental", "EXPERIMENTAL", 3840, 2160, 999999,
                false, Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac", "remote-javacv"), true, true,
                50, true, Set.of("basic", "pro", "team", "enterprise", "experimental"), Set.of("mp4", "webm", "mov", "dash", "hls", "cmaf"), 100, Map.of());
    }

    public static EntitlementPolicy forTier(String tier) {
        return switch (tier.toUpperCase()) {
            case "FREE" -> freeTier();
            case "PRO" -> proTier();
            case "TEAM" -> teamTier();
            case "ENTERPRISE" -> enterpriseTier();
            case "EXPERIMENTAL" -> experimentalTier();
            default -> freeTier();
        };
    }

    public boolean isProviderAllowed(String providerKey) {
        return allowedProviders == null || allowedProviders.contains(providerKey);
    }

    public boolean isFormatAllowed(String format) {
        return exportFormats == null || exportFormats.contains(format);
    }

    public boolean isEffectPackAllowed(String packId) {
        return effectPacksAllowed == null || effectPacksAllowed.contains(packId);
    }
}
