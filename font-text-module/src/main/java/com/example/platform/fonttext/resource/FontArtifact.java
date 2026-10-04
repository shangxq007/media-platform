package com.example.platform.fonttext.resource;

import com.example.platform.shared.identity.ArtifactId;
import java.util.Objects;

/**
 * Canonical font artifact identity.
 *
 * <p>Three identities are strictly orthogonal:
 * <ul>
 *   <li>{@link ArtifactId} — business/source identity (content-independent)</li>
 *   <li>{@link FontContentDigest} — content identity (SHA-256 of bytes)</li>
 *   <li>Storage identity — carried separately (not part of this type)</li>
 * </ul>
 *
 * <p>This type does not carry storage references. One artifact may have multiple
 * physical replicas; replica binding is a separate concern.
 */
public record FontArtifact(
        ArtifactId artifactId,
        FontContentDigest sourceDigest,
        FontContentDigest contentDigest,
        long byteSize,
        String mediaType,
        FontFormat format) {

    public FontArtifact {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(format, "format");
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must be >= 0");
        }
    }
}
