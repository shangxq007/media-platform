package com.example.platform.render.domain.renderplan;

import com.example.platform.colorimage.ColorDescription;
import com.example.platform.colorimage.RasterSampleDescription;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed render output requirement (C14/C15). References color-image-module value
 * types (ColorDescription, RasterSampleDescription) without redefining them.
 *
 * @param role            output role
 * @param colorDescription optional typed color description
 * @param rasterSample     optional typed raster sample description
 */
public record RenderOutputRequirement(
        RenderOutputRole role,
        Optional<ColorDescription> colorDescription,
        Optional<RasterSampleDescription> rasterSample) {

    public RenderOutputRequirement {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(colorDescription, "colorDescription");
        Objects.requireNonNull(rasterSample, "rasterSample");
    }

    public static RenderOutputRequirement of(RenderOutputRole role) {
        return new RenderOutputRequirement(role, Optional.empty(), Optional.empty());
    }
}
