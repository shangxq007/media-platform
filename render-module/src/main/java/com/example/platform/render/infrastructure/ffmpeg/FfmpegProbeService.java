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
 *
 * <p>This service constructs probe commands via {@link FfmpegCommandFactory} and
 * executes them through the {@link ProcessToolRunner} port. No direct process
 * execution is performed.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * Map<String, String> probeData = probeService.probe("storage://video.mp4");
 * </pre>
 */
public class FfmpegProbeService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegProbeService.class);

    private final ProcessToolRunner processToolRunner;
    private final FfmpegCommandFactory commandFactory;

    public FfmpegProbeService(ProcessToolRunner processToolRunner,
            FfmpegCommandFactory commandFactory) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
    }

    /**
     * Probes a media file and returns the parsed metadata.
     *
     * @param inputUri the input file URI
     * @return map of probe metadata (format, streams, etc.)
     * @throws IllegalStateException if the probe fails
     */
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

        // Parse JSON output (skeleton — actual JSON parsing deferred)
        return Map.of(
                "inputUri", inputUri,
                "exitCode", result.exitCode(),
                "rawOutput", result.stdout()
        );
    }

    /**
     * Extracts a thumbnail from a media file.
     *
     * @param inputUri   the input file URI
     * @param outputUri  the output thumbnail URI
     * @param timeOffset time offset in seconds
     * @param width      thumbnail width in pixels
     * @return true if the thumbnail was extracted successfully
     */
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
