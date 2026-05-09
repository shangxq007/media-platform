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
public class FfmpegEnvironmentValidator {

    private static final Logger log = LoggerFactory.getLogger(FfmpegEnvironmentValidator.class);

    private final ProcessToolRunner processToolRunner;

    public FfmpegEnvironmentValidator(ProcessToolRunner processToolRunner) {
        this.processToolRunner = processToolRunner;
    }

    /**
     * Validates the FFmpeg environment.
     *
     * @return true if both ffmpeg and ffprobe are available and working
     */
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

    /**
     * Validates a single binary by running it with -version.
     *
     * @param toolKey the tool key (must be registered in the tool registry)
     * @return true if the binary is available
     */
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
