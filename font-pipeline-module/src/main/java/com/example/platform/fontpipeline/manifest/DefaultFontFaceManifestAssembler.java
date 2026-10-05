package com.example.platform.fontpipeline.manifest;

import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resource.FontContentDigest;
import java.util.Objects;
import java.util.Set;

/**
 * Default {@link FontFaceManifestAssembler}: a pure union of the FontBox static-metadata partial
 * and the LWJGL/FreeType capability partial.
 *
 * <p><b>Pure function.</b> It reads no files, computes no digests, and accesses no storage. The
 * {@code contentDigest} argument is the digest of the sanitized bytes, produced upstream by the
 * sanitizer stage; there is no {@code contentDigest = sourceDigest} fallback.
 *
 * <p><b>Fails closed.</b> The two partials must agree on {@code sourceDigest} and {@code faceIndex};
 * any disagreement raises {@link FontManifestMergeException}.
 *
 * <p><b>State ceilings.</b> {@code validationState} is {@code "PARSED"} — Phase 4's maximum; it does
 * not assert OTS/sanitization structural validation. {@code conformanceState} is {@code "NOT_EVALUATED"}.
 * {@code scriptConformance} is the empty set (not tested in V1).
 *
 * <p>The merge does not reorder any pipeline stage and does not invoke the Phase 2/3 adapters; it
 * consumes their already-produced partial manifests.
 */
public final class DefaultFontFaceManifestAssembler implements FontFaceManifestAssembler {

    static final String VALIDATION_STATE_PARSED = "PARSED";
    static final String CONFORMANCE_STATE_NOT_EVALUATED = "NOT_EVALUATED";

    @Override
    public FontFaceManifest assemble(
            PartialFontManifest fontBox,
            LwjglPartialManifest lwjgl,
            FontContentDigest contentDigest) {
        Objects.requireNonNull(fontBox, "fontBox");
        Objects.requireNonNull(lwjgl, "lwjgl");
        Objects.requireNonNull(contentDigest, "contentDigest");

        if (!fontBox.sourceDigest().equals(lwjgl.sourceDigest())) {
            throw new FontManifestMergeException(
                    "Partial manifests have different sourceDigest: fontBox=" + fontBox.sourceDigest()
                            + " lwjgl=" + lwjgl.sourceDigest());
        }
        if (!fontBox.faceIndex().equals(lwjgl.faceIndex())) {
            throw new FontManifestMergeException(
                    "Partial manifests have different faceIndex: fontBox=" + fontBox.faceIndex()
                            + " lwjgl=" + lwjgl.faceIndex());
        }

        return new FontFaceManifest(
                contentDigest,
                fontBox.faceIndex(),
                fontBox.format(),
                fontBox.familyName(),
                fontBox.subfamilyName(),
                fontBox.weightClass(),
                fontBox.widthClass(),
                fontBox.slantStyle(),
                fontBox.glyphCount(),
                fontBox.unicodeCoverage(),
                Set.of(),
                fontBox.hasGsub(),
                fontBox.hasGpos(),
                fontBox.hasGdef(),
                lwjgl.variationAxes(),
                lwjgl.namedInstances(),
                lwjgl.hasStat(),
                lwjgl.hasAvar(),
                lwjgl.colorCapabilities(),
                VALIDATION_STATE_PARSED,
                CONFORMANCE_STATE_NOT_EVALUATED);
    }
}
