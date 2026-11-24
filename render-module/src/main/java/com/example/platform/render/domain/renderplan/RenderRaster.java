package com.example.platform.render.domain.renderplan;

/** Requested output raster. Even dimensions support the bounded R1 4:2:0 output. */
public record RenderRaster(int width, int height) {
    public static final RenderRaster DEFAULT = new RenderRaster(320, 240);

    public RenderRaster {
        if (width < 16 || height < 16 || width > 1920 || height > 1920
                || (width & 1) != 0 || (height & 1) != 0 || (long) width * height > 2_073_600) {
            throw new IllegalArgumentException("Output requires even dimensions from 16 to 1920 and at most 2073600 pixels");
        }
    }
}
