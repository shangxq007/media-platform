package com.example.platform.fontpipeline;

import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resource.FontContentDigest;
import java.util.Objects;

/**
 * Output of the isolated font processing pipeline.
 *
 * <p>{@code sourceDigest} is SHA-256 of the raw input bytes; {@code contentDigest} is SHA-256 of the
 * sanitized bytes produced by the sanitizer stage and must equal {@code manifest.contentDigest()}.
 * The sanitized bytes themselves are never exposed by this result.
 */
public record FontProcessingResult(
        FontContentDigest sourceDigest,
        FontContentDigest contentDigest,
        FontFaceManifest manifest) {

    public FontProcessingResult {
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(manifest, "manifest");
        if (!contentDigest.equals(manifest.contentDigest())) {
            throw new IllegalArgumentException("contentDigest must match manifest.contentDigest()");
        }
    }
}
