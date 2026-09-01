package com.example.platform.compositeresource.domain;

public final class CompositeResourceDiffException extends IllegalArgumentException {
    private final CompositeResourceDiffErrorCode code;

    CompositeResourceDiffException(CompositeResourceDiffErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public CompositeResourceDiffErrorCode code() {
        return code;
    }
}
