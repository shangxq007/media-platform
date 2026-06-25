package com.example.platform.outbox.app;

import com.example.platform.outbox.domain.TaskCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mock PROBE handler — verifies the coordination runtime works.
 * Replaced by real FFprobe integration in production.
 */
@Component
public class MockProbeTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(MockProbeTaskHandler.class);

    @Override
    public TaskCapability capability() {
        return TaskCapability.PROBE;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        log.info("MockProbeTaskHandler: processing probe for task={} job={}",
                context.taskId(), context.jobId());
    }
}
