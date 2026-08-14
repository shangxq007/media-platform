package com.example.platform.audio.domain.mix;

/**
 * AUDIO_V2 (A5): explicit mute semantic.
 *
 * <p>Mute is an independent boolean semantic. {@code mute = true} is NOT the
 * same canonical semantic as {@code gain = 0}: a provider may compile mute to
 * zero gain at execution time, but the canonical revision content keeps the
 * two semantics distinct. Default {@code false}.
 */
public record AudioMute(boolean muted) {

    public static final AudioMute UNMUTED = new AudioMute(false);

    public static AudioMute of(boolean muted) {
        return new AudioMute(muted);
    }

    public static AudioMute mutedState() {
        return new AudioMute(true);
    }

    public static AudioMute defaultMute() {
        return UNMUTED;
    }

    @Override
    public String toString() {
        return Boolean.toString(muted);
    }
}
