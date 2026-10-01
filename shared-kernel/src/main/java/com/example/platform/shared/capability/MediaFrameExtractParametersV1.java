package com.example.platform.shared.capability;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.time.ExactDecimalSeconds;
import java.util.Objects;

/**
 * {@code media.frame-extract@1.0} capability parameter value object.
 *
 * <p>Provider-neutral: no provider/plugin/backend/worker identity, no business
 * intent ({@code COVER_OF} / {@code THUMBNAIL} are operation effects).</p>
 *
 * @param position source-domain exact instant (shared-kernel {@link MediaTime});
 *                 a transport {@code timestampSeconds} is converted to an exact
 *                 rational at the boundary, never through {@code double}
 * @param encoding requested raster encoding (PNG, or JPEG with optional quality)
 * @param width    requested width ({@link FrameWidth.Native} or an explicit
 *                 pixel width {@code >= 16})
 */
public record MediaFrameExtractParametersV1(
        MediaTime position,
        RasterImageEncoding encoding,
        FrameWidth width)
        implements CapabilityParameterContract {

    public MediaFrameExtractParametersV1 {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(width, "width");
    }

    /**
     * Transport-adapter factory: decodes an exact decimal-seconds <em>string</em> into the
     * canonical {@link MediaTime} position and assembles the typed parameter value.
     *
     * <p>This is the single supported bridge from a transport time value to this capability's
     * parameter authority. It never uses a {@code double}: the caller must supply the exact
     * decimal string (e.g. from a Jackson {@code BigDecimal}/{@code DecimalNode}), and the
     * conversion goes through {@link ExactDecimalSeconds#toMediaTime(String)} — exactly the
     * {@code decimal -> exact rational} rule the repository already uses at the ingest boundary.
     * Non-finite, negative or non-representable values fail closed.
     *
     * @throws IllegalArgumentException when {@code decimalSeconds} is null/blank/negative,
     *         malformed, or not representable as an exact long-rational {@link MediaTime}
     */
    public static MediaFrameExtractParametersV1 ofExactSeconds(
            String decimalSeconds, RasterImageEncoding encoding, FrameWidth width) {
        return new MediaFrameExtractParametersV1(
                ExactDecimalSeconds.toMediaTime(decimalSeconds), encoding, width);
    }
}
