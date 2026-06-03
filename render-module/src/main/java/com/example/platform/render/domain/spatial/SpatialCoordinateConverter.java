package com.example.platform.render.domain.spatial;

/**
 * Converts normalized_ppm (parts per million, 0..1_000_000) coordinates to pixel values.
 *
 * <p>Uses edge-based nearest rounding as defined in Spatial Coordinate System v1:
 * <ul>
 *   <li>left = round(xPpm * width / 1_000_000)</li>
 *   <li>top = round(yPpm * height / 1_000_000)</li>
 *   <li>right = round((xPpm + widthPpm) * width / 1_000_000)</li>
 *   <li>bottom = round((yPpm + heightPpm) * height / 1_000_000)</li>
 *   <li>pixelWidth = max(1, right - left)</li>
 *   <li>pixelHeight = max(1, bottom - top)</li>
 * </ul>
 *
 * <p>Example for 1920x1080 canvas:
 * <pre>
 *   x=250000, y=250000, w=500000, h=500000
 *   left = round(250000/1000000*1920) = round(480.0) = 480
 *   top  = round(250000/1000000*1080) = round(270.0) = 270
 *   right = round(750000/1000000*1920) = round(1440.0) = 1440
 *   bottom = round(750000/1000000*1080) = round(810.0) = 810
 *   pixelWidth = max(1, 1440-480) = 960
 *   pixelHeight = max(1, 810-270) = 540
 * </pre>
 */
public final class SpatialCoordinateConverter {

    private SpatialCoordinateConverter() {}

    /**
     * Round normalized_ppm to nearest pixel value.
     */
    private static int ppmToNearestPixel(double ppm, int canvasDimension) {
        return (int) Math.round(ppm * canvasDimension / 1_000_000.0);
    }

    /**
     * Convert normalized_ppm x to pixel left position (nearest rounding).
     */
    public static int ppmToPixelLeft(double ppm, int canvasWidth) {
        return ppmToNearestPixel(ppm, canvasWidth);
    }

    /**
     * Convert normalized_ppm y to pixel top position (nearest rounding).
     */
    public static int ppmToPixelTop(double ppm, int canvasHeight) {
        return ppmToNearestPixel(ppm, canvasHeight);
    }

    /**
     * Convert a full ppm region to pixel crop parameters for ffmpeg crop filter.
     * Uses edge-based nearest rounding: round each edge independently, then compute size.
     *
     * @return int[]{x, y, width, height} in pixels
     */
    public static int[] ppmRegionToCropFilter(double xPpm, double yPpm, double widthPpm, double heightPpm,
                                               int canvasWidth, int canvasHeight) {
        int left = ppmToNearestPixel(xPpm, canvasWidth);
        int top = ppmToNearestPixel(yPpm, canvasHeight);
        int right = ppmToNearestPixel(xPpm + widthPpm, canvasWidth);
        int bottom = ppmToNearestPixel(yPpm + heightPpm, canvasHeight);
        int pixelWidth = Math.max(1, right - left);
        int pixelHeight = Math.max(1, bottom - top);
        return new int[]{left, top, pixelWidth, pixelHeight};
    }

    /**
     * Convert a ppm position and size to pixel overlay coordinates for ffmpeg overlay filter.
     *
     * @return int[]{x, y, width, height} in pixels
     */
    public static int[] ppmPositionToOverlay(double xPpm, double yPpm, double widthPpm, double heightPpm,
                                              int canvasWidth, int canvasHeight) {
        int x = ppmToNearestPixel(xPpm, canvasWidth);
        int y = ppmToNearestPixel(yPpm, canvasHeight);
        int w = Math.max(1, ppmToNearestPixel(widthPpm, canvasWidth));
        int h = Math.max(1, ppmToNearestPixel(heightPpm, canvasHeight));
        return new int[]{x, y, w, h};
    }

    /**
     * Clamp a pixel value to valid range [0, max].
     */
    public static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }
}
