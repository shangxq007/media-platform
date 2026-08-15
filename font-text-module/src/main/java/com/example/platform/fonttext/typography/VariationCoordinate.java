package com.example.platform.fonttext.typography;

import java.util.Objects;

/** ROADMAP_19 (C27): exact axis coordinate — tag + Rational. Zero double/float. */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class VariationCoordinate implements Comparable<VariationCoordinate> {

    private final VariationAxisTag axis;
    private final FontRational coordinate;

    public VariationCoordinate(VariationAxisTag axis, FontRational coordinate) {
        this.axis = Objects.requireNonNull(axis, "axis");
        this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
    }

    public VariationAxisTag axis() { return axis; }
    public FontRational coordinate() { return coordinate; }

    @Override
    public int compareTo(VariationCoordinate o) { return axis.compareTo(o.axis); }

    @Override
    public boolean equals(Object o) {
        return o instanceof VariationCoordinate v && axis.equals(v.axis) && coordinate.equals(v.coordinate);
    }

    @Override
    public int hashCode() { return Objects.hash(axis, coordinate); }

    @Override
    public String toString() { return axis + "=" + coordinate; }
}
