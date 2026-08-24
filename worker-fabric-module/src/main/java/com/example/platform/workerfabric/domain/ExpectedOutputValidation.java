package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Typed validation result against the expected output declarations of an executable task. */
public record ExpectedOutputValidation(String validationReference, Status status) {

    public ExpectedOutputValidation {
        Objects.requireNonNull(validationReference, "validationReference");
        Objects.requireNonNull(status, "status");
        if (validationReference.isBlank()) {
            throw new IllegalArgumentException("validationReference must not be blank");
        }
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    public enum Status {
        VALID,
        INVALID
    }
}
