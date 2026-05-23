package com.example.platform.notification.domain;

public enum NotificationTemplateCode {
    RENDER_FINISHED, RENDER_CREATED, RENDER_COMPLETED, RENDER_FAILED,
    RENDER_AI_PROCESSING, RENDER_RENDERING, RENDER_CACHE_HASH_INVALIDATED,
    ARTIFACT_CREATED, GENERIC_EVENT;

    public static NotificationTemplateCode fromEventType(String eventType) {
        return switch (eventType) {
            case "render.job.finished" -> RENDER_FINISHED;
            case "render.job.created" -> RENDER_CREATED;
            case "render.job.completed" -> RENDER_COMPLETED;
            case "render.job.failed" -> RENDER_FAILED;
            case "render.job.ai_processing" -> RENDER_AI_PROCESSING;
            case "render.job.rendering" -> RENDER_RENDERING;
            case "render.cache.hash_invalidated" -> RENDER_CACHE_HASH_INVALIDATED;
            case "artifact.created" -> ARTIFACT_CREATED;
            default -> GENERIC_EVENT;
        };
    }
}
