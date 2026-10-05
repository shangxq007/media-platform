package com.example.platform.render.infrastructure.font;

import com.example.platform.fonttext.manifest.FontFaceManifest;
import java.util.Objects;

/**
 * FONT-V1 Phase 6b: one-way projection {@link FontFaceManifest} -> {@link FontAsset}.
 *
 * <p>Pure conversion: no Spring, no IO, no pipeline, no registry. It is deliberately NOT wired into
 * any render consumer (Phase 6b only establishes the type conversion).
 *
 * <p><b>Identity.</b> {@code FontAsset.id} is the executed-content identity: the manifest's
 * {@code contentDigest} (SHA-256 of the sanitized bytes) in lowercase hex — the same canonical
 * identity the font-text contract assigns to the validated execution content. The manifest carries
 * no separate business id or file name, so {@code fileName} is {@code null} rather than fabricated.
 *
 * <p><b>Digest mapping.</b> {@code FontAsset.sha256} is the sanitized {@code contentDigest} string,
 * never the raw {@code sourceDigest}.
 *
 * <p><b>Operational fields.</b> The manifest has no security/validation/subset facts, so
 * {@code securityResult} / {@code validationResult} / {@code subsetResult} are {@code null}
 * (not-evaluated) and {@code storageUri} is {@code null}.
 *
 * <p><b>V1 limitations.</b> {@code fileSize} is {@code 0}: {@code FontProcessingResult} does not
 * expose the sanitized bytes, so the size cannot be known here. {@code fontSubfamily} is
 * {@code null}: {@link FontFaceManifest} exposes no {@code subfamilyName} accessor
 * (known FONT-FACEMANIFEST-ACCESSOR-GAP; not addressed in Phase 6b).
 */
public final class FontAssetProjection {

    private FontAssetProjection() {
    }

    /**
     * Project a validated manifest into the render-side {@link FontAsset} shape.
     *
     * @param manifest the canonical font face manifest (must not be {@code null})
     * @return a {@link FontAsset} with {@code status = READY} and no operational results attached
     * @throws NullPointerException if {@code manifest} is {@code null}
     */
    public static FontAsset from(FontFaceManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        String contentDigest = Objects.requireNonNull(manifest.contentDigest(), "manifest.contentDigest()")
                .sha256Hex();
        return new FontAsset(
                contentDigest,
                null,
                manifest.familyName(),
                null,
                manifest.format().name(),
                0L,
                contentDigest,
                null,
                FontAssetStatus.READY,
                null,
                null,
                null);
    }
}
