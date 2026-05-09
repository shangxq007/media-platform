package com.example.platform.render.infrastructure.mlt;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates that the MLT/melt environment is correctly configured.
 */
public class MltEnvironmentValidator {

    private static final Logger log = LoggerFactory.getLogger(MltEnvironmentValidator.class);

    private final ProcessToolRunner processToolRunner;

    public MltEnvironmentValidator(ProcessToolRunner processToolRunner) {
        this.processToolRunner = processToolRunner;
    }

    /**
     * Validates the MLT environment.
     *
     * @return true if melt is available and working
     */
    public boolean validate() {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    "melt", List.of("-query", "plugins"), 10_000);
            ToolExecutionResult result = processToolRunner.execute(request);
            if (result.isSuccess()) {
                log.info("MLT/melt environment validation passed");
                return true;
            }
        } catch (Exception e) {
            log.debug("MLT/melt is not available: {}", e.getMessage());
        }
        log.warn("MLT/melt environment validation failed");
        return false;
    }
}
