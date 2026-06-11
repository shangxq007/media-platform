package com.example.platform.render.infrastructure.font;

import java.util.List;

public record FontSecurityResult(
        String scanner,
        String scanStatus,
        String scannedAt,
        boolean productionSafe,
        List<String> warnings,
        String sha256,
        String mimeType,
        boolean magicBytesValid,
        boolean pathSafe,
        boolean extensionWhitelisted
) {
    public static FontSecurityResult rejected(String scanner, List<String> warnings) {
        return new FontSecurityResult(scanner, "REJECTED", java.time.Instant.now().toString(),
                false, warnings, null, null, false, false, false);
    }

    public static FontSecurityResult passed(String scanner, String sha256, String mimeType) {
        return new FontSecurityResult(scanner, "PASSED", java.time.Instant.now().toString(),
                true, List.of(), sha256, mimeType, true, true, true);
    }
}
