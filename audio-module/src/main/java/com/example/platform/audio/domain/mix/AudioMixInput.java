package com.example.platform.audio.domain.mix;

import java.util.Objects;

/**
 * AUDIO_V2 (A7): typed routing reference to a Timeline clip's audio
 * contribution. Identifies the composition entity (clip on a track) that feeds
 * an audio route. audio-module must not depend on render/timeline, so the
 * reference is a typed value pair (clipId, trackId) assembled by the Timeline
 * side; it is NOT a bare provider label or FFmpeg node name.
 */
public record AudioMixInput(
        String trackId,
        String clipId) {

    public AudioMixInput {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(clipId, "clipId");
        if (trackId.isBlank() || clipId.isBlank()) {
            throw new IllegalArgumentException("AudioMixInput ids must not be blank");
        }
    }

    public static AudioMixInput of(String trackId, String clipId) {
        return new AudioMixInput(trackId, clipId);
    }

    @Override
    public String toString() {
        return trackId + "/" + clipId;
    }
}
