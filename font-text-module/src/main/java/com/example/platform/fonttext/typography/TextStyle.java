package com.example.platform.fonttext.typography;

import java.util.Objects;

/**
 * ROADMAP_19 (C18/C31/C43/C44): final frozen V1 TextStyle. NO lineHeight
 * (ParagraphStyle owns it), NO fill (deferred), NO duplicated selection fields.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class TextStyle {

    private final FontSelectionIntent fontSelection;
    private final FontSize fontSize;
    private final FontRational tracking;
    private final OpenTypeFeatureIntent features;

    @com.fasterxml.jackson.annotation.JsonCreator
public TextStyle(@com.fasterxml.jackson.annotation.JsonProperty("fontSelection") FontSelectionIntent fontSelection, @com.fasterxml.jackson.annotation.JsonProperty("fontSize") FontSize fontSize, @com.fasterxml.jackson.annotation.JsonProperty("tracking") FontRational tracking, @com.fasterxml.jackson.annotation.JsonProperty("features") OpenTypeFeatureIntent features) {
        this.fontSelection = Objects.requireNonNull(fontSelection, "fontSelection");
        this.fontSize = Objects.requireNonNull(fontSize, "fontSize");
        this.tracking = Objects.requireNonNull(tracking, "tracking");
        this.features = Objects.requireNonNull(features, "features");
    }

    public FontSelectionIntent fontSelection() { return fontSelection; }
    public FontSize fontSize() { return fontSize; }
    public FontRational tracking() { return tracking; }
    public OpenTypeFeatureIntent features() { return features; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextStyle s)) return false;
        return fontSelection.equals(s.fontSelection) && fontSize.equals(s.fontSize)
                && tracking.equals(s.tracking) && features.equals(s.features);
    }

    @Override
    public int hashCode() { return Objects.hash(fontSelection, fontSize, tracking, features); }
}
