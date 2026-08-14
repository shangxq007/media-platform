package com.example.platform.audio.domain.mix;

import java.util.List;
import java.util.Objects;

/**
 * AUDIO_V2 (A2/A7/A8): canonical audio mix state — the single Audio V2 mix
 * authority. A mix owns a master bus and an ordered list of typed audio routes
 * (clip → mix → master). Deterministic: route order is preserved (semantic),
 * every route references a distinct input (no duplicate input ids — a clip
 * contributes exactly once). No provider config, no FFmpeg syntax, no Media
 * source metadata.
 */
public record AudioMix(
        AudioMasterBus masterBus,
        List<AudioRoute> routes) {

    public static final AudioMix EMPTY = new AudioMix(AudioMasterBus.master(), List.of());

    public AudioMix {
        Objects.requireNonNull(masterBus, "masterBus");
        List<AudioRoute> normalized = routes == null ? List.of() : List.copyOf(routes);
        // single contribution per input: duplicate inputs rejected (no orphan/duplicate route)
        normalized.forEach(r -> {
            long dupes = normalized.stream().filter(q -> q.input().equals(r.input())).count();
            if (dupes > 1) {
                throw new IllegalArgumentException("duplicate audio route input: " + r.input());
            }
        });
        routes = normalized;
    }

    public static AudioMix of(AudioMasterBus masterBus, List<AudioRoute> routes) {
        return new AudioMix(masterBus, routes);
    }

    public static AudioMix empty() {
        return EMPTY;
    }

    public AudioMix withRoutes(List<AudioRoute> newRoutes) {
        return new AudioMix(masterBus, newRoutes);
    }

    /**
     * Deterministic canonical representation (route order preserved; each route
     * toString is deterministic). Used for semantic content / hashing.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AudioMix{master=").append(masterBus).append(",routes=[");
        for (int i = 0; i < routes.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(routes.get(i));
        }
        return sb.append("]}").toString();
    }
}
