package com.example.platform.render.infrastructure.gpac;

import java.util.Map;

/**
 * DRM packaging hints (CENC / AES-128 / SAMPLE-AES) — keys supplied externally via KMS.
 */
public record PackagingDrmProfile(
        boolean enabled,
        String scheme, // cenc, aes-128, sample-aes, widevine, playready, fairplay
        String keyId,
        String licenseServerUrl,
        Map<String, String> extra) {

    public static PackagingDrmProfile cencPlaceholder(String keyId, String licenseUrl) {
        return new PackagingDrmProfile(true, "cenc", keyId, licenseUrl, Map.of());
    }

    public Map<String, String> toExtraParams() {
        return Map.of(
                "drm.enabled", String.valueOf(enabled),
                "drm.scheme", scheme != null ? scheme : "cenc",
                "drm.keyId", keyId != null ? keyId : "",
                "drm.licenseUrl", licenseServerUrl != null ? licenseServerUrl : "");
    }
}
