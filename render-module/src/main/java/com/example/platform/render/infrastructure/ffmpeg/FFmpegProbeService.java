package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for probing media files using FFprobe.
 */
public class FFmpegProbeService {

    private static final Logger log = LoggerFactory.getLogger(FFmpegProbeService.class);

    private final ProcessToolRunner processToolRunner;
    private final FFmpegCommandFactory commandFactory;

    public FFmpegProbeService(ProcessToolRunner processToolRunner,
            FFmpegCommandFactory commandFactory) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
    }

    public Map<String, Object> probe(String inputUri) {
        List<String> args = commandFactory.buildProbeCommand(inputUri);
        log.info("Probing media file: {}", inputUri);

        ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                "ffprobe", args, 30_000);

        ToolExecutionResult result = processToolRunner.execute(request);

        if (!result.isSuccess()) {
            throw new IllegalStateException(
                    "FFprobe failed for " + inputUri + ": " + result.stderr());
        }

        return Map.of(
                "inputUri", inputUri,
                "exitCode", result.exitCode(),
                "rawOutput", result.stdout()
        );
    }

    public boolean extractThumbnail(String inputUri, String outputUri,
            double timeOffset, int width) {
        List<String> args = commandFactory.buildThumbnailCommand(inputUri, outputUri, timeOffset, width);
        log.info("Extracting thumbnail: input={} output={}", inputUri, outputUri);

        ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                "ffmpeg", args, 60_000);

        ToolExecutionResult result = processToolRunner.execute(request);
        return result.isSuccess();
    }
}
