package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class HarfBuzzShapingValidator implements FontCoverageChecker {
    private static final Logger log = LoggerFactory.getLogger(HarfBuzzShapingValidator.class);

    @Override
    public String checkerName() {
        return "HarfBuzzShapingValidator";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public CoverageResult checkCoverage(Set<Integer> requiredCodePoints, Set<String> requiredScripts) {
        log.warn("HarfBuzzShapingValidator is disabled. Returning empty coverage result.");
        return new CoverageResult(Set.of(), requiredCodePoints, Set.of(), requiredScripts);
    }
}
