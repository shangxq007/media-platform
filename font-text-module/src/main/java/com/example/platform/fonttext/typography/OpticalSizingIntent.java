package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C28): optical sizing intent. EXPLICIT carries exact coordinate; AUTO must resolve before apply. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class OpticalSizingIntent {

    public enum Kind { DISABLED, AUTO, EXPLICIT }

    private final Kind kind;
    private final FontRational explicitCoordinate; // null unless EXPLICIT

    private OpticalSizingIntent(Kind kind, FontRational explicitCoordinate) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.explicitCoordinate = explicitCoordinate;
        if (kind == Kind.EXPLICIT && explicitCoordinate == null) {
            throw new IllegalArgumentException("EXPLICIT optical sizing requires exact coordinate");
        }
        if (kind != Kind.EXPLICIT && explicitCoordinate != null) {
            throw new IllegalArgumentException("coordinate only valid for EXPLICIT");
        }
    }

    public static OpticalSizingIntent disabled() { return new OpticalSizingIntent(Kind.DISABLED, null); }
    public static OpticalSizingIntent auto() { return new OpticalSizingIntent(Kind.AUTO, null); }
    public static OpticalSizingIntent explicit(FontRational coordinate) { return new OpticalSizingIntent(Kind.EXPLICIT, coordinate); }

    public Kind kind() { return kind; }
    public FontRational explicitCoordinate() { return explicitCoordinate; }

    @Override
    public boolean equals(Object o) {
        return o instanceof OpticalSizingIntent i && kind == i.kind && Objects.equals(explicitCoordinate, i.explicitCoordinate);
    }

    @Override
    public int hashCode() { return Objects.hash(kind, explicitCoordinate); }
}
