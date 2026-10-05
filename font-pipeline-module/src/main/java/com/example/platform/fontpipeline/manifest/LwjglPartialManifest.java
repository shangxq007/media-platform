package com.example.platform.fontpipeline.manifest;

import com.example.platform.fonttext.manifest.FontFaceManifest.AxisCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.ColorCapability;
import com.example.platform.fonttext.manifest.FontFaceManifest.NamedInstance;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import java.util.List;
import java.util.Objects;

/**
 * Partial manifest produced by the LWJGL/FreeType route.
 *
 * <p>This is the complement of {@link PartialFontManifest} (FontBox static metadata): it carries
 * only the capabilities FontBox does not expose — variable-font design space, named instances,
 * {@code STAT}/{@code avar} presence, and color-font technologies. The two partial manifests share
 * no fields; a Phase 4 assembler performs a true disjoint union into the canonical manifest.
 *
 * <p>Container metadata ({@code format}, names, OS/2, cmap, glyph count) is deliberately absent;
 * it comes from the FontBox route.
 *
 * @param sourceDigest content digest of the parsed bytes
 * @param faceIndex face index within a collection ({@code 0} for single-face fonts)
 * @param variationAxes variable-font axes, empty for static fonts
 * @param namedInstances named instances, empty when the font declares none
 * @param hasStat {@code STAT} table present
 * @param hasAvar {@code avar} table present
 * @param colorCapabilities color technologies present, empty for non-color fonts
 */
public record LwjglPartialManifest(
        FontContentDigest sourceDigest,
        FaceIndex faceIndex,
        List<AxisCapability> variationAxes,
        List<NamedInstance> namedInstances,
        boolean hasStat,
        boolean hasAvar,
        List<ColorCapability> colorCapabilities) {

    public LwjglPartialManifest {
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(faceIndex, "faceIndex");
        variationAxes = List.copyOf(Objects.requireNonNull(variationAxes, "variationAxes"));
        namedInstances = List.copyOf(Objects.requireNonNull(namedInstances, "namedInstances"));
        colorCapabilities = List.copyOf(Objects.requireNonNull(colorCapabilities, "colorCapabilities"));
    }
}
