package com.example.platform.render.domain.timeline;

/**
 * Audio output specification for a timeline (OUTPUT/EXECUTION projection).
 *
 * <p>AUDIO_V2 (A16): legacy {@code volume} and {@code normalize} fields are
 * RETIRED — they carried undefined mix semantics with no real consumers.
 * Canonical gain/mute semantics now live in the Audio V2 mix authority
 * ({@code audio-module}); this record keeps only the execution-oriented
 * output encoding facts (codec/rate/channels/bitrate).
 *
 * @param codec        audio codec (e.g., "aac", "mp3")
 * @param sampleRate   sample rate in Hz (e.g., 48000)
 * @param channels     number of audio channels (1 = mono, 2 = stereo)
 * @param bitrateKbps  audio bitrate in kbps
 */
public record TimelineAudioSpec(
        String codec,
        int sampleRate,
        int channels,
        int bitrateKbps) {

    /**
     * Default AAC audio spec: 48kHz, stereo, 128kbps.
     */
    public static TimelineAudioSpec aacDefault() {
        return new TimelineAudioSpec("aac", 48000, 2, 128);
    }

    /**
     * Creates a custom audio spec.
     */
    public static TimelineAudioSpec of(String codec, int sampleRate, int channels, int bitrateKbps) {
        return new TimelineAudioSpec(codec, sampleRate, channels, bitrateKbps);
    }
}
