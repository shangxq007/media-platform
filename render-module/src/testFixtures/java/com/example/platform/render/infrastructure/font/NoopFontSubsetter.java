package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

public class NoopFontSubsetter implements FontSubsetter {
    private static final Logger log = LoggerFactory.getLogger(NoopFontSubsetter.class);

    @Override
    public String subsetterName() {
        return "NoopFontSubsetter";
    }

    @Override
    public FontSubsetResult subset(Path fontFile, Set<Integer> codePoints, SubsetOptions options) {
        log.warn("NoopFontSubsetter used for file: {}. This is NOT production-safe.", fontFile);
        return new FontSubsetResult(
                "noop", false, null, null, "ttf", 0, 0, 0,
                java.util.List.of(), java.util.Map.of()
        );
    }
}
