package com.example.platform.entitlement.domain;

import java.util.Set;

/**
 * Policy defining which providers a tier can access.
 */
public record ProviderAccessPolicy(
        String policyId,
        String tier,
        Set<String> allowedProviders,
        boolean gpuAllowed,
        boolean remoteWorkerAllowed,
        Set<String> allowedGpuPresets) {

    public static ProviderAccessPolicy forTier(String tier) {
        return switch (tier.toUpperCase()) {
            case "FREE" -> new ProviderAccessPolicy("pap-free", "FREE",
                    Set.of("javacv", "mlt", "gstreamer"), false, false, Set.of());
            case "PRO" -> new ProviderAccessPolicy("pap-pro", "PRO",
                    Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac", "natron"), false, false, Set.of());
            case "TEAM" -> new ProviderAccessPolicy("pap-team", "TEAM",
                    Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac", "natron", "remote-javacv"),
                    true, true, Set.of("gpu_h264", "gpu_h265"));
            case "ENTERPRISE" -> new ProviderAccessPolicy("pap-enterprise", "ENTERPRISE",
                    Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac", "natron", "remote-javacv"),
                    true, true, Set.of("gpu_h264", "gpu_h265"));
            case "EXPERIMENTAL" -> new ProviderAccessPolicy("pap-experimental", "EXPERIMENTAL",
                    Set.of("javacv", "ofx", "mlt", "gstreamer", "gpac", "remote-javacv"),
                    true, true, Set.of("gpu_h264", "gpu_h265"));
            default -> forTier("FREE");
        };
    }

    public boolean isProviderAllowed(String providerKey) {
        return allowedProviders != null && allowedProviders.contains(providerKey);
    }
}
