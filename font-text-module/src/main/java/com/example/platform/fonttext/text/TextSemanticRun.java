package com.example.platform.fonttext.text;

import java.util.Objects;

/**
 * ROADMAP_19 (C16/C45): range-level language/script/direction semantics.
 * UNSPECIFIED (null fields) is distinct from any derived value; direction
 * override is optional (NONE).
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class TextSemanticRun {

    private final TextRange range;
    private final LanguageTag language;   // null = UNSPECIFIED
    private final ScriptTag script;       // null = UNSPECIFIED
    private final RangeDirectionOverride directionOverride;

    public TextSemanticRun(TextRange range, LanguageTag language, ScriptTag script,
                           RangeDirectionOverride directionOverride) {
        this.range = Objects.requireNonNull(range, "range");
        this.language = language;
        this.script = script;
        this.directionOverride = Objects.requireNonNull(directionOverride, "directionOverride");
    }

    public TextRange range() { return range; }
    public LanguageTag language() { return language; }
    public ScriptTag script() { return script; }
    public RangeDirectionOverride directionOverride() { return directionOverride; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextSemanticRun r)) return false;
        return range.equals(r.range) && Objects.equals(language, r.language)
                && Objects.equals(script, r.script) && directionOverride == r.directionOverride;
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, language, script, directionOverride);
    }
}
