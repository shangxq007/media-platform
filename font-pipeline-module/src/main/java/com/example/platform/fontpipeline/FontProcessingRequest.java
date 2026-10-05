package com.example.platform.fontpipeline;

import com.example.platform.fonttext.resource.FaceIndex;
import java.util.Objects;

/**
 * Input to the isolated font processing pipeline: raw font bytes plus the caller-declared
 * media type and the face index to parse.
 *
 * @param rawBytes raw font bytes (defensively copied)
 * @param declaredMediaType caller-declared media type (advisory only)
 * @param faceIndex face index within a collection ({@code 0} for single-face fonts)
 */
public record FontProcessingRequest(
        byte[] rawBytes,
        String declaredMediaType,
        FaceIndex faceIndex) {

    public FontProcessingRequest {
        Objects.requireNonNull(rawBytes, "rawBytes");
        if (rawBytes.length == 0) {
            throw new IllegalArgumentException("rawBytes must not be empty");
        }
        Objects.requireNonNull(declaredMediaType, "declaredMediaType");
        if (declaredMediaType.isBlank()) {
            throw new IllegalArgumentException("declaredMediaType must not be blank");
        }
        Objects.requireNonNull(faceIndex, "faceIndex");
        rawBytes = rawBytes.clone();
    }

    @Override
    public byte[] rawBytes() {
        return rawBytes.clone();
    }

    public static FontProcessingRequest ofSingleFace(byte[] rawBytes, String declaredMediaType) {
        return new FontProcessingRequest(rawBytes, declaredMediaType, new FaceIndex(0));
    }
}
