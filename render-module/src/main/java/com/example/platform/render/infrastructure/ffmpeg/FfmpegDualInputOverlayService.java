package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Dual-input FFmpeg overlay compositor (base + transparent overlay), used by OFX / PopcornFX paths.
 */
@Service
public class FfmpegDualInputOverlayService {

    private static final Logger log = LoggerFactory.getLogger(FfmpegDualInputOverlayService.class);

    private final ProcessToolRunner processToolRunner;

    @Value("${render.providers.ffmpeg.binary:ffmpeg}")
    private String ffmpegBinary;

    @Value("${render.providers.ffmpeg.overlay-timeout-ms:600000}")
    private long overlayTimeoutMs;

    public FfmpegDualInputOverlayService(ProcessToolRunner processToolRunner) {
        this.processToolRunner = processToolRunner;
    }

    public OverlayResult compose(Path baseVideo, Path overlayAsset, Path output,
                                 int x, int y, double opacity) {
        if (!Files.isRegularFile(baseVideo)) {
            return OverlayResult.failed("Base video missing: " + baseVideo);
        }
        if (!Files.isRegularFile(overlayAsset)) {
            return OverlayResult.failed("Overlay asset missing: " + overlayAsset);
        }
        try {
            Files.createDirectories(output.getParent());
            List<String> args = buildOverlayCommand(baseVideo, overlayAsset, output, x, y, opacity);
            ToolExecutionResult result = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("ffmpeg-overlay", args, overlayTimeoutMs));
            if (!result.isSuccess()) {
                return OverlayResult.failed("ffmpeg overlay failed: " + result.stderr());
            }
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                return OverlayResult.failed("ffmpeg overlay produced empty output");
            }
            log.info("FfmpegDualInputOverlayService: composed {} + {} -> {}", baseVideo, overlayAsset, output);
            return OverlayResult.success(output);
        } catch (Exception e) {
            log.error("Dual-input overlay failed", e);
            return OverlayResult.failed(e.getMessage());
        }
    }

    List<String> buildOverlayCommand(Path baseVideo, Path overlayAsset, Path output,
                                   int x, int y, double opacity) {
        double alpha = Math.max(0.0, Math.min(1.0, opacity));
        String filter = String.format(
                "[1:v]format=rgba,colorchannelmixer=aa=%.3f[ov];[0:v][ov]overlay=%d:%d:format=auto[out]",
                alpha, x, y);
        List<String> args = new ArrayList<>();
        args.add(ffmpegBinary);
        args.add("-y");
        args.add("-i");
        args.add(baseVideo.toString());
        args.add("-i");
        args.add(overlayAsset.toString());
        args.add("-filter_complex");
        args.add(filter);
        args.add("-map");
        args.add("[out]");
        args.add("-map");
        args.add("0:a?");
        args.add("-c:v");
        args.add("libx264");
        args.add("-preset");
        args.add("fast");
        args.add("-c:a");
        args.add("copy");
        args.add(output.toString());
        return args;
    }

    public record OverlayResult(boolean success, Path outputPath, String errorMessage) {
        public static OverlayResult success(Path output) {
            return new OverlayResult(true, output, null);
        }

        public static OverlayResult failed(String message) {
            return new OverlayResult(false, null, message);
        }
    }
}
