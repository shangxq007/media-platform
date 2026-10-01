package com.example.platform.execution.binding;

import java.util.Objects;

/**
 * Fail-closed carrier for a re-derived bound graph that does not reproduce the
 * expected semantic digest — the durable record and the current binding inputs
 * disagree, so the work must not be executed.
 */
public class BoundGraphDigestMismatchException extends RuntimeException {

    private final String expectedDigest;
    private final String actualDigest;

    public BoundGraphDigestMismatchException(String expectedDigest, String actualDigest) {
        super("bound graph digest mismatch: expected " + expectedDigest + " but re-derived " + actualDigest);
        this.expectedDigest = Objects.requireNonNull(expectedDigest, "expectedDigest");
        this.actualDigest = Objects.requireNonNull(actualDigest, "actualDigest");
    }

    public String expectedDigest() {
        return expectedDigest;
    }

    public String actualDigest() {
        return actualDigest;
    }
}
