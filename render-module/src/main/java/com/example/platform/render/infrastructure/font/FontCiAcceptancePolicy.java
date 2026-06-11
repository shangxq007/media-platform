package com.example.platform.render.infrastructure.font;

import java.util.List;

public record FontCiAcceptancePolicy(
        String name,
        FontQaProfile profile,
        boolean scanUserUploads,
        boolean failOnNoopInProduction,
        List<String> requiredChecks,
        List<String> optionalChecks,
        String reportArtifactPath
) {
    public static FontCiAcceptancePolicy defaultCi() {
        return new FontCiAcceptancePolicy(
                "default-ci", FontQaProfile.LIGHT, false, true,
                List.of("font-fixture-parseable", "manifest-generation",
                        "metadata-extraction", "basic-coverage", "noop-production-warning"),
                List.of(), null);
    }

    public static FontCiAcceptancePolicy nightly() {
        return new FontCiAcceptancePolicy(
                "nightly", FontQaProfile.FULL, true, true,
                List.of("font-fixture-parseable", "manifest-generation",
                        "metadata-extraction", "full-coverage", "noop-production-warning",
                        "ots-sanitizer", "subset-roundtrip", "woff2-loadability"),
                List.of("font-bakery-profile", "harfBuzz-shaping"),
                "build/reports/font-qa-nightly.json");
    }

    public static FontCiAcceptancePolicy releaseGate() {
        return new FontCiAcceptancePolicy(
                "release-gate", FontQaProfile.RELEASE_GATE, true, true,
                List.of("font-fixture-parseable", "manifest-generation",
                        "metadata-extraction", "full-coverage", "noop-production-warning",
                        "ots-sanitizer", "subset-roundtrip", "woff2-loadability",
                        "font-bakery-profile", "harfBuzz-shaping"),
                List.of(), "build/reports/font-qa-release.json");
    }
}
