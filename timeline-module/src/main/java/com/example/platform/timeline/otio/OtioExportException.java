package com.example.platform.timeline.otio;

/** Explicit failure for a canonical Timeline semantic that this MVP cannot represent losslessly. */
public final class OtioExportException extends RuntimeException {

    public enum Code {
        UNSUPPORTED_SEMANTIC,
        INVALID_TIMELINE
    }

    private final Code code;

    public OtioExportException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
