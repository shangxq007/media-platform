package com.example.platform.fontpipeline.sanitize;

import com.example.platform.fonttext.resource.FontContentDigest;
import java.util.Objects;

/**
 * A request to sanitize raw font bytes.
 *
 * @param rawBytes raw uploaded font bytes (defensively copied)
 * @param sourceDigest content digest of {@code rawBytes}
 * @param declaredMediaType caller-declared media type (advisory only)
 */
public record FontSanitizeRequest(
        byte[] rawBytes,
        FontContentDigest sourceDigest,
        String declaredMediaType) {

    public FontSanitizeRequest {
        Objects.requireNonNull(rawBytes, "rawBytes");
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(declaredMediaType, "declaredMediaType");
        if (rawBytes.length == 0) {
            throw new IllegalArgumentException("rawBytes must not be empty");
        }
        rawBytes = rawBytes.clone();
    }

    @Override
    public byte[] rawBytes() {
        return rawBytes.clone();
    }
}
