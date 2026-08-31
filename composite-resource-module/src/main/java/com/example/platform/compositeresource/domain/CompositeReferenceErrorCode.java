package com.example.platform.compositeresource.domain;

public enum CompositeReferenceErrorCode {
    CYCLE_DETECTED,
    INCOMPLETE_REFERENCE_CLOSURE,
    EXACT_PIN_DIGEST_MISMATCH,
    DUPLICATE_NODE_IDENTITY
}
