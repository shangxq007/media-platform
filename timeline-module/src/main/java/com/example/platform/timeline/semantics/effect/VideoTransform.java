package com.example.platform.timeline.semantics.effect;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Static crop, scale, clockwise rotation, position and opacity in composition space.
 * Position anchors the top-left of the rotated bounds. Crop is relative to the source.
 */
public record VideoTransform(
        int positionXMicros, int positionYMicros,
        int scaleXMicros, int scaleYMicros,
        int rotationMicroDegrees,
        int cropLeftMicros, int cropRightMicros, int cropTopMicros, int cropBottomMicros,
        int opacityMicros) {

    public static final int UNIT = 1_000_000;
    public static final String FORMAT = "composition-transform-micro-v1";
    public static final VideoTransform IDENTITY = new VideoTransform(
            0, 0, UNIT, UNIT, 0, 0, UNIT, 0, UNIT, UNIT);

    public VideoTransform {
        if (positionXMicros < -16 * UNIT || positionXMicros > 16 * UNIT
                || positionYMicros < -16 * UNIT || positionYMicros > 16 * UNIT) {
            throw new IllegalArgumentException("Position must be within 16 composition dimensions");
        }
        if (scaleXMicros < 1 || scaleYMicros < 1 || scaleXMicros > 10 * UNIT || scaleYMicros > 10 * UNIT) {
            throw new IllegalArgumentException("Scale must be in (0,10]");
        }
        if (rotationMicroDegrees < -180 * UNIT || rotationMicroDegrees >= 180 * UNIT) {
            throw new IllegalArgumentException("Rotation must be in [-180,180) degrees");
        }
        if (cropLeftMicros < 0 || cropRightMicros > UNIT || cropTopMicros < 0 || cropBottomMicros > UNIT
                || cropLeftMicros >= cropRightMicros || cropTopMicros >= cropBottomMicros) {
            throw new IllegalArgumentException("Crop must be a nonempty source-relative rectangle");
        }
        if (opacityMicros < 0 || opacityMicros > UNIT) {
            throw new IllegalArgumentException("Opacity must be in [0,1]");
        }
    }

    @JsonProperty("format")
    public String format() {
        return FORMAT;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static VideoTransform fromJson(JsonNode node) {
        requireShape(node.isObject() ? node.size() : -1, node.path("format").asText());
        return read(key -> {
            JsonNode value = node.path(key);
            if (!value.isIntegralNumber() || !value.canConvertToInt()) {
                throw new IllegalArgumentException("Invalid fixed-point transform field: " + key);
            }
            return value.intValue();
        });
    }

    public static VideoTransform fromParameters(Map<String, Object> parameters) {
        requireShape(parameters.size(), parameters.get("format"));
        return read(key -> {
            Object value = parameters.get(key);
            if (value instanceof Integer exact) return exact;
            if (value instanceof Long exact && exact >= Integer.MIN_VALUE && exact <= Integer.MAX_VALUE) {
                return exact.intValue();
            }
            throw new IllegalArgumentException("Invalid fixed-point transform field: " + key);
        });
    }

    private static void requireShape(int size, Object format) {
        if (size != 11 || !FORMAT.equals(format)) {
            throw new IllegalArgumentException("Unsupported composition transform format");
        }
    }

    private static VideoTransform read(ToIntFunction<String> integer) {
        return new VideoTransform(integer.applyAsInt("positionXMicros"), integer.applyAsInt("positionYMicros"),
                integer.applyAsInt("scaleXMicros"), integer.applyAsInt("scaleYMicros"),
                integer.applyAsInt("rotationMicroDegrees"),
                integer.applyAsInt("cropLeftMicros"), integer.applyAsInt("cropRightMicros"),
                integer.applyAsInt("cropTopMicros"), integer.applyAsInt("cropBottomMicros"),
                integer.applyAsInt("opacityMicros"));
    }
}
