package com.example.platform.render.app.output;

/**
 * Thrown when render output registration fails.
 *
 * <p>{@code productRegistered} indicates whether a Product was already
 * registered before the failure (e.g., storage checksum failure after
 * Product registration). If true, the Product should be marked FAILED.
 */
public class RenderOutputRegistrationException extends RuntimeException {

    private final String jobId;
    private final boolean productRegistered;

    public RenderOutputRegistrationException(String jobId, String message, boolean productRegistered) {
        super(message);
        this.jobId = jobId;
        this.productRegistered = productRegistered;
    }

    public String jobId() {
        return jobId;
    }

    public boolean isProductRegistered() {
        return productRegistered;
    }
}
