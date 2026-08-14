package com.example.platform.media.domain.description;

import java.io.Serializable;

/**
 * Source audio technical description (F3 contract).
 *
 * <p>This is SOURCE description only (sample rate, channels, layout, sample
 * format, bit depth). It is NOT audio mix / DSP composition authority — that
 * belongs to #15 Audio V2.
 */
public record SourceAudioDescription(
        Integer sampleRate,
        Integer channels,
        String channelLayout,
        String sampleFormat,
        Integer bitDepth) implements Serializable {

    public SourceAudioDescription {
        if (sampleRate != null && sampleRate <= 0) {
            throw new IllegalArgumentException("sampleRate must be > 0 when present");
        }
        if (channels != null && channels <= 0) {
            throw new IllegalArgumentException("channels must be > 0 when present");
        }
    }
}
