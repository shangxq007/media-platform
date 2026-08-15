package com.example.platform.fonttext.execution;

import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.text.RangeDirectionOverride;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.typography.OpenTypeFeatureIntent;
import com.example.platform.fonttext.typography.VariationCoordinate;
import java.util.List;
import java.util.Objects;

/** ROADMAP_19 (C32/C49): provider-neutral shaping request. Zero provider classes. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class TextShapingRequest {

    private final TextContent content;
    private final int startScalar;
    private final int endScalar;
    private final ResolvedFontInstance font;
    private final ScriptTag script;
    private final String language; // null = unspecified
    private final RangeDirectionOverride direction;
    private final OpenTypeFeatureIntent features;

    public TextShapingRequest(TextContent content, int startScalar, int endScalar,
                              ResolvedFontInstance font, ScriptTag script, String language,
                              RangeDirectionOverride direction, OpenTypeFeatureIntent features) {
        this.content = Objects.requireNonNull(content, "content");
        if (startScalar < 0 || endScalar < startScalar || endScalar > content.scalarCount()) {
            throw new IllegalArgumentException("invalid shaping range");
        }
        this.startScalar = startScalar;
        this.endScalar = endScalar;
        this.font = Objects.requireNonNull(font, "font");
        this.script = Objects.requireNonNull(script, "script");
        this.language = language;
        this.direction = Objects.requireNonNull(direction, "direction");
        this.features = Objects.requireNonNull(features, "features");
    }

    public TextContent content() { return content; }
    public int startScalar() { return startScalar; }
    public int endScalar() { return endScalar; }
    public ResolvedFontInstance font() { return font; }
    public ScriptTag script() { return script; }
    public String language() { return language; }
    public RangeDirectionOverride direction() { return direction; }
    public OpenTypeFeatureIntent features() { return features; }
    public List<VariationCoordinate> variationCoordinates() { return font.variationCoordinates(); }
}
