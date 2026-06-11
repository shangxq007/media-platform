package com.example.platform.render.infrastructure.font;

import java.util.Set;

public interface FontCoverageChecker {

    String checkerName();

    default boolean enabled() { return true; }

    record CoverageResult(
            Set<Integer> supportedCodePoints,
            Set<Integer> missingCodePoints,
            Set<String> supportedScripts,
            Set<String> missingScripts
    ) {}

    CoverageResult checkCoverage(Set<Integer> requiredCodePoints, Set<String> requiredScripts);
}
