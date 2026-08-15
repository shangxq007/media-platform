package com.example.platform.colorimage;

import java.util.Objects;

/**
 * ROADMAP_18 (CI15/CI17): exact encoded raster storage extent. width/height
 * positive bounded integers. Source storage geometry; Timeline presentation
 * geometry is separate.
 */
public record EncodedRasterExtent(int width, int height) {

    public static final int MAX_DIMENSION = 1_000_000;

    public EncodedRasterExtent {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("raster extent dimensions must be positive");
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IllegalArgumentException("raster extent exceeds bounded domain");
        }
    }
}
