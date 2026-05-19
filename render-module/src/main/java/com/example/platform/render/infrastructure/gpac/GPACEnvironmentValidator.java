package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPACEnvironmentValidator {

    private static final Logger log = LoggerFactory.getLogger(GPACEnvironmentValidator.class);

    private final ProcessToolRunner processToolRunner;

    public GPACEnvironmentValidator(ProcessToolRunner processToolRunner) {
        this.processToolRunner = processToolRunner;
    }

    public boolean validate() {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    "MP4Box", List.of("-version"), 10_000);
            ToolExecutionResult result = processToolRunner.execute(request);
            if (result.isSuccess()) {
                log.info("GPAC/MP4Box environment validation passed");
                return true;
            }
        } catch (Exception e) {
            log.debug("GPAC/MP4Box is not available: {}", e.getMessage());
        }
        log.warn("GPAC/MP4Box environment validation failed");
        return false;
    }
}
