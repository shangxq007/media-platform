package com.example.platform.execution.planning;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Roadmap #21 canonical byte writer (Correction 5 B2) — end-to-end injective
 * framing for the COMPLETE semantic stream.
 *
 * <p>Every variable-length scalar is UTF-8 byte-length-prefixed:
 * {@code <byteLength>:<UTF8 bytes>}. Lists are independently framed elements
 * with explicit count. Optionals carry explicit present/absent markers.
 * Numeric scalars use exact canonical decimal forms. NO delimiter-only
 * grammar for arbitrary semantic values; semantic content can never affect
 * structure.
 *
 * <p>DOMAIN OWNERSHIP unchanged: #20-owned objects are delegated to the #20
 * RenderPlanCanonicalCodec (single canonical authority); this writer frames
 * the #21 structural layer.
 */
public final class CanonicalWriter {

    private final StringBuilder sb = new StringBuilder();

    /** Tag/variant/type marker (framed). */
    public CanonicalWriter tag(String tag) {
        frame(tag);
        return this;
    }

    /** Named field — framed key + framed value (injective pair). */
    public CanonicalWriter field(String key, String value) {
        frame(key);
        frame(value == null ? "" : value);
        return this;
    }

    /** Optional marker: '1' present / '0' absent, then value when present. */
    public CanonicalWriter optional(boolean present, String value) {
        sb.append(present ? "1" : "0");
        if (present) {
            frame(value == null ? "" : value);
        }
        return this;
    }

    /** Framed scalar (nullable → explicit "null" marker distinct from empty). */
    public CanonicalWriter scalar(String value) {
        if (value == null) {
            frame("\u0000NULL");
        } else {
            frame(value);
        }
        return this;
    }

    /** Framed exact long. */
    public CanonicalWriter exactLong(long v) {
        frame(Long.toString(v));
        return this;
    }

    /** Framed exact rational (ticks/timeScale). */
    public CanonicalWriter exactTime(long ticks, long timeScale) {
        frame(Long.toString(ticks));
        frame(Long.toString(timeScale));
        return this;
    }

    /**
     * Framed list with explicit count; elements pre-canonicalized by the
     * caller. Non-semantic-order collections should be sorted by the caller
     * over canonical keys before passing.
     */
    public CanonicalWriter list(List<String> canonicalElements) {
        List<String> elems = canonicalElements == null ? List.of() : canonicalElements;
        frame(Integer.toString(elems.size()));
        for (String e : elems) {
            frame(e == null ? "" : e);
        }
        return this;
    }

    /** Deterministic sort helper for non-semantic-order collections. */
    public static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        return copy;
    }

    /** Final canonical byte string (UTF-8 framed stream). */
    public String build() {
        return sb.toString();
    }

    /** UTF-8 byte length of a string (multibyte-safe). */
    public static int utf8Length(String s) {
        return s == null ? 0 : s.getBytes(StandardCharsets.UTF_8).length;
    }

    private void frame(String value) {
        sb.append(utf8Length(value)).append(':').append(value);
    }
}
