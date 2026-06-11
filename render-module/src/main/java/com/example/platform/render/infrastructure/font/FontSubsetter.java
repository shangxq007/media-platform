package com.example.platform.render.infrastructure.font;

import java.nio.file.Path;
import java.util.Set;

public interface FontSubsetter {

    String subsetterName();

    default boolean enabled() { return true; }

    FontSubsetResult subset(Path fontFile, Set<Integer> codePoints, SubsetOptions options);

    record SubsetOptions(
            String format,
            boolean hinting,
            boolean kerning,
            boolean ligatures,
            Set<String> layoutFeatures,
            boolean desubroutinize,
            boolean nameIds,
            boolean glyphNames,
            boolean notdefOutline
    ) {
        public static SubsetOptions defaultWoff2() {
            return new SubsetOptions("woff2", true, true, true,
                    Set.of("kern", "liga", "calt"), true, true, true, true);
        }
    }
}
