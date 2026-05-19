package com.example.platform.shared.web;

public enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST("COMMON-400-001", "Invalid request", 400),
    RESOURCE_NOT_FOUND("COMMON-404-001", "Resource not found", 404),
    CONFLICT("COMMON-409-001", "Conflict", 409),
    INTERNAL_ERROR("COMMON-500-001", "Internal error", 500),
    INTEGRATION_ERROR("COMMON-502-001", "Integration error", 502),
    AUTHENTICATION_REQUIRED("COMMON-401-001", "Authentication required", 401),
    INSUFFICIENT_PERMISSION("COMMON-403-001", "Insufficient permission", 403),
    RATE_LIMIT_EXCEEDED("SECURITY-429-001", "Rate limit exceeded", 429);

    private final String code;
    private final String title;
    private final int status;

    CommonErrorCode(String code, String title, int status) {
        this.code = code;
        this.title = title;
        this.status = status;
    }

    @Override
    public String code() { return code; }
    @Override
    public String title() { return title; }
    @Override
    public int status() { return status; }
}
