package com.example.platform.render.infrastructure.font;

import java.nio.file.Path;
import java.util.Set;

/**
 * FontTools-based coverage checker skeleton.
 *
 * Disabled by default. Enable via: render.font.tools.enabled=true
 */
public class FontToolsCoverageChecker implements FontCoverageChecker {

    private boolean enabled = false;

    public FontToolsCoverageChecker enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public String checkerName() {
        return "FontToolsCoverageChecker";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public CoverageResult checkCoverage(Set<Integer> requiredCodePoints, Set<String> requiredScripts) {
        if (!enabled) {
            return new CoverageResult(Set.of(), requiredCodePoints, Set.of(), requiredScripts);
        }
        return new CoverageResult(Set.of(), requiredCodePoints, Set.of(), requiredScripts);
    }
}
