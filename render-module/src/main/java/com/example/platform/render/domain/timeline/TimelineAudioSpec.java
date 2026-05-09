package com.example.platform.render.domain.timeline;

/**
 * Audio output specification for a timeline.
 *
 * @param codec        audio codec (e.g., "aac", "mp3")
 * @param sampleRate   sample rate in Hz (e.g., 48000)
 * @param channels     number of audio channels (1 = mono, 2 = stereo)
 * @param bitrateKbps  audio bitrate in kbps
 * @param volume       volume multiplier (1.0 = normal)
 * @param normalize    whether to normalize audio levels
 */
public record TimelineAudioSpec(
        String codec,
        int sampleRate,
        int channels,
        int bitrateKbps,
        double volume,
        boolean normalize) {

    /**
     * Default AAC audio spec: 48kHz, stereo, 128kbps.
     */
    public static TimelineAudioSpec aacDefault() {
        return new TimelineAudioSpec("aac", 48000, 2, 128, 1.0, false);
    }

    /**
     * Creates a custom audio spec.
     */
    public static TimelineAudioSpec of(String codec, int sampleRate, int channels, int bitrateKbps) {
        return new TimelineAudioSpec(codec, sampleRate, channels, bitrateKbps, 1.0, false);
    }
}
