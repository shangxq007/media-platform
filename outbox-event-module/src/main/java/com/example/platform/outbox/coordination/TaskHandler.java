package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.TaskCapability;

/**
 * SPI for platform task handlers.
 *
 * <p>Each handler is associated with a {@link TaskCapability}. The dispatcher
 * resolves the handler by capability and calls {@link #execute(TaskExecutionContext)}.</p>
 */
public interface TaskHandler {

    /**
     * The capability this handler serves.
     */
    TaskCapability capability();

    /**
     * Execute the task. The handler is responsible for its own error handling.
     * Throw RuntimeException to signal a permanent failure.
     */
    void execute(TaskExecutionContext context);
}
