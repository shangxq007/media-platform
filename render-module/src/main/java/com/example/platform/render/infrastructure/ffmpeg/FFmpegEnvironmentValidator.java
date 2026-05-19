package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates that the FFmpeg environment is correctly configured.
 *
 * <p>Checks for the presence and executability of ffmpeg and ffprobe binaries.
 * Called at startup to verify the render environment.</p>
 */
public class FFmpegEnvironmentValidator {

    private static final Logger log = LoggerFactory.getLogger(FFmpegEnvironmentValidator.class);

    private final ProcessToolRunner processToolRunner;

    public FFmpegEnvironmentValidator(ProcessToolRunner processToolRunner) {
        this.processToolRunner = processToolRunner;
    }

    public boolean validate() {
        boolean ffmpegOk = validateBinary("ffmpeg");
        boolean ffprobeOk = validateBinary("ffprobe");

        if (ffmpegOk && ffprobeOk) {
            log.info("FFmpeg environment validation passed");
        } else {
            log.warn("FFmpeg environment validation failed: ffmpeg={} ffprobe={}", ffmpegOk, ffprobeOk);
        }

        return ffmpegOk && ffprobeOk;
    }

    public boolean validateBinary(String toolKey) {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    toolKey, List.of("-version"), 10_000);
            ToolExecutionResult result = processToolRunner.execute(request);
            if (result.isSuccess()) {
                log.debug("{} is available: {}", toolKey, result.stdout().lines().findFirst().orElse("unknown"));
                return true;
            }
        } catch (Exception e) {
            log.debug("{} is not available: {}", toolKey, e.getMessage());
        }
        return false;
    }
}
