package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Paired composition-space translation keys in exact absolute timeline time. */
public record PositionKeys(List<Key> keys) {
    public static final String X = "transform.positionXMicros";
    public static final String Y = "transform.positionYMicros";
    public static final int LIMIT = 16 * VideoTransform.UNIT;
    public static final int MAX_KEYS = 16;

    public record Key(MediaTime time, int xMicros, int yMicros) {
        public Key {
            if (time.ticks() < 0 || xMicros < -LIMIT || xMicros > LIMIT || yMicros < -LIMIT || yMicros > LIMIT) {
                throw new IllegalArgumentException("Position keys require nonnegative times and offsets within 16 composition dimensions");
            }
        }
    }

    public PositionKeys {
        keys = List.copyOf(keys);
        if (keys.size() == 1 || keys.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Specify two to sixteen position keys, or clear them all");
        }
        try {
            for (int i = 1; i < keys.size(); i++) {
                if (!keys.get(i - 1).time().isLessThan(keys.get(i).time())) {
                    throw new IllegalArgumentException("Position key times must strictly increase");
                }
            }
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Position key times exceed the exact time arithmetic range", overflow);
        }
    }

    public void requireInside(MediaTime start, MediaTime end) {
        try {
            if (!keys.isEmpty() && (keys.getFirst().time().isLessThan(start) || !keys.getLast().time().isLessThan(end))) {
                throw new IllegalArgumentException("Position keys must fall inside the clip's half-open timeline interval");
            }
            for (var key : keys) key.time().subtract(start);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Position key times exceed the exact time arithmetic range", overflow);
        }
    }

    public static boolean isPosition(CanonicalAutomationCurve curve) {
        return X.equals(curve.parameterPath()) || Y.equals(curve.parameterPath());
    }

    public static Optional<PositionKeys> forClip(List<CanonicalAutomationCurve> curves, String id) {
        var bound = curves.stream().filter(c -> isPosition(c) && id.equals(c.targetEntityId())).toList();
        if (bound.isEmpty()) return Optional.empty();
        var x = bound.stream().filter(c -> X.equals(c.parameterPath())).findFirst();
        var y = bound.stream().filter(c -> Y.equals(c.parameterPath())).findFirst();
        if (bound.size() != 2 || x.isEmpty() || y.isEmpty()) {
            throw new IllegalArgumentException("Position requires one X and one Y curve on the same clip");
        }
        var horizontal = x.get();
        var vertical = y.get();
        for (var curve : bound) {
            if (!curve.valueType().equals("float") || !curve.extrapolation().equals("HOLD")
                    || curve.keyframes().stream().anyMatch(k -> !k.interpolation().equals("LINEAR"))) {
                throw new IllegalArgumentException("Position supports only linear keys with hold extrapolation");
            }
        }
        if (horizontal.keyframes().size() < 2 || horizontal.keyframes().size() != vertical.keyframes().size()) {
            throw new IllegalArgumentException("X and Y must have matching position key times");
        }
        var keys = new ArrayList<Key>();
        for (int i = 0; i < horizontal.keyframes().size(); i++) {
            var a = horizontal.keyframes().get(i);
            var b = vertical.keyframes().get(i);
            if (!a.time().equals(b.time())) throw new IllegalArgumentException("X and Y must have matching position key times");
            keys.add(new Key(a.time(), micros(a.value()), micros(b.value())));
        }
        return Optional.of(new PositionKeys(keys));
    }

    private static int micros(double value) {
        if (!Double.isFinite(value) || value != Math.rint(value) || value < -LIMIT || value > LIMIT) {
            throw new IllegalArgumentException("Position requires integral composition micros within bounds");
        }
        return (int) value;
    }

    public CanonicalAutomationCurve curve(String clipId, boolean xAxis, CanonicalAutomationCurve previous) {
        String path = xAxis ? X : Y;
        String id = previous == null ? "position-" + (xAxis ? "x-" : "y-") + clipId : previous.automationId();
        var frames = keys.stream().map(k -> {
            String keyId = previous == null ? id + "/" + k.time() : previous.keyframes().stream()
                    .filter(old -> old.time().equals(k.time())).map(CanonicalAutomationKeyframe::keyframeId)
                    .findFirst().orElse(id + "/" + k.time());
            return new CanonicalAutomationKeyframe(keyId, k.time(), xAxis ? k.xMicros() : k.yMicros(), "LINEAR");
        }).toList();
        return new CanonicalAutomationCurve(id, clipId, path, "float", "HOLD", frames);
    }

    public String canonicalSamples() {
        return keys.stream().map(k -> k.time() + ";" + k.xMicros() + ";" + k.yMicros())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    public static PositionKeys parseSamples(String samples) {
        return new PositionKeys(java.util.Arrays.stream(samples.split("\\|", -1)).map(part -> {
            var values = part.split(";", -1);
            if (values.length != 3) throw new IllegalArgumentException("Invalid position key sample");
            return new Key(MediaTime.parse(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
        }).toList());
    }
}
