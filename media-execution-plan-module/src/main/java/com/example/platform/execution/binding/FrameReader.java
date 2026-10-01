package com.example.platform.execution.binding;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Sequential reader of the canonical writer's injective framing.
 *
 * <p>Mirror of {@code CanonicalWriter}: every value is a UTF-8 byte-length
 * prefixed frame {@code <byteLength>:<UTF-8 bytes>}. Parsing walks the exact byte
 * stream, so the encoding is losslessly invertible; any malformed frame fails
 * closed.
 */
final class FrameReader {

    private final byte[] bytes;
    private int offset;

    private FrameReader(byte[] bytes) {
        this.bytes = bytes;
    }

    static FrameReader of(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        return new FrameReader(payload);
    }

    boolean hasRemaining() {
        return offset < bytes.length;
    }

    /** Reads the next frame and requires it to equal the expected tag/payload. */
    void requireTag(String expected) {
        String actual = frame();
        if (!expected.equals(actual)) {
            throw new UnsupportedPersistedConstructException(
                    "expected tag " + expected + " but read " + actual);
        }
    }

    /** Reads the next framed value. */
    String text() {
        return frame();
    }

    String text(String field) {
        try {
            return frame();
        } catch (RuntimeException failure) {
            throw new UnsupportedPersistedConstructException("malformed field " + field);
        }
    }

    long exactLong() {
        try {
            return Long.parseLong(frame());
        } catch (NumberFormatException failure) {
            throw new UnsupportedPersistedConstructException("malformed exact long");
        }
    }

    int exactInt() {
        long value = exactLong();
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new UnsupportedPersistedConstructException("malformed exact int");
        }
        return (int) value;
    }

    boolean exactBoolean() {
        String value = frame();
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new UnsupportedPersistedConstructException("malformed boolean");
    }

    /** Reads a 1/0 presence marker; returns the value only when present. */
    String optional() {
        // CanonicalWriter writes the presence marker as a RAW byte (no length prefix)
        // immediately followed, when present, by the framed value.
        if (offset >= bytes.length) {
            throw new UnsupportedPersistedConstructException("truncated optional marker");
        }
        byte marker = bytes[offset++];
        if (marker == '0') {
            return null;
        }
        if (marker != '1') {
            throw new UnsupportedPersistedConstructException("malformed optional marker");
        }
        return frame();
    }

    /**
     * Reads the next frame as a nested canonical sub-stream (a value written with
     * {@code field(...)} or as a {@code list(...)} element is itself a framed stream).
     */
    FrameReader nested() {
        return FrameReader.of(text().getBytes(StandardCharsets.UTF_8));
    }

    /** Reads a named field whose value is itself a framed canonical sub-stream. */
    FrameReader fieldNested(String key) {
        requireField(key);
        return nested();
    }

    /** Reads the named field's framed value. */
    String field(String key) {
        requireField(key);
        return text();
    }

    long fieldExactLong(String key) {
        requireField(key);
        return exactLong();
    }

    int fieldInt(String key) {
        requireField(key);
        return exactInt();
    }

    boolean fieldBoolean(String key) {
        requireField(key);
        return exactBoolean();
    }

    private void requireField(String key) {
        String actual = text();
        if (!key.equals(actual)) {
            throw new UnsupportedPersistedConstructException(
                    "expected field " + key + " but read " + actual);
        }
    }

    /** Reads a 1/0 presence marker and, when present, the nested sub-stream. */
    FrameReader optionalNested() {
        String value = optional();
        return value == null ? null : FrameReader.of(value.getBytes(StandardCharsets.UTF_8));
    }

    int listCount() {
        long value = exactLong();
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new UnsupportedPersistedConstructException("malformed list count");
        }
        return (int) value;
    }

    private String frame() {
        int colon = offset;
        while (colon < bytes.length && bytes[colon] != ':') {
            colon++;
        }
        if (colon == offset || colon >= bytes.length) {
            throw new UnsupportedPersistedConstructException("truncated canonical frame");
        }
        int length;
        try {
            length = Integer.parseInt(
                    new String(bytes, offset, colon - offset, StandardCharsets.US_ASCII));
        } catch (NumberFormatException failure) {
            throw new UnsupportedPersistedConstructException("malformed frame length");
        }
        int start = colon + 1;
        int end = start + length;
        if (length < 0 || end > bytes.length) {
            throw new UnsupportedPersistedConstructException("truncated canonical frame");
        }
        offset = end;
        return new String(bytes, start, length, StandardCharsets.UTF_8);
    }
}
