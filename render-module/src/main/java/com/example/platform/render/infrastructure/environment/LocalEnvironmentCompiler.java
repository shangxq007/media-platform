package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.BackendExecutionSpec;
import com.example.platform.render.domain.execution.ExecutionJob;
import com.example.platform.render.domain.execution.ExecutionTask;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local environment compiler — translates BackendExecutionSpec → ExecutionJob.
 * Creates single-task jobs with no retry for local execution.
 */
@Component
public class LocalEnvironmentCompiler implements EnvironmentCompiler {

    private static final Logger log = LoggerFactory.getLogger(LocalEnvironmentCompiler.class);

    @Override public String environmentType() { return "local"; }

    @Override
    public boolean supports(String environmentType) {
        return "local".equals(environmentType);
    }

    @Override
    public ExecutionJob compile(BackendExecutionSpec backendSpec) {
        log.info("LocalEnvironmentCompiler: compiling backendSpec={}", backendSpec.backendId());
        ExecutionTask task = ExecutionTask.of(backendSpec);
        return ExecutionJob.of("local", backendSpec.backendId(),
                backendSpec.backendType(), List.of(task));
    }
}
