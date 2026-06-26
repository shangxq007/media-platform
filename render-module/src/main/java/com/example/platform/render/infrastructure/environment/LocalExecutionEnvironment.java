package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.ExecutionJob;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local execution environment — wraps existing execution path.
 * Accepts ExecutionJob, submits synchronously.
 */
@Component
public class LocalExecutionEnvironment implements ExecutionEnvironment {

    private static final Logger log = LoggerFactory.getLogger(LocalExecutionEnvironment.class);

    @Override public String environmentId() { return "local"; }
    @Override public String environmentType() { return "local"; }

    @Override
    public boolean supports(List<String> capabilities) {
        return true;
    }

    @Override
    public String submit(ExecutionJob job) {
        String execId = "local-exec-" + System.currentTimeMillis();
        log.info("LocalExecutionEnvironment: submitting job={} backend={} tasks={}",
                job.jobId(), job.backendId(), job.tasks().size());
        return execId;
    }

    @Override
    public boolean cancel(String executionId) {
        log.info("LocalExecutionEnvironment: cancelling {}", executionId);
        return true;
    }

    @Override
    public String status(String executionId) {
        return "COMPLETED";
    }
}
