package com.example.platform.billing.usage;

/**
 * Canonical usage dimensions (frozen vocabulary).
 *
 * <p>Each dimension binds to one or more canonical {@link UsageUnit}s; an illegal
 * dimension/unit pairing is rejected by {@link UsageUnit#validate(UsageDimension, UsageUnit)}.
 * </p>
 *
 * <p>The DEFER items (GPU_DURATION, RENDER_FRAME, GENERATED_IMAGE,
 * GENERATED_VIDEO_SECOND) MUST NOT be added as core authority values.</p>
 */
public enum UsageDimension {

    REQUEST,
    DURATION,
    BYTE_STORED,
    BYTE_READ,
    BYTE_WRITTEN,
    BYTE_EGRESS,
    TOKEN_INPUT,
    TOKEN_OUTPUT,
    DELIVERY_BYTE
}
