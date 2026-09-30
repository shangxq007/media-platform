package com.example.platform.shared.capability;

/**
 * Requested output width for a raster frame (capability domain floor only).
 *
 * <p>{@link Native} means the source frame's native width. {@link ExplicitPixels}
 * means scale to the requested width with the aspect ratio preserved; it does
 * NOT mean crop. The capability defines the floor {@code px >= 16} and no
 * provider-specific universal maximum; bounded maxima are supplied by operation
 * policy and by the selected implementation support envelope.</p>
 */
public sealed interface FrameWidth permits FrameWidth.Native, FrameWidth.ExplicitPixels {

    /** Source native width. */
    record Native() implements FrameWidth {
        /** Shared instance (immutable, no state). */
        public static final Native NATIVE = new Native();
    }

    /** Scale to an explicit pixel width (aspect ratio preserved, never crop). */
    record ExplicitPixels(int pixels) implements FrameWidth {

        /** Capability domain floor (no universal maximum). */
        public static final int MINIMUM_PIXELS = 16;

        public ExplicitPixels {
            if (pixels < MINIMUM_PIXELS) {
                throw new IllegalArgumentException(
                        "frame width must be >= " + MINIMUM_PIXELS + " px: " + pixels);
            }
        }
    }
}
