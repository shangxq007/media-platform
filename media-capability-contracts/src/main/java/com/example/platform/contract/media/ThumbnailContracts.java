package com.example.platform.contract.media;

import com.example.platform.shared.capability.FrameWidth;
import com.example.platform.shared.capability.JpegQuality;
import com.example.platform.shared.capability.MediaFrameExtractParametersV1;
import com.example.platform.shared.capability.RasterImageEncoding;
import java.math.BigDecimal;
import java.util.Locale;

/** Provider-neutral media request intent. Its output is committed by the canonical Artifact owner;
 * provider choice and execution state are deliberately absent from this transport vocabulary. */
public final class ThumbnailContracts {
    public static final String CAPABILITY = "media.thumbnail";

    /** Capability contract version declared alongside {@link #CAPABILITY}. */
    public static final String CAPABILITY_VERSION = "1.0";

    /** Provenance operation label; not a second execution or Operation authority. */
    public static final String OPERATION_ID = "thumbnail";

    private ThumbnailContracts() {}

    static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    /**
     * Immutable thumbnail <em>transport</em> request.
     *
     * <p>Transport DTO: scope + target + wire-shaped parameter fields, plus the typed
     * {@link #capabilityParameters()} authority. The typed parameter is the shared
     * {@link MediaFrameExtractParametersV1} ({@code shared.capability}), identical in shape to the
     * cover request's, so the slice no longer owns a second parameter vocabulary. Field names and
     * ranges stay unchanged (non-breaking HTTP).
     */
    public record Request(String tenantId, String projectId, String sourceAssetId,
            double timestampSeconds, String imageFormat, Integer width, Integer quality,
            String idempotencyKey) {
        public Request {
            require(tenantId, "tenantId"); require(projectId, "projectId");
            require(sourceAssetId, "sourceAssetId"); require(idempotencyKey, "idempotencyKey");
            if (!Double.isFinite(timestampSeconds) || timestampSeconds < 0) throw new IllegalArgumentException("timestamp must be finite and non-negative");
            imageFormat = imageFormat == null ? "jpeg" : imageFormat.toLowerCase(Locale.ROOT);
            if (!imageFormat.equals("jpeg") && !imageFormat.equals("png")) throw new IllegalArgumentException("unsupported image format");
            if (width != null && (width < 16 || width > 4096)) throw new IllegalArgumentException("unsupported thumbnail width");
            if (quality != null && (quality < 1 || quality > 100)) throw new IllegalArgumentException("unsupported thumbnail quality");
        }

        /**
         * Typed parameter authority for this request (shared {@code media.frame-extract} vocabulary).
         *
         * <p>Same construction as the cover request: the transport {@code double} decodes through the
         * shortest round-trip decimal string and the exact {@code decimal -> rational} rule, never
         * through a floating time authority.
         */
        public MediaFrameExtractParametersV1 capabilityParameters() {
            return MediaFrameExtractParametersV1.ofExactSeconds(
                    BigDecimal.valueOf(timestampSeconds).toPlainString(),
                    encoding(),
                    width == null ? FrameWidth.Native.NATIVE : new FrameWidth.ExplicitPixels(width));
        }

        private RasterImageEncoding encoding() {
            if ("png".equals(imageFormat)) {
                return RasterImageEncoding.Png.PNG;
            }
            return quality == null
                    ? RasterImageEncoding.Jpeg.documentedDefault()
                    : new RasterImageEncoding.Jpeg(new JpegQuality(quality));
        }

        private static void require(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required"); }
    }

}
