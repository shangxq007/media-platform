package com.example.platform.compositeresource.domain;

import java.util.Optional;

public final class CompositeReferenceValidationException extends IllegalArgumentException {
    private final CompositeReferenceErrorCode code;
    private final Optional<CompositeReferenceCycle> cycle;

    CompositeReferenceValidationException(
            CompositeReferenceErrorCode code,
            String message,
            CompositeReferenceCycle cycle) {
        super(message);
        this.code = code;
        this.cycle = Optional.ofNullable(cycle);
    }

    public CompositeReferenceErrorCode code() {
        return code;
    }

    public Optional<CompositeReferenceCycle> cycle() {
        return cycle;
    }
}
