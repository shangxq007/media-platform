package com.example.platform.social.infrastructure.platform;

public record PublishResult(
        boolean success,
        String platformPostId,
        String platformPostUrl,
        String errorCode,
        String errorMessage
) {}
