package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Linear multipliers anchored to the authored edges of an audio clip. Zero disables an edge. */
public record ClipAudioFades(MediaTime in, MediaTime out) {
    public static final String FADE_IN = "audio.fade_in";
    public static final String FADE_OUT = "audio.fade_out";
    public static final String TIME_PATTERN = "(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})";
    public static final ClipAudioFades NONE = new ClipAudioFades(MediaTime.ZERO, MediaTime.ZERO);

    public ClipAudioFades {
        if (Objects.requireNonNull(in).ticks() < 0 || Objects.requireNonNull(out).ticks() < 0) {
            throw new IllegalArgumentException("Audio fade durations cannot be negative");
        }
    }

    public boolean active() {
        return in.ticks() > 0 || out.ticks() > 0;
    }

    public void requireFits(MediaTime duration) {
        // Compare exactly without requiring the sum to fit in a MediaTime denominator.
        var a = BigInteger.valueOf(in.timeScale());
        var b = BigInteger.valueOf(out.timeScale());
        var sum = BigInteger.valueOf(in.ticks()).multiply(b).add(BigInteger.valueOf(out.ticks()).multiply(a));
        if (sum.multiply(BigInteger.valueOf(duration.timeScale())).compareTo(
                BigInteger.valueOf(duration.ticks()).multiply(a).multiply(b)) > 0) {
            throw new IllegalArgumentException("Fade-in plus fade-out duration exceeds the audio clip duration");
        }
    }

    public static MediaTime parseDuration(String value) {
        if (value == null || !value.matches(TIME_PATTERN)) {
            throw new IllegalArgumentException("Audio fade duration must be a nonnegative rational time");
        }
        return MediaTime.parse(value);
    }

    public static boolean isFade(TimelineClipEffect effect) {
        return FADE_IN.equals(effect.effectKey()) || FADE_OUT.equals(effect.effectKey());
    }

    public static ClipAudioFades fromEffects(List<TimelineClipEffect> effects) {
        return new ClipAudioFades(edge(effects, FADE_IN), edge(effects, FADE_OUT));
    }

    private static MediaTime edge(List<TimelineClipEffect> effects, String key) {
        var matching = effects.stream().filter(e -> key.equals(e.effectKey())).toList();
        if (matching.isEmpty()) return MediaTime.ZERO;
        if (matching.size() != 1 || matching.getFirst().parameters().size() != 1
                || !(matching.getFirst().parameters().get("duration") instanceof String value)) {
            throw new IllegalArgumentException("Expected one exact duration for " + key);
        }
        var duration = parseDuration(value);
        if (duration.ticks() == 0 || !duration.toString().equals(value)) {
            throw new IllegalArgumentException(key + " requires a positive canonical duration");
        }
        return duration;
    }

    /** Preserve unrelated effects and identities. Disabled edges are not persisted. */
    public List<TimelineClipEffect> updateEffects(String clipId, List<TimelineClipEffect> effects) {
        var changed = new ArrayList<TimelineClipEffect>();
        boolean seenIn = false;
        boolean seenOut = false;
        for (var effect : effects) {
            if (!isFade(effect)) {
                changed.add(effect);
                continue;
            }
            boolean incoming = FADE_IN.equals(effect.effectKey());
            var duration = incoming ? in : out;
            if (duration.ticks() > 0) {
                changed.add(duration.toString().equals(effect.parameters().get("duration")) ? effect
                        : new TimelineClipEffect(effect.id(), effect.effectKey(), Map.of("duration", duration.toString())));
            }
            seenIn |= incoming;
            seenOut |= !incoming;
        }
        if (!seenIn && in.ticks() > 0) changed.add(authored(clipId, FADE_IN, in));
        if (!seenOut && out.ticks() > 0) changed.add(authored(clipId, FADE_OUT, out));
        return List.copyOf(changed);
    }

    private static TimelineClipEffect authored(String clipId, String key, MediaTime duration) {
        return new TimelineClipEffect(key + "-" + clipId, key, Map.of("duration", duration.toString()));
    }
}
