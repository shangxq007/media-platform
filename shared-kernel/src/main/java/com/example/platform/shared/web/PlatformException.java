package com.example.platform.shared.web;

public class PlatformException extends RuntimeException {
    private final ErrorCode errorCode;

    public PlatformException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
