package com.example.platform.compatibility.domain;

public record MigrationError(
        String errorCode,
        String message,
        String stepId,
        boolean recoverable
) {}
