package com.example.platform.render.domain.timeline.semantics.automation;

import com.example.platform.render.domain.timeline.semantics.time.MediaTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Automation and Keyframe contract.
 * <p>
 * v1 interpolation modes: HOLD, LINEAR only.
 * Keyframes must have stable IDs, unique times per parameter, deterministic
 * ordering, canonical serialization.
 */
public final class Automation {

    private Automation() {}

    /**
     * Automation curve: maps time to parameter value.
     *
     * @param automationId     stable unique ID
     * @param targetEntityId   ID of the entity being automated
     * @param parameterPath    dot-notation path to the parameter (e.g., "opacity", "transform.x")
     * @param valueType        value type identifier
     * @param keyframes        ordered list of keyframes
     * @param extrapolation    behavior before first and after last keyframe
     */
    public record AutomationCurve(
        String automationId,
        String targetEntityId,
        String parameterPath,
        String valueType,
        List<Keyframe> keyframes,
        ExtrapolationMode extrapolation
    ) {
        public AutomationCurve {
            Objects.requireNonNull(automationId, "automationId");
            Objects.requireNonNull(targetEntityId, "targetEntityId");
            Objects.requireNonNull(parameterPath, "parameterPath");
            Objects.requireNonNull(valueType, "valueType");
            Objects.requireNonNull(extrapolation, "extrapolation");
            if (automationId.isBlank()) throw new IllegalArgumentException("automationId must not be blank");
            if (parameterPath.isBlank()) throw new IllegalArgumentException("parameterPath must not be blank");
            if (valueType.isBlank()) throw new IllegalArgumentException("valueType must not be blank");
            if (keyframes == null) keyframes = List.of();
            keyframes = validateAndSort(keyframes);
        }

        private static List<Keyframe> validateAndSort(List<Keyframe> keyframes) {
            List<Keyframe> sorted = new ArrayList<>(keyframes);
            sorted.sort(Comparator.comparing(Keyframe::time));
            // Validate unique times
            for (int i = 1; i < sorted.size(); i++) {
                if (sorted.get(i).time().isEqualTo(sorted.get(i - 1).time())) {
                    throw new IllegalArgumentException(
                        "Duplicate keyframe time: " + sorted.get(i).time());
                }
            }
            return List.copyOf(sorted);
        }

        /**
         * Returns the interpolated value at the given time.
         */
        public double evaluate(MediaTime time) {
            Objects.requireNonNull(time, "time");
            if (keyframes.isEmpty()) return 0;

            // Before first keyframe: extrapolate
            if (time.isLessThanOrEqualTo(keyframes.get(0).time())) {
                return switch (extrapolation) {
                    case HOLD -> keyframes.get(0).value();
                    case LINEAR -> keyframes.get(0).value();
                };
            }
            // After last keyframe: extrapolate
            if (time.isGreaterThanOrEqualTo(keyframes.get(keyframes.size() - 1).time())) {
                return switch (extrapolation) {
                    case HOLD -> keyframes.get(keyframes.size() - 1).value();
                    case LINEAR -> keyframes.get(keyframes.size() - 1).value();
                };
            }
            // Find surrounding keyframes
            for (int i = 0; i < keyframes.size() - 1; i++) {
                Keyframe k0 = keyframes.get(i);
                Keyframe k1 = keyframes.get(i + 1);
                if (time.isGreaterThanOrEqualTo(k0.time()) && time.isLessThanOrEqualTo(k1.time())) {
                    if (k0.interpolation() == InterpolationMode.HOLD) {
                        return k0.value();
                    }
                    // LINEAR: compute fraction using exact time arithmetic
                    MediaTime duration = k1.time().subtract(k0.time());
                    if (duration.ticks() == 0) return k0.value();
                    MediaTime elapsed = time.subtract(k0.time());
                    double fraction = (double) elapsed.ticks() * duration.timeScale()
                        / (double) (elapsed.timeScale() * duration.ticks());
                    return k0.value() + fraction * (k1.value() - k0.value());
                }
            }
            return keyframes.get(keyframes.size() - 1).value();
        }
    }

    /**
     * Keyframe: a point in time with a value and interpolation to next.
     *
     * @param keyframeId   stable unique ID
     * @param time         time on the timeline
     * @param value        value at this time
     * @param interpolation interpolation mode to the next keyframe
     */
    public record Keyframe(
        String keyframeId,
        MediaTime time,
        double value,
        InterpolationMode interpolation
    ) {
        public Keyframe {
            Objects.requireNonNull(keyframeId, "keyframeId");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(interpolation, "interpolation");
            if (keyframeId.isBlank()) throw new IllegalArgumentException("keyframeId must not be blank");
        }
    }

    /**
     * Interpolation mode. v1: HOLD and LINEAR only.
     */
    public enum InterpolationMode {
        HOLD,
        LINEAR
    }

    /**
     * Extrapolation behavior for times outside the keyframe range.
     */
    public enum ExtrapolationMode {
        HOLD,
        LINEAR
    }

    /**
     * Value type identifiers for automation.
     */
    public static final class ValueType {
        public static final String FLOAT = "float";
        public static final String INT = "int";
        public static final String BOOL = "bool";
        public static final String COLOR = "color";

        private ValueType() {}
    }
}
