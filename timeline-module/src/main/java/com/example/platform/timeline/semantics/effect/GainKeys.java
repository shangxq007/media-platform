package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** Audio clip gain multipliers, in exact absolute timeline time. */
public record GainKeys(List<Key> keys) {
    public static final String PATH = "audio.gainMultiplierMicros";
    public static final int MAX_KEYS = PositionKeys.MAX_KEYS;
    public static final int MAX_MULTIPLIER = 1_000_000;

    public record Key(MediaTime time, long multiplierMicros) {
        public Key {
            java.util.Objects.requireNonNull(time, "gain key time");
            if (multiplierMicros < 0 || multiplierMicros > MAX_MULTIPLIER) {
                throw new IllegalArgumentException("Gain key multiplier must be between 0 and 1 inclusive");
            }
        }
    }

    public GainKeys {
        keys = List.copyOf(keys);
        if (keys.size() == 1 || keys.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Specify two to sixteen gain keys, or clear them all");
        }
        for (int i = 1; i < keys.size(); i++) {
            if (compare(keys.get(i - 1).time(), keys.get(i).time()) >= 0) {
                throw new IllegalArgumentException("Gain key times must strictly increase");
            }
        }
    }

    public void requireInside(MediaTime start, MediaTime end) {
        if (!keys.isEmpty() && (compare(keys.getFirst().time(), start) < 0 || compare(keys.getLast().time(), end) >= 0)) {
            throw new IllegalArgumentException("Gain keys must fall inside the clip's half-open timeline interval");
        }
        // Worker transport also needs an exactly representable clip-local offset.
        for (var key : keys) offset(key.time(), start);
    }

    public static boolean isGain(CanonicalAutomationCurve curve) {
        return PATH.equals(curve.parameterPath());
    }

    public static Optional<GainKeys> forClip(List<CanonicalAutomationCurve> curves, String clipId) {
        var bound = curves.stream().filter(c -> isGain(c) && clipId.equals(c.targetEntityId())).toList();
        if (bound.isEmpty()) return Optional.empty();
        var curve = bound.getFirst();
        if (bound.size() != 1 || !"float".equals(curve.valueType()) || !"HOLD".equals(curve.extrapolation())
                || curve.keyframes().size() < 2) {
            throw new IllegalArgumentException("Gain requires one nonempty linear channel with hold extrapolation");
        }
        return Optional.of(new GainKeys(curve.keyframes().stream().map(k -> {
            if (!"LINEAR".equals(k.interpolation()) || !Double.isFinite(k.value()) || k.value() != Math.rint(k.value())) {
                throw new IllegalArgumentException("Gain requires linear keys with integral multiplier micros");
            }
            return new Key(k.time(), (long) k.value());
        }).toList()));
    }

    public CanonicalAutomationCurve curve(String clipId, CanonicalAutomationCurve previous) {
        String id = previous == null ? "gain-" + clipId : previous.automationId();
        var frames = keys.stream().map(k -> {
            String fallback = id + "/" + k.time();
            String keyId = previous == null ? fallback : previous.keyframes().stream()
                    .filter(old -> old.time().equals(k.time())).map(CanonicalAutomationKeyframe::keyframeId)
                    .findFirst().orElse(fallback);
            return new CanonicalAutomationKeyframe(keyId, k.time(), k.multiplierMicros(), "LINEAR");
        }).toList();
        return new CanonicalAutomationCurve(id, clipId, PATH, "float", "HOLD", frames);
    }

    public GainKeys relativeTo(MediaTime origin) {
        return new GainKeys(keys.stream().map(k -> new Key(offset(k.time(), origin), k.multiplierMicros())).toList());
    }

    public String canonicalSamples() {
        return keys.stream().map(k -> k.time() + ";" + k.multiplierMicros())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    public static GainKeys parseSamples(String samples) {
        return new GainKeys(java.util.Arrays.stream(samples.split("\\|", -1)).map(part -> {
            var values = part.split(";", -1);
            if (values.length != 2) throw new IllegalArgumentException("Invalid gain key sample");
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
            throw new IllegalArgumentException("Gain key is not exactly representable relative to the clip start", overflow);
        }
    }
}
