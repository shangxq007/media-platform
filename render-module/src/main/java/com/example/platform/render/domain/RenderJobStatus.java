package com.example.platform.render.domain;

public enum RenderJobStatus {
    QUEUED,
    AI_PROCESSING,
    RENDERING,
    COMPLETED,
    FAILED,
    CANCELLED,
    REJECTED
}
