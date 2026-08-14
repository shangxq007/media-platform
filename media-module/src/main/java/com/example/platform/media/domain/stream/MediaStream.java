package com.example.platform.media.domain.stream;

import com.example.platform.media.domain.description.SourceAudioDescription;
import com.example.platform.media.domain.description.SourceColorDescription;
import com.example.platform.media.domain.description.SourceVideoDescription;
import com.example.platform.media.domain.time.TimeBase;
import com.example.platform.shared.time.FrameRate;
import java.io.Serializable;

/**
 * Canonical source stream structural description
 * (SOURCE_STREAM_IDENTITY_AUTHORITY_V1 / F3 contract).
 *
 * <p>A MediaStream describes ONE source container stream: its identity,
 * kind, normalized codec, exact timebase, exact nominal rate, and typed
 * video/audio/color description. VFR is expressed as nominal rational rate +
 * isVfr marker (+ optional per-frame timing reference); never double fps.
 * Unknown/unavailable values are absent (null), never sentinel doubles.
 */
public record MediaStream(
        MediaStreamId id,
        int streamIndex,
        StreamKind kind,
        String codec,
        TimeBase timeBase,
        FrameRate nominalFrameRate,
        boolean isVfr,
        SourceVideoDescription video,
        SourceAudioDescription audio,
        SourceColorDescription color,
        String containerStreamDescription) implements Serializable {

    public MediaStream {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (streamIndex < 0) {
            throw new IllegalArgumentException("streamIndex must be >= 0");
        }
        if (timeBase == null) {
            throw new IllegalArgumentException("timeBase must not be null");
        }
    }
}
