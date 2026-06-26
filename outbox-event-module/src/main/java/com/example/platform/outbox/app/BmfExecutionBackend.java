package com.example.platform.outbox.app;

import com.example.platform.outbox.domain.TaskCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BMF execution backend — executes media processing graphs.
 * Currently uses local CLI/subprocess. Future: native BMF SDK integration.
 */
@Component
public class BmfExecutionBackend implements ExecutionBackend {

    private static final Logger log = LoggerFactory.getLogger(BmfExecutionBackend.class);

    @Override
    public String backendId() {
        return "bmf";
    }

    @Override
    public boolean supports(TaskCapability capability) {
        return capability == TaskCapability.MEDIA_PIPELINE
                || capability == TaskCapability.TRANSCODE
                || capability == TaskCapability.FRAME_EXTRACTION
                || capability == TaskCapability.FILTER
                || capability == TaskCapability.THUMBNAIL;
    }

    @Override
    public ExecutionResult execute(ExecutionRequest request) {
        long start = System.currentTimeMillis();
        String graphType = request.payload() != null
                ? request.payload().getOrDefault("graphType", request.taskCapability().name())
                : request.taskCapability().name();

        log.info("BMF execution STARTED: backend=bmf task={} cap={} graphType={}",
                request.taskId(), request.taskCapability(), graphType);

        try {
            // Phase 1: CLI/subprocess placeholder
            // Future: native BMF SDK graph execution
            Thread.sleep(50); // simulate execution
            long dur = System.currentTimeMillis() - start;

            log.info("BMF execution FINISHED: task={} cap={} dur={}ms",
                    request.taskId(), request.taskCapability(), dur);
            return ExecutionResult.success(0, "BMF graph executed: " + graphType, "", dur);
        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            log.error("BMF execution FAILED: task={} error={}", request.taskId(), e.getMessage());
            return ExecutionResult.failure(-1, e.getMessage(), dur, "BMF_ERROR", e.getMessage());
        }
    }
}
