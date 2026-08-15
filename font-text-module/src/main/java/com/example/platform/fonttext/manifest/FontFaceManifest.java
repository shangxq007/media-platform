package com.example.platform.fonttext.manifest;

import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import com.example.platform.fonttext.text.ScriptTag;
import com.example.platform.fonttext.typography.VariationAxisTag;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ROADMAP_19 (C5/C10): immutable deterministic facts parsed from exact
 * validated font content. Three-way classification documented per field:
 * canonical parsed fact / derived diagnostic / execution capability observation.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
public final class FontFaceManifest {

    public record AxisCapability(VariationAxisTag axis, long min, long def, long max, int flags) {}
    public record NamedInstance(String name, List<com.example.platform.fonttext.typography.VariationCoordinate> coordinates) {}
    public record ColorCapability(String technology) {} // COLR, COLRv1, SVG, CBDT/CBLC, sbix

    private final FontContentDigest contentDigest;   // canonical parsed fact
    private final FaceIndex faceIndex;               // canonical parsed fact
    private final FontFormat format;                 // canonical parsed fact
    private final String familyName;                 // derived diagnostic (never identity)
    private final String subfamilyName;              // derived diagnostic
    private final int weightClass;                   // canonical parsed fact
    private final int widthClass;                    // canonical parsed fact
    private final String slantStyle;                 // canonical parsed fact
    private final int glyphCount;                    // canonical parsed fact
    private final Set<Integer> unicodeCoverage;      // canonical parsed fact (scalar set)
    private final Set<ScriptTag> scriptConformance;  // derived diagnostic (NOT_TESTED default)
    private final boolean hasGsub;                   // canonical parsed fact
    private final boolean hasGpos;                   // canonical parsed fact
    private final boolean hasGdef;                   // canonical parsed fact
    private final List<AxisCapability> variationAxes; // canonical parsed fact
    private final List<NamedInstance> namedInstances; // canonical parsed fact (authoring metadata)
    private final boolean hasStat;                   // canonical parsed fact
    private final boolean hasAvar;                   // canonical parsed fact
    private final List<ColorCapability> colorCapabilities; // canonical parsed fact
    private final String validationState;            // execution capability observation
    private final String conformanceState;           // execution capability observation

    public FontFaceManifest(FontContentDigest contentDigest, FaceIndex faceIndex, FontFormat format,
                            String familyName, String subfamilyName, int weightClass, int widthClass,
                            String slantStyle, int glyphCount, Set<Integer> unicodeCoverage,
                            Set<ScriptTag> scriptConformance, boolean hasGsub, boolean hasGpos,
                            boolean hasGdef, List<AxisCapability> variationAxes,
                            List<NamedInstance> namedInstances, boolean hasStat, boolean hasAvar,
                            List<ColorCapability> colorCapabilities, String validationState,
                            String conformanceState) {
        this.contentDigest = Objects.requireNonNull(contentDigest, "contentDigest");
        this.faceIndex = Objects.requireNonNull(faceIndex, "faceIndex");
        this.format = Objects.requireNonNull(format, "format");
        this.familyName = familyName;
        this.subfamilyName = subfamilyName;
        this.weightClass = weightClass;
        this.widthClass = widthClass;
        this.slantStyle = slantStyle;
        this.glyphCount = glyphCount;
        this.unicodeCoverage = Set.copyOf(unicodeCoverage);
        this.scriptConformance = Set.copyOf(scriptConformance);
        this.hasGsub = hasGsub;
        this.hasGpos = hasGpos;
        this.hasGdef = hasGdef;
        this.variationAxes = List.copyOf(variationAxes);
        this.namedInstances = List.copyOf(namedInstances);
        this.hasStat = hasStat;
        this.hasAvar = hasAvar;
        this.colorCapabilities = List.copyOf(colorCapabilities);
        this.validationState = validationState;
        this.conformanceState = conformanceState;
    }

    public FontContentDigest contentDigest() { return contentDigest; }
    public FaceIndex faceIndex() { return faceIndex; }
    public FontFormat format() { return format; }
    public String familyName() { return familyName; }
    public Set<Integer> unicodeCoverage() { return unicodeCoverage; }
    public Set<ScriptTag> scriptConformance() { return scriptConformance; }
    public List<AxisCapability> variationAxes() { return variationAxes; }
    public List<NamedInstance> namedInstances() { return namedInstances; }
    public List<ColorCapability> colorCapabilities() { return colorCapabilities; }
    public boolean hasStat() { return hasStat; }
    public boolean hasAvar() { return hasAvar; }

    public boolean coversScalar(int scalar) { return unicodeCoverage.contains(scalar); }

    public boolean coversScalars(Iterable<Integer> scalars) {
        for (int s : scalars) {
            if (!unicodeCoverage.contains(s)) return false;
        }
        return true;
    }
}
