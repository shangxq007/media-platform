package com.example.platform.render.infrastructure.libass;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LibassSubtitleCompositor {

    private static final Logger log = LoggerFactory.getLogger(LibassSubtitleCompositor.class);

    private final ProcessToolRunner processToolRunner;
    private final LibassAssFileWriter assFileWriter = new LibassAssFileWriter();

    @Value("${render.providers.ffmpeg.binary:ffmpeg}")
    private String ffmpegBinary;

    @Value("${render.subtitle.libass.timeout-ms:300000}")
    private long timeoutMs;

    public LibassSubtitleCompositor(ProcessToolRunner processToolRunner) {
        this.processToolRunner = processToolRunner;
    }

    public ComposeResult applyTextOverlays(Path inputVideo, Path outputVideo, TimelineSpec spec) {
        if (spec.textOverlays() == null || spec.textOverlays().isEmpty()) {
            return ComposeResult.noOverlays();
        }
        if (!Files.isRegularFile(inputVideo)) {
            return ComposeResult.failed("Input video missing: " + inputVideo);
        }
        try {
            TimelineOutputSpec output = spec.outputSpec();
            int width = output != null ? output.width() : 1920;
            int height = output != null ? output.height() : 1080;
            Path assPath = outputVideo.getParent().resolve("burn-in.ass");
            assFileWriter.write(assPath, spec.textOverlays(), width, height);

            List<String> args = new ArrayList<>();
            args.add(ffmpegBinary);
            args.add("-y");
            args.add("-i");
            args.add(inputVideo.toString());
            args.add("-vf");
            args.add("ass=" + escapeAssPath(assPath));
            args.add("-c:a");
            args.add("copy");
            args.add(outputVideo.toString());

            ToolExecutionResult result = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("ffmpeg-libass", args, timeoutMs));
            if (!result.isSuccess()) {
                return ComposeResult.failed("libass burn-in failed: " + result.stderr());
            }
            log.info("LibassSubtitleCompositor: burned {} overlays into {}", spec.textOverlays().size(), outputVideo);
            return ComposeResult.success(outputVideo);
        } catch (Exception e) {
            log.error("Libass compositor failed", e);
            return ComposeResult.failed(e.getMessage());
        }
    }

    private String escapeAssPath(Path assPath) {
        return assPath.toAbsolutePath().toString().replace("'", "'\\''").replace(":", "\\:");
    }

    public record ComposeResult(boolean success, boolean wasSkipped, Path outputPath, String errorMessage) {
        public static ComposeResult success(Path output) {
            return new ComposeResult(true, false, output, null);
        }

        public static ComposeResult noOverlays() {
            return new ComposeResult(true, true, null, null);
        }

        public static ComposeResult failed(String message) {
            return new ComposeResult(false, false, null, message);
        }
    }
}
