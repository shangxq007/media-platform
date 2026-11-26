package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationKeyframe;
import java.util.List;
import java.util.Objects;

/** Two linear opacity samples in absolute timeline time. The curve replaces static transform opacity. */
public record OpacityKeys(MediaTime firstTime, double firstValue, MediaTime lastTime, double lastValue) {
    public OpacityKeys {
        Objects.requireNonNull(firstTime);
        Objects.requireNonNull(lastTime);
        if (!firstTime.isLessThan(lastTime) || !inRange(firstValue) || !inRange(lastValue)) {
            throw new IllegalArgumentException("Opacity requires increasing key times and values from zero to one");
        }
    }

    private static boolean inRange(double value) {
        return value >= 0 && value <= 1;
    }

    public void requireInside(MediaTime start, MediaTime end) {
        if (firstTime.isLessThan(start) || !lastTime.isLessThan(end)) {
            throw new IllegalArgumentException("Both opacity keys must be inside the visible clip interval");
        }
    }

    public static OpacityKeys from(CanonicalAutomationCurve curve) {
        var keys = curve.keyframes();
        if (!curve.valueType().equals("float") || !curve.extrapolation().equals("HOLD")
                || keys.size() != 2 || keys.stream().anyMatch(key -> !key.interpolation().equals("LINEAR"))) {
            throw new IllegalArgumentException("This opacity curve requires two linear float keys and hold extrapolation");
        }
        return new OpacityKeys(keys.getFirst().time(), keys.getFirst().value(), keys.getLast().time(), keys.getLast().value());
    }

    public CanonicalAutomationCurve curve(String id, String target, String firstId, String lastId) {
        return new CanonicalAutomationCurve(id, target, "opacity", "float", "HOLD", List.of(
                new CanonicalAutomationKeyframe(firstId, firstTime, firstValue, "LINEAR"),
                new CanonicalAutomationKeyframe(lastId, lastTime, lastValue, "LINEAR")));
    }
}
