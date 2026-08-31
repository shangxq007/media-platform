package com.example.platform.render.app.operation;

/**
 * Application-boundary command for the H7 media-clip operation.
 *
 * <p>Media identities remain validated transport values here. The render
 * application service resolves them to the Media-owned identity types while
 * assembling the canonical operation, so HTTP adapters do not depend on the
 * Media module or mint a second source-identity model.
 */
public record AddMediaClipCommand(
        String baseRevisionId,
        String baseContentHash,
        String trackId,
        String clipId,
        String mediaAssetId,
        String mediaStreamId,
        String artifactId,
        String contentDigest,
        String sourceStart,
        String sourceEnd,
        String timelineStart,
        String timelineEnd,
        long rateNumerator,
        long rateDenominator,
        Direction direction) {

    public AddMediaClipCommand {
        requireText(baseRevisionId, "baseRevisionId");
        requireText(baseContentHash, "baseContentHash");
        requireText(trackId, "trackId");
        requireText(clipId, "clipId");
        requireText(mediaAssetId, "mediaAssetId");
        requireText(mediaStreamId, "mediaStreamId");
        requireText(artifactId, "artifactId");
        requireText(contentDigest, "contentDigest");
        requireText(sourceStart, "sourceStart");
        requireText(sourceEnd, "sourceEnd");
        requireText(timelineStart, "timelineStart");
        requireText(timelineEnd, "timelineEnd");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    /** Render application input vocabulary; canonical Timeline conversion stays behind the service. */
    public enum Direction {
        FORWARD,
        REVERSE
    }
}
