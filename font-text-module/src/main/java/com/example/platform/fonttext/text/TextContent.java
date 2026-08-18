package com.example.platform.fonttext.text;

import java.util.Objects;

/**
 * ROADMAP_19 (C9/C10/C16): immutable logical authored Unicode sequence.
 * Author sequence preserved — NO silent NFC/NFD/NFKC/NFKD normalization.
 * Malformed (unpaired surrogate) input fails closed at construction.
 */
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)


public final class TextContent {

    private final String value;

    @com.fasterxml.jackson.annotation.JsonCreator
public TextContent(@com.fasterxml.jackson.annotation.JsonProperty("value") String value) {
        Objects.requireNonNull(value, "value");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new IllegalArgumentException("unpaired high surrogate at UTF-16 offset " + i);
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("unpaired low surrogate at UTF-16 offset " + i);
            }
        }
        this.value = value;
    }

    public String value() { return value; }

    /** Unicode scalar (code point) count — canonical range unit (C11). */
    public int scalarCount() { return value.codePointCount(0, value.length()); }

    /** Canonical scalar offset -> UTF-16 offset (adapter projection only, C11). */
    public int utf16OffsetForScalar(int scalarOffset) {
        if (scalarOffset < 0 || scalarOffset > scalarCount()) {
            throw new IndexOutOfBoundsException("scalar offset out of range: " + scalarOffset);
        }
        return value.offsetByCodePoints(0, scalarOffset);
    }

    /** UTF-16 offset -> canonical scalar offset (adapter projection only, C11). */
    public int scalarOffsetForUtf16(int utf16Offset) {
        if (utf16Offset < 0 || utf16Offset > value.length()) {
            throw new IndexOutOfBoundsException("utf16 offset out of range: " + utf16Offset);
        }
        return value.codePointCount(0, utf16Offset);
    }

    public String substringByScalars(int startScalar, int endScalar) {
        return value.substring(utf16OffsetForScalar(startScalar), utf16OffsetForScalar(endScalar));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TextContent t && value.equals(t.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
