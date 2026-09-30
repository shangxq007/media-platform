package com.example.platform.shared.capability;

import java.util.Objects;
import java.util.Optional;

/**
 * Requested raster image encoding for the extracted frame.
 *
 * <p>{@link Png} carries no quality parameter. {@link Jpeg} may carry an
 * explicit {@link JpegQuality}; when absent the meaning is the documented
 * contract default (materialized and pinned at resolve, never a hidden provider
 * default).</p>
 */
public sealed interface RasterImageEncoding permits RasterImageEncoding.Png, RasterImageEncoding.Jpeg {

    /** PNG encoding (no quality parameter). */
    record Png() implements RasterImageEncoding {
        /** Shared instance (immutable, no state). */
        public static final Png PNG = new Png();
    }

    /** JPEG encoding with an optional explicit quality. */
    record Jpeg(Optional<JpegQuality> quality) implements RasterImageEncoding {

        public Jpeg {
            Objects.requireNonNull(quality, "quality");
        }

        /** JPEG with an explicit quality value. */
        public Jpeg(JpegQuality quality) {
            this(Optional.of(Objects.requireNonNull(quality, "quality")));
        }

        /** JPEG with the documented contract default quality (to be pinned at resolve). */
        public static Jpeg documentedDefault() {
            return new Jpeg(Optional.empty());
        }

        /** True when an explicit quality value is present. */
        public boolean hasExplicitQuality() {
            return quality.isPresent();
        }
    }
}
