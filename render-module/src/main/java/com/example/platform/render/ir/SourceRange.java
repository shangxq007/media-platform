package com.example.platform.render.ir;
import com.example.platform.shared.time.RationalTime;

import java.util.Objects;

/**
 * A range within a source asset, identified by start time and duration.
 */
public record SourceRange(AssetVersionRef assetRef, RationalTime start, RationalTime duration) {
    public SourceRange {
        Objects.requireNonNull(assetRef, "assetRef must not be null");
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
    }
}
