package com.example.platform.render.app.timeline;

import java.util.List;

/**
 * Result of resolving timeline source asset IDs to input Product IDs.
 *
 * <p>Used by {@link TimelineInputProductResolver} to communicate the outcome
 * of resolving source assets from a TimelineSpec into registered READY
 * RAW_MEDIA Products suitable for render input materialization.</p>
 *
 * <p>Fail-closed: any invalid or unmapped source asset produces a failure result.
 * No warning-only path exists for R6.1.</p>
 */
public record TimelineInputProductResolverResult(
        List<String> inputProductIds,
        List<String> sourceAssetIds,
        boolean valid,
        String failureReason) {

    public static TimelineInputProductResolverResult success(
            List<String> inputProductIds, List<String> sourceAssetIds) {
        return new TimelineInputProductResolverResult(
                inputProductIds, sourceAssetIds, true, null);
    }

    public static TimelineInputProductResolverResult failure(
            List<String> sourceAssetIds, String failureReason) {
        return new TimelineInputProductResolverResult(
                List.of(), sourceAssetIds, false, failureReason);
    }
}
