package com.example.platform.audio.domain.mix;

import java.util.List;
import java.util.Objects;

/**
 * AUDIO_V2 (A9/A16): bounded canonical DSP node.
 *
 * <p>Expresses WHAT processing is requested, provider-neutral. Never holds
 * FFmpeg filter strings or provider syntax. The bounded catalog for this
 * milestone: GAIN, EQ, COMPRESSOR, LIMITER (per frozen contract; broader DAW
 * plugin ecosystems are out of scope).
 */
public record AudioDspNode(
        DspNodeType type,
        List<AudioDspParam> params) {

    public AudioDspNode {
        Objects.requireNonNull(type, "type");
        List<AudioDspParam> normalized = params == null ? List.of() : List.copyOf(params);
        // deterministic order: preserve caller order (semantic chain order is
        // meaningful), but duplicate parameter names are rejected
        normalized.forEach(p -> {
            long dupes = normalized.stream().filter(q -> q.name().equals(p.name())).count();
            if (dupes > 1) {
                throw new IllegalArgumentException("duplicate DSP param name: " + p.name());
            }
        });
        params = normalized;
    }

    public static AudioDspNode of(DspNodeType type, AudioDspParam... params) {
        return new AudioDspNode(type, List.of(params));
    }

    public enum DspNodeType {
        GAIN,
        EQ,
        COMPRESSOR,
        LIMITER
    }

    /**
     * Deterministic canonical representation: {@code type:name=value,...}.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(type.name());
        for (AudioDspParam p : params) {
            sb.append(':').append(p.name()).append('=').append(p.value());
        }
        return sb.toString();
    }
}
