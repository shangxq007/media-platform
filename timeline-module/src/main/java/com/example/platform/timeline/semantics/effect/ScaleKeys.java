package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** Uniform fixed-point multipliers, in exact absolute timeline time. */
public record ScaleKeys(List<Key> keys) {
    public static final String PATH = "transform.scaleMultiplierMicros";
    public static final int MAX_KEYS = PositionKeys.MAX_KEYS;
    private static final long UNIT = VideoTransform.UNIT;
    public static final long MAX_MULTIPLIER = 10 * UNIT * UNIT;

    public record Key(MediaTime time, long multiplierMicros) {
        public Key {
            java.util.Objects.requireNonNull(time, "scale key time");
            if (multiplierMicros < 1 || multiplierMicros > MAX_MULTIPLIER) {
                throw new IllegalArgumentException("Scale key multiplier must be positive and within the transform range");
            }
        }
    }

    public ScaleKeys {
        keys = List.copyOf(keys);
        if (keys.size() == 1 || keys.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Specify two to sixteen scale keys, or clear them all");
        }
        for (int i = 1; i < keys.size(); i++) {
            if (compare(keys.get(i - 1).time(), keys.get(i).time()) >= 0) {
                throw new IllegalArgumentException("Scale key times must strictly increase");
            }
        }
    }

    public void requireInside(MediaTime start, MediaTime end) {
        if (!keys.isEmpty() && (compare(keys.getFirst().time(), start) < 0 || compare(keys.getLast().time(), end) >= 0)) {
            throw new IllegalArgumentException("Scale keys must fall inside the clip's half-open timeline interval");
        }
        // Worker transport also needs an exactly representable clip-local offset.
        for (var key : keys) offset(key.time(), start);
    }

    public void requireBounds(VideoTransform transform) {
        long min = Math.min(transform.scaleXMicros(), transform.scaleYMicros());
        long max = Math.max(transform.scaleXMicros(), transform.scaleYMicros());
        long lower = (UNIT + min - 1) / min;
        long upper = MAX_MULTIPLIER / max;
        // The linear curve cannot overshoot its endpoints.
        if (keys.stream().anyMatch(k -> k.multiplierMicros() < lower || k.multiplierMicros() > upper)) {
            throw new IllegalArgumentException("Scale keys must keep both effective transform axes within [0.000001, 10]");
        }
    }

    public static boolean isScale(CanonicalAutomationCurve curve) {
        return PATH.equals(curve.parameterPath());
    }

    public static Optional<ScaleKeys> forClip(List<CanonicalAutomationCurve> curves, String clipId) {
        var bound = curves.stream().filter(c -> isScale(c) && clipId.equals(c.targetEntityId())).toList();
        if (bound.isEmpty()) return Optional.empty();
        var curve = bound.getFirst();
        if (bound.size() != 1 || !"float".equals(curve.valueType()) || !"HOLD".equals(curve.extrapolation())
                || curve.keyframes().size() < 2) {
            throw new IllegalArgumentException("Scale requires one nonempty linear channel with hold extrapolation");
        }
        return Optional.of(new ScaleKeys(curve.keyframes().stream().map(k -> {
            if (!"LINEAR".equals(k.interpolation()) || !Double.isFinite(k.value()) || k.value() != Math.rint(k.value())) {
                throw new IllegalArgumentException("Scale requires linear keys with integral multiplier micros");
            }
            return new Key(k.time(), (long) k.value());
        }).toList()));
    }

    public CanonicalAutomationCurve curve(String clipId, CanonicalAutomationCurve previous) {
        String id = previous == null ? "scale-" + clipId : previous.automationId();
        var frames = keys.stream().map(k -> {
            String fallback = id + "/" + k.time();
            String keyId = previous == null ? fallback : previous.keyframes().stream()
                    .filter(old -> old.time().equals(k.time())).map(CanonicalAutomationKeyframe::keyframeId)
                    .findFirst().orElse(fallback);
            return new CanonicalAutomationKeyframe(keyId, k.time(), k.multiplierMicros(), "LINEAR");
        }).toList();
        return new CanonicalAutomationCurve(id, clipId, PATH, "float", "HOLD", frames);
    }

    public ScaleKeys relativeTo(MediaTime origin) {
        return new ScaleKeys(keys.stream().map(k -> new Key(offset(k.time(), origin), k.multiplierMicros())).toList());
    }

    public String canonicalSamples() {
        return keys.stream().map(k -> k.time() + ";" + k.multiplierMicros())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    public static ScaleKeys parseSamples(String samples) {
        return new ScaleKeys(java.util.Arrays.stream(samples.split("\\|", -1)).map(part -> {
            var values = part.split(";", -1);
            if (values.length != 2) throw new IllegalArgumentException("Invalid scale key sample");
            return new Key(MediaTime.parse(values[0]), Long.parseLong(values[1]));
        }).toList());
    }

    private static int compare(MediaTime left, MediaTime right) {
        return product(left.ticks(), right.timeScale()).compareTo(product(right.ticks(), left.timeScale()));
    }

    private static BigInteger product(long a, long b) {
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
    }

    private static MediaTime offset(MediaTime time, MediaTime start) {
        var n = product(time.ticks(), start.timeScale()).subtract(product(start.ticks(), time.timeScale()));
        var d = product(time.timeScale(), start.timeScale());
        var gcd = n.gcd(d);
        try {
            return MediaTime.ofRational(n.divide(gcd).longValueExact(), d.divide(gcd).longValueExact());
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Scale key is not exactly representable relative to the clip start", overflow);
        }
    }
}
