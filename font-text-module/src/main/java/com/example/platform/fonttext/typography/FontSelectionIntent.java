package com.example.platform.fonttext.typography;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP_19 (C21/C44): SOLE font face + variation selection authority.
 * weight/stretch/slant/opsz/axis overrides live ONLY here (never TextStyle).
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class FontSelectionIntent {

    public enum WeightIntent { NORMAL, MEDIUM, SEMIBOLD, BOLD, EXTRABOLD, BLACK, THIN, LIGHT }
    public enum StretchIntent { NORMAL, CONDENSED, EXPANDED }
    public enum SlantIntent { NORMAL, ITALIC, OBLIQUE }

    private final List<FontFamilyName> familyPreferences; // ordered preference
    private final WeightIntent weight;
    private final StretchIntent stretch;
    private final SlantIntent slant;
    private final OpticalSizingIntent opticalSizing;
    private final List<VariationCoordinate> explicitAxisOverrides; // sorted by tag, immutable

    public FontSelectionIntent(List<FontFamilyName> familyPreferences, WeightIntent weight,
                               StretchIntent stretch, SlantIntent slant,
                               OpticalSizingIntent opticalSizing,
                               List<VariationCoordinate> explicitAxisOverrides) {
        List<FontFamilyName> fam = new ArrayList<>(familyPreferences);
        if (fam.isEmpty()) {
            throw new IllegalArgumentException("at least one family preference required");
        }
        List<VariationCoordinate> axes = new ArrayList<>(explicitAxisOverrides);
        axes.sort(null);
        this.familyPreferences = Collections.unmodifiableList(fam);
        this.weight = Objects.requireNonNull(weight, "weight");
        this.stretch = Objects.requireNonNull(stretch, "stretch");
        this.slant = Objects.requireNonNull(slant, "slant");
        this.opticalSizing = Objects.requireNonNull(opticalSizing, "opticalSizing");
        this.explicitAxisOverrides = Collections.unmodifiableList(axes);
    }

    public List<FontFamilyName> familyPreferences() { return familyPreferences; }
    public WeightIntent weight() { return weight; }
    public StretchIntent stretch() { return stretch; }
    public SlantIntent slant() { return slant; }
    public OpticalSizingIntent opticalSizing() { return opticalSizing; }
    public List<VariationCoordinate> explicitAxisOverrides() { return explicitAxisOverrides; }

    public FontSelectionIntent withAxisOverride(VariationCoordinate coordinate) {
        List<VariationCoordinate> axes = new ArrayList<>(explicitAxisOverrides);
        axes.removeIf(a -> a.axis().equals(coordinate.axis()));
        axes.add(coordinate);
        return new FontSelectionIntent(familyPreferences, weight, stretch, slant, opticalSizing, axes);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FontSelectionIntent i)) return false;
        return familyPreferences.equals(i.familyPreferences) && weight == i.weight
                && stretch == i.stretch && slant == i.slant
                && opticalSizing.equals(i.opticalSizing) && explicitAxisOverrides.equals(i.explicitAxisOverrides);
    }

    @Override
    public int hashCode() {
        return Objects.hash(familyPreferences, weight, stretch, slant, opticalSizing, explicitAxisOverrides);
    }
}
