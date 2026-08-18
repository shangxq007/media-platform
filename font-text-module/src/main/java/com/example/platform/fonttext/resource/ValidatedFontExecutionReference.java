package com.example.platform.fonttext.resource;

import com.example.platform.fonttext.security.FontSecurityState;
import java.util.Objects;

/**
 * ROADMAP_19 (C8): historical dual-pin — source content digest + exact
 * validated execution content digest. Historical render NEVER re-sanitizes.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class ValidatedFontExecutionReference {

    private final FontContentDigest sourceFontContentDigest;
    private final FontContentDigest validatedExecutionContentDigest;
    private final FontSecurityState securityState;
    private final FontFormat format;
    private final FaceIndex faceIndex;

    @com.fasterxml.jackson.annotation.JsonCreator
public ValidatedFontExecutionReference(@com.fasterxml.jackson.annotation.JsonProperty("sourceFontContentDigest") FontContentDigest sourceFontContentDigest, @com.fasterxml.jackson.annotation.JsonProperty("validatedExecutionContentDigest") FontContentDigest validatedExecutionContentDigest, @com.fasterxml.jackson.annotation.JsonProperty("securityState") FontSecurityState securityState, @com.fasterxml.jackson.annotation.JsonProperty("format") FontFormat format, @com.fasterxml.jackson.annotation.JsonProperty("faceIndex") FaceIndex faceIndex) {
        this.sourceFontContentDigest = Objects.requireNonNull(sourceFontContentDigest, "source digest");
        this.validatedExecutionContentDigest = Objects.requireNonNull(validatedExecutionContentDigest, "validated digest");
        this.securityState = Objects.requireNonNull(securityState, "securityState");
        this.format = Objects.requireNonNull(format, "format");
        this.faceIndex = Objects.requireNonNull(faceIndex, "faceIndex");
        if (securityState == FontSecurityState.RAW) {
            throw new IllegalArgumentException("RAW font cannot be a validated execution reference");
        }
    }

    public FontContentDigest sourceFontContentDigest() { return sourceFontContentDigest; }
    public FontContentDigest validatedExecutionContentDigest() { return validatedExecutionContentDigest; }
    public FontSecurityState securityState() { return securityState; }
    public FontFormat format() { return format; }
    public FaceIndex faceIndex() { return faceIndex; }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ValidatedFontExecutionReference r)) return false;
        return sourceFontContentDigest.equals(r.sourceFontContentDigest)
                && validatedExecutionContentDigest.equals(r.validatedExecutionContentDigest)
                && securityState == r.securityState && format == r.format && faceIndex.equals(r.faceIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFontContentDigest, validatedExecutionContentDigest, securityState, format, faceIndex);
    }
}
