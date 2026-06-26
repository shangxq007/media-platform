package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.ExecutionJob;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenCue environment compiler — transforms ExecutionJob → OpenCueJobSpec.
 * Phase 1: single task → single layer. No frame splitting.
 */
@Component
public class OpenCueEnvironmentCompiler implements EnvironmentCompiler {

    private static final Logger log = LoggerFactory.getLogger(OpenCueEnvironmentCompiler.class);
    private final OpenCueProperties props;

    public OpenCueEnvironmentCompiler(OpenCueProperties props) { this.props = props; }

    @Override public String environmentType() { return "opencue"; }

    @Override
    public boolean supports(String environmentType) { return "opencue".equals(environmentType); }

    @Override
    public ExecutionJob compile(com.example.platform.render.domain.execution.BackendExecutionSpec spec) {
        log.info("OpenCueEnvironmentCompiler: compiling backendSpec={}", spec.backendId());
        var task = com.example.platform.render.domain.execution.ExecutionTask.of(spec);
        return ExecutionJob.of("opencue", spec.backendId(), spec.backendType(), List.of(task));
    }
}
