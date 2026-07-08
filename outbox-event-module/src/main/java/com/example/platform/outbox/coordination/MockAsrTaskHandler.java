package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.TaskCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mock ASR handler — verifies the coordination runtime works.
 * Replaced by real Whisper integration in production.
 */
@Component
public class MockAsrTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(MockAsrTaskHandler.class);

    @Override
    public TaskCapability capability() {
        return TaskCapability.ASR;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        log.info("MockAsrTaskHandler: processing ASR for task={} job={}",
                context.taskId(), context.jobId());
    }
}
