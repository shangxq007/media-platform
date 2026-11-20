package com.example.platform.timeline.semantics.effect;

import java.util.Objects;

/**
 * Canonical static video transform for one clip.
 *
 * <p>Coordinates are output-canvas pixels (the current 320x240 output canvas);
 * scale is relative to the output canvas; rotation is clockwise degrees around
 * the transformed frame centre; crop ratios select a source rectangle; opacity
 * is applied after position/rotation. Order is Crop -> Scale -> Rotate ->
 * Position -> Opacity. Defaults preserve the pre-transform layout.</p>
 */
public record VideoTransform(
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

    public static final VideoTransform IDENTITY = new VideoTransform(
            0, 0, 1, 1, 0, 0, 1, 0, 1, 1);

    public VideoTransform {
        requireFinite(positionX, "positionX");
        requireFinite(positionY, "positionY");
        requireFinite(scaleX, "scaleX");
        requireFinite(scaleY, "scaleY");
        requireFinite(rotationDegrees, "rotationDegrees");
        requireFinite(cropLeft, "cropLeft");
        requireFinite(cropRight, "cropRight");
        requireFinite(cropTop, "cropTop");
        requireFinite(cropBottom, "cropBottom");
        requireFinite(opacity, "opacity");
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

    private static void requireFinite(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    public VideoTransform withCrop(double left, double right, double top, double bottom) {
        return new VideoTransform(positionX, positionY, scaleX, scaleY, rotationDegrees,
                left, right, top, bottom, opacity);
    }

    public double cropWidth() { return cropRight - cropLeft; }
    public double cropHeight() { return cropBottom - cropTop; }

    @Override
    public String toString() {
        return "VideoTransform[position=" + positionX + "," + positionY
                + " scale=" + scaleX + "," + scaleY
                + " rotation=" + rotationDegrees
                + " crop=" + cropLeft + "," + cropRight + "," + cropTop + "," + cropBottom
                + " opacity=" + opacity + "]";
    }
}
