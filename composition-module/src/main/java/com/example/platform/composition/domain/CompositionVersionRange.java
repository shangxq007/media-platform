package com.example.platform.composition.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.regex.Pattern;

/** Interpreter of the platform-owned composition-version-range-v1 interval grammar. */
public final class CompositionVersionRange {
    private static final JsonNode GRAMMAR;
    static {
        try (var stream = CompositionVersionRange.class.getResourceAsStream("/composition/version-range-v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing canonical version grammar");
            GRAMMAR = new ObjectMapper().readTree(stream);
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private static final Pattern VERSION = Pattern.compile(GRAMMAR.get("versionPattern").asText());
    private static final Pattern WILDCARD = Pattern.compile(GRAMMAR.get("wildcardPattern").asText());
    private static final Pattern BOUND = Pattern.compile(GRAMMAR.get("boundPattern").asText());
    private CompositionVersionRange() {}
    private record Version(long major, long minor, long patch) implements Comparable<Version> {
        public int compareTo(Version b) {
            int c = Long.compare(major, b.major);
            if (c == 0) c = Long.compare(minor, b.minor);
            return c == 0 ? Long.compare(patch, b.patch) : c;
        }
    }
    private record Bound(Version version, boolean inclusive) {}
    private static Version version(String value) {
        if (value == null) return null;
        var m = VERSION.matcher(value);
        if (!m.matches()) return null;
        try {
            long a = Long.parseLong(m.group(1)), b = Long.parseLong(m.group(2));
            long c = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
            long max = GRAMMAR.get("maximumComponent").asLong();
            return a > max || b > max || c > max ? null : new Version(a,b,c);
        } catch (NumberFormatException e) { return null; }
    }
    public static String check(String range, String actual) {
        if (range == null || range.isBlank()) return "MALFORMED_VERSION_RANGE";
        String expression = range.trim();
        Bound lower = null, upper = null;
        Version exact = version(expression);
        var wildcard = WILDCARD.matcher(expression);
        if (exact != null) {
            lower = new Bound(exact, true); upper = lower;
        } else if (wildcard.matches()) {
            Version start = version(wildcard.group(1) + "." + (wildcard.group(2) == null ? "0" : wildcard.group(2)) + ".0");
            if (start == null) return "MALFORMED_VERSION_RANGE";
            lower = new Bound(start, true);
            upper = new Bound(wildcard.group(2) == null ? new Version(start.major+1,0,0) : new Version(start.major,start.minor+1,0), false);
        } else {
            String[] tokens = expression.split("\\s+");
            if (tokens.length > 2) return "MALFORMED_VERSION_RANGE";
            for (String token : tokens) {
                var bound = BOUND.matcher(token);
                if (!bound.matches()) return "MALFORMED_VERSION_RANGE";
                Version v = version(bound.group(2));
                if (v == null) return "MALFORMED_VERSION_RANGE";
                String op = bound.group(1);
                if (op.startsWith(">")) {
                    if (lower != null) return "MALFORMED_VERSION_RANGE";
                    lower = new Bound(v, op.equals(">="));
                } else {
                    if (upper != null) return "MALFORMED_VERSION_RANGE";
                    upper = new Bound(v, op.equals("<="));
                }
            }
        }
        if (lower != null && upper != null) {
            int c = lower.version.compareTo(upper.version);
            if (c > 0 || c == 0 && (!lower.inclusive || !upper.inclusive)) return "INVALID_VERSION_RANGE";
        }
        Version candidate = version(actual);
        if (candidate == null) return "MALFORMED_VERSION";
        if (lower != null) {
            int c = candidate.compareTo(lower.version);
            if (c < 0 || c == 0 && !lower.inclusive) return "INCOMPATIBLE_VERSION_RANGE";
        }
        if (upper != null) {
            int c = candidate.compareTo(upper.version);
            if (c > 0 || c == 0 && !upper.inclusive) return "INCOMPATIBLE_VERSION_RANGE";
        }
        return "OK";
    }
}
