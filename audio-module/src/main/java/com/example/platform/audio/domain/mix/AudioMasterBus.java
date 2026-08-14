package com.example.platform.audio.domain.mix;

import java.util.Objects;

/**
 * AUDIO_V2 (A8): canonical mix root / output bus semantic.
 *
 * <p>AudioMasterBus is the canonical routing/mix root of an {@link AudioMix}.
 * It is NOT an FFmpeg output, codec config, render target or provider option.
 * Sends/returns and subgroups beyond the master are DEFERRED.
 */
public record AudioMasterBus(
        String busId) {

    public static final String DEFAULT_MASTER_BUS_ID = "master";

    public AudioMasterBus {
        Objects.requireNonNull(busId, "busId");
        if (busId.isBlank()) {
            throw new IllegalArgumentException("AudioMasterBus id must not be blank");
        }
    }

    public static AudioMasterBus master() {
        return new AudioMasterBus(DEFAULT_MASTER_BUS_ID);
    }

    public static AudioMasterBus of(String busId) {
        return new AudioMasterBus(busId);
    }

    @Override
    public String toString() {
        return busId;
    }
}
