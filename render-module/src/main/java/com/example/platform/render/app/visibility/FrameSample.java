package com.example.platform.render.app.visibility;

import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/** One requested output-frame sample mapped into source time. */
public record FrameSample(int outputFrameIndex, MediaTime outputTime, MediaTime sourceTime) {

    public FrameSample {
        if (outputFrameIndex < 0) throw new IllegalArgumentException("outputFrameIndex must be >= 0");
        Objects.requireNonNull(outputTime, "outputTime");
        Objects.requireNonNull(sourceTime, "sourceTime");
    }
}
