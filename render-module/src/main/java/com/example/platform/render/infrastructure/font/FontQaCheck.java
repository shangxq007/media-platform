package com.example.platform.render.infrastructure.font;

public record FontQaCheck(
        String name,
        FontQaSeverity severity,
        boolean passed,
        String message
) {}
