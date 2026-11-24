package com.example.platform.timeline.canonical;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Exact authored display proportions, independent of preview and delivery limits. */
public record CompositionAspectRatio(int numerator, int denominator) {
    public static final String FORMAT = "composition-aspect-rational-v1";
    public static final CompositionAspectRatio DEFAULT = new CompositionAspectRatio(4, 3);

    public CompositionAspectRatio {
        if (numerator <= 0 || denominator <= 0) {
            throw new IllegalArgumentException("Composition aspect components must be integers from 1 to 2147483647");
        }
        int left = numerator;
        int right = denominator;
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        numerator /= left;
        denominator /= left;
    }

    @JsonProperty("format")
    public String format() {
        return FORMAT;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CompositionAspectRatio fromJson(JsonNode value) {
        if (value == null || !value.isObject() || value.size() != 3
                || !FORMAT.equals(value.path("format").textValue())) {
            throw new IllegalArgumentException("Unsupported composition aspect ratio format");
        }
        var numerator = value.path("numerator");
        var denominator = value.path("denominator");
        if (!numerator.isIntegralNumber() || !numerator.canConvertToInt()
                || !denominator.isIntegralNumber() || !denominator.canConvertToInt()) {
            throw new IllegalArgumentException("Composition aspect components must be exact bounded integers");
        }
        var ratio = new CompositionAspectRatio(numerator.intValue(), denominator.intValue());
        if (ratio.numerator() != numerator.intValue() || ratio.denominator() != denominator.intValue()) {
            throw new IllegalArgumentException("Stored composition aspect ratio must be reduced");
        }
        return ratio;
    }

    public static CompositionAspectRatio fromParameters(Map<String, Object> parameters) {
        if (parameters.size() != 1 || !(parameters.get("aspectRatio") instanceof Map<?, ?> ratio)
                || ratio.size() != 3 || !FORMAT.equals(ratio.get("format"))) {
            throw new IllegalArgumentException("Unsupported composition aspect ratio format");
        }
        return new CompositionAspectRatio(exactInt(ratio.get("numerator")), exactInt(ratio.get("denominator")));
    }

    private static int exactInt(Object value) {
        if (value instanceof Integer exact) return exact;
        if (value instanceof Long exact && exact >= 1 && exact <= Integer.MAX_VALUE) return exact.intValue();
        throw new IllegalArgumentException("Composition aspect components must be exact bounded integers");
    }

    /** Reduced pair used for exact projection and patch metadata. */
    public static CompositionAspectRatio fromKey(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,9}:[1-9][0-9]{0,9}")) {
            throw new IllegalArgumentException("Invalid exact composition aspect ratio");
        }
        int separator = value.indexOf(':');
        var ratio = new CompositionAspectRatio(Integer.parseInt(value.substring(0, separator)),
                Integer.parseInt(value.substring(separator + 1)));
        if (!ratio.toString().equals(value)) {
            throw new IllegalArgumentException("Stored composition aspect ratio must be reduced");
        }
        return ratio;
    }

    public boolean matches(int rasterWidth, int rasterHeight) {
        return rasterWidth > 0 && rasterHeight > 0
                && (long) rasterWidth * denominator == (long) rasterHeight * numerator;
    }

    @Override
    public String toString() {
        return numerator + ":" + denominator;
    }
}
