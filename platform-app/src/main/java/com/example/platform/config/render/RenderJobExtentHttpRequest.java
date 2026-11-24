package com.example.platform.config.render;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * HTTP extent shape for a formal RenderJob submission.
 *
 * <p>{@code startMillis}/{@code endMillis} are the requested half-open range start (inclusive) and
 * end (exclusive) in milliseconds; {@code frameRateNumerator}/{@code frameRateDenominator} express
 * the exact rational frame rate. The controller validates {@code start < end}.
 */
public record RenderJobExtentHttpRequest(
        @NotNull @PositiveOrZero Long startMillis,
        @NotNull @PositiveOrZero Long endMillis,
        @NotNull @Positive Integer frameRateNumerator,
        @NotNull @Positive Integer frameRateDenominator,
        @Positive Integer width,
        @Positive Integer height) {

    public RenderJobExtentHttpRequest(Long startMillis, Long endMillis,
            Integer frameRateNumerator, Integer frameRateDenominator) {
        this(startMillis, endMillis, frameRateNumerator, frameRateDenominator, null, null);
    }
    public com.example.platform.render.domain.renderplan.RenderExtent toExtent() {
        if ((width == null) != (height == null)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Output width and height must be supplied together");
        }
        com.example.platform.render.domain.renderplan.RenderRaster raster;
        try {
            raster = width == null ? com.example.platform.render.domain.renderplan.RenderRaster.DEFAULT
                    : new com.example.platform.render.domain.renderplan.RenderRaster(width, height);
        } catch (IllegalArgumentException invalid) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
        var extent=new com.example.platform.render.domain.renderplan.RenderExtent(
                com.example.platform.shared.time.MediaTime.ofMillis(startMillis),
                com.example.platform.shared.time.MediaTime.ofMillis(endMillis),
                com.example.platform.shared.time.FrameRate.of(frameRateNumerator,frameRateDenominator), raster);
        if(!extent.isValid())throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"extent start must be before end");
        return extent;
    }
}
