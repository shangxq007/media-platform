package com.example.platform.render.domain.timeline.version;

/**
 * Exception thrown when optimistic concurrency check fails.
 */
public class TimelineConflictException extends RuntimeException {

    private final String productId;
    private final String expectedRevisionId;
    private final String actualRevisionId;

    public TimelineConflictException(String productId, String expectedRevisionId, String actualRevisionId) {
        super(String.format("Timeline revision conflict for product=%s: expected=%s, actual=%s",
                productId, expectedRevisionId, actualRevisionId));
        this.productId = productId;
        this.expectedRevisionId = expectedRevisionId;
        this.actualRevisionId = actualRevisionId;
    }

    public String getProductId() { return productId; }
    public String getExpectedRevisionId() { return expectedRevisionId; }
    public String getActualRevisionId() { return actualRevisionId; }

    public static final String ERROR_CODE = "TIMELINE_REVISION_CONFLICT";
}
