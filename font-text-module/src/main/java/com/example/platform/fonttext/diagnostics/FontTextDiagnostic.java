package com.example.platform.fonttext.diagnostics;

import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.TextRange;
import java.util.List;
import java.util.Objects;

/** ROADMAP_19 (C25/C43): typed missing-glyph / unsupported-text diagnostics. No silent tofu. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class FontTextDiagnostic {

    public enum Code {
        MISSING_GLYPH, UNSUPPORTED_SCRIPT, SHAPING_CONFORMANCE_FAILURE,
        FONT_UNAVAILABLE, FONT_VALIDATION_FAILED,
        FONT_RIGHTS_NOT_EVALUATED_OR_DENIED_HOOK,
        COLOR_FONT_CAPABILITY_UNAVAILABLE, VARIABLE_FONT_CAPABILITY_UNAVAILABLE,
        INVALID_VARIATION_COORDINATE, AUTO_OPTICAL_SIZING_UNRESOLVED
    }

    private final Code code;
    private final TextRange range;
    private final ScriptTag script;         // null = unspecified
    private final String language;          // null = unspecified
    private final List<FontContentDigest> attemptedFonts;
    private final String reason;

    public FontTextDiagnostic(Code code, TextRange range, ScriptTag script, String language,
                              List<FontContentDigest> attemptedFonts, String reason) {
        this.code = Objects.requireNonNull(code, "code");
        this.range = Objects.requireNonNull(range, "range");
        this.script = script;
        this.language = language;
        this.attemptedFonts = List.copyOf(attemptedFonts);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Code code() { return code; }
    public TextRange range() { return range; }
    public ScriptTag script() { return script; }
    public String language() { return language; }
    public List<FontContentDigest> attemptedFonts() { return attemptedFonts; }
    public String reason() { return reason; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FontTextDiagnostic d)) return false;
        return code == d.code && range.equals(d.range) && Objects.equals(script, d.script)
                && Objects.equals(language, d.language) && attemptedFonts.equals(d.attemptedFonts)
                && reason.equals(d.reason);
    }

    @Override
    public int hashCode() { return Objects.hash(code, range, script, language, attemptedFonts, reason); }
}
