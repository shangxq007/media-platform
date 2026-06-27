package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.BackendExecutionSpec;
import com.example.platform.render.domain.execution.ExecutionJob;
import com.example.platform.render.domain.execution.ExecutionTask;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenCue environment compiler — transforms BackendExecutionSpec → ExecutionJob.
 *
 * <p>Phase 1: one task → one layer. No frame splitting.
 * Deterministic: equivalent BackendExecutionSpec produces equivalent ExecutionJob.
 *
 * <p>Architecture boundary enforcement:
 * <ul>
 *   <li>No repository access</li>
 *   <li>No planning logic</li>
 *   <li>No provider selection</li>
 *   <li>No ProductRuntime access</li>
 *   <li>No StorageRuntime semantic changes</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "opencue.enabled", havingValue = "true", matchIfMissing = false)
public class OpenCueEnvironmentCompiler implements EnvironmentCompiler {

    private static final Logger log = LoggerFactory.getLogger(OpenCueEnvironmentCompiler.class);
    private final OpenCueProperties props;

    public OpenCueEnvironmentCompiler(OpenCueProperties props) {
        this.props = props;
    }

    @Override
    public String environmentType() {
        return "opencue";
    }

    @Override
    public boolean supports(String environmentType) {
        return "opencue".equals(environmentType);
    }

    /**
     * Compile a BackendExecutionSpec into an ExecutionJob for OpenCue.
     *
     * <p>Deterministic: same spec → same job structure.
     * BackendId and producerId are preserved in scheduling hints.
     */
    @Override
    public ExecutionJob compile(BackendExecutionSpec spec) {
        if (!props.isEnabled()) {
            log.warn("OpenCue compiler invoked while disabled — compilation proceeds but "
                    + "environment submit will be rejected.");
        }
        log.info("OpenCueEnvironmentCompiler: compiling backendSpec={} backendType={}",
                spec.backendId(), spec.backendType());
        ExecutionTask task = ExecutionTask.of(spec);
        return ExecutionJob.of("opencue", spec.backendId(), spec.backendType(), List.of(task));
    }
}
