package com.example.platform.shared.capability;

import com.example.platform.shared.time.MediaTime;
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
}
