package com.example.platform.fontpipeline.manifest;

import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resource.FontContentDigest;

/**
 * Assembles a complete {@link FontFaceManifest} from a FontBox partial and an LWJGL partial.
 *
 * <p>Pure function: it does not read files, compute digests, or access any storage. The
 * {@code contentDigest} is supplied by the caller — the digest of the sanitized bytes produced
 * upstream by the sanitizer stage — and is never derived from the partial manifests.
 *
 * <p>Fails closed on any partial-manifest inconsistency: the two partials must describe the same
 * source bytes ({@code sourceDigest}) and the same face ({@code faceIndex}).
 */
public interface FontFaceManifestAssembler {

    /**
     * Merge the two partial manifests into the canonical manifest.
     *
     * @param fontBox partial manifest from the FontBox static-metadata route
     * @param lwjgl partial manifest from the LWJGL/FreeType capability route
     * @param contentDigest digest of the sanitized font bytes supplied by the caller
     * @return the complete canonical manifest
     * @throws FontManifestMergeException if the two partials disagree on {@code sourceDigest} or {@code faceIndex}
     */
    FontFaceManifest assemble(
            PartialFontManifest fontBox,
            LwjglPartialManifest lwjgl,
            FontContentDigest contentDigest);
}
