package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.canonical.CompositionAspectRatio;

/** Requested output raster. Even dimensions support the bounded R1 4:2:0 output. */
public record RenderRaster(int width, int height) {
    public static final int MIN_DIMENSION = 16;
    public static final int MAX_DIMENSION = 1920;
    public static final int MAX_PIXELS = 2_073_600;

    public static final RenderRaster DEFAULT = new RenderRaster(320, 240);

    public RenderRaster {
        if (!admitted(width, height)) {
            throw new IllegalArgumentException("Output requires even dimensions from 16 to 1920 and at most 2073600 pixels");
        }
    }

    /** Checks the smallest exact aligned raster, without changing the authored ratio. */
    static boolean supportsAspect(CompositionAspectRatio ratio) {
        long minimumComponent = Math.min(ratio.numerator(), ratio.denominator());
        long scale = (MIN_DIMENSION + minimumComponent - 1) / minimumComponent;
        if (((ratio.numerator() | ratio.denominator()) & 1) != 0 && (scale & 1) != 0) {
            scale++;
        }
        return admitted(ratio.numerator() * scale, ratio.denominator() * scale);
    }

    private static boolean admitted(long width, long height) {
        return width >= MIN_DIMENSION && height >= MIN_DIMENSION
                && width <= MAX_DIMENSION && height <= MAX_DIMENSION
                && (width & 1) == 0 && (height & 1) == 0 && width * height <= MAX_PIXELS;
    }
}
