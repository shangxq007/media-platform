package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.semantics.effect.VideoTransform;
import java.util.Objects;

/** Provider-neutral physical projection of the canonical VideoTransform. */
public record VideoTransformSpec(
        double positionX,
        double positionY,
        double scaleX,
        double scaleY,
        double rotationDegrees,
        double cropLeft,
        double cropRight,
        double cropTop,
        double cropBottom,
        double opacity) {

    public static final VideoTransformSpec IDENTITY = new VideoTransformSpec(
            0, 0, 1, 1, 0, 0, 1, 0, 1, 1);

    public VideoTransformSpec {
        if (Double.isNaN(positionX) || Double.isInfinite(positionX)
                || Double.isNaN(positionY) || Double.isInfinite(positionY)
                || Double.isNaN(scaleX) || Double.isInfinite(scaleX)
                || Double.isNaN(scaleY) || Double.isInfinite(scaleY)
                || Double.isNaN(rotationDegrees) || Double.isInfinite(rotationDegrees)
                || Double.isNaN(opacity) || Double.isInfinite(opacity)) {
            throw new IllegalArgumentException("video transform values must be finite");
        }
        if (scaleX <= 0 || scaleY <= 0 || scaleX > 10 || scaleY > 10) {
            throw new IllegalArgumentException("scale must be in (0,10]");
        }
        if (cropLeft < 0 || cropRight > 1 || cropTop < 0 || cropBottom > 1
                || cropLeft >= cropRight || cropTop >= cropBottom) {
            throw new IllegalArgumentException("crop must be a non-empty normalized rectangle");
        }
        if (opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("opacity must be in [0,1]");
        }
    }

    public static VideoTransformSpec from(VideoTransform transform) {
        Objects.requireNonNull(transform, "transform");
        return new VideoTransformSpec(transform.positionX(), transform.positionY(),
                transform.scaleX(), transform.scaleY(), transform.rotationDegrees(),
                transform.cropLeft(), transform.cropRight(), transform.cropTop(),
                transform.cropBottom(), transform.opacity());
    }
}
