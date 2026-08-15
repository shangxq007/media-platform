package com.example.platform.fonttext.resolution;

import com.example.platform.fonttext.text.LanguageTag;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.typography.FontFamilyName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** ROADMAP_19 (C22/C38): explicit ordered canonical fallback intent. Order is semantic. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)

public final class FontFallbackPolicy {

    private final List<FontFamilyName> defaultChain;
    private final List<ScriptOverride> scriptOverrides;
    private final List<LanguageOverride> languageOverrides;
    private final List<FontFamilyName> emojiChain;

    public record ScriptOverride(ScriptTag script, List<FontFamilyName> chain) {}
    public record LanguageOverride(LanguageTag language, List<FontFamilyName> chain) {}

    public FontFallbackPolicy(List<FontFamilyName> defaultChain, List<ScriptOverride> scriptOverrides,
                              List<LanguageOverride> languageOverrides, List<FontFamilyName> emojiChain) {
        this.defaultChain = Collections.unmodifiableList(new ArrayList<>(defaultChain));
        this.scriptOverrides = Collections.unmodifiableList(new ArrayList<>(scriptOverrides));
        this.languageOverrides = Collections.unmodifiableList(new ArrayList<>(languageOverrides));
        this.emojiChain = Collections.unmodifiableList(new ArrayList<>(emojiChain));
        if (defaultChain.isEmpty()) {
            throw new IllegalArgumentException("default fallback chain must not be empty");
        }
    }

    public List<FontFamilyName> defaultChain() { return defaultChain; }
    public List<ScriptOverride> scriptOverrides() { return scriptOverrides; }
    public List<LanguageOverride> languageOverrides() { return languageOverrides; }
    public List<FontFamilyName> emojiChain() { return emojiChain; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FontFallbackPolicy p)) return false;
        return defaultChain.equals(p.defaultChain) && scriptOverrides.equals(p.scriptOverrides)
                && languageOverrides.equals(p.languageOverrides) && emojiChain.equals(p.emojiChain);
    }

    @Override
    public int hashCode() { return Objects.hash(defaultChain, scriptOverrides, languageOverrides, emojiChain); }
}
