package com.example.platform.workflow.temporal;

/**
 * Shared Temporal task queue for media render workflows; align with {@code spring.temporal.workers} when set explicitly.
 */
public final class RenderTaskQueue {

    public static final String NAME = "media-platform-tasks";

    private RenderTaskQueue() {}
}
