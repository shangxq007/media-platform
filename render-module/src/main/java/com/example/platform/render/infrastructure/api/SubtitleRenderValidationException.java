package com.example.platform.render.infrastructure.api;

public class SubtitleRenderValidationException extends RuntimeException {
    private final PublicApiError error;

    public SubtitleRenderValidationException(PublicApiError error) {
        super(error.message());
        this.error = error;
    }

    public PublicApiError getError() {
        return error;
    }
}
