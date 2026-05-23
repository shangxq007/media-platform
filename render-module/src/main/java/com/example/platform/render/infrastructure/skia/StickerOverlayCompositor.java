package com.example.platform.render.infrastructure.skia;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineSticker;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StickerOverlayCompositor {

    private static final Logger log = LoggerFactory.getLogger(StickerOverlayCompositor.class);

    private final ProcessToolRunner processToolRunner;
    private final StickerRasterizer rasterizer;
    private final TimelineStickerReader stickerReader;
    private final TimelineScriptParser timelineScriptParser;

    @Value("${render.providers.ffmpeg.binary:ffmpeg}")
    private String ffmpegBinary;

    @Value("${render.providers.skia.timeout-ms:300000}")
    private long timeoutMs;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public StickerOverlayCompositor(ProcessToolRunner processToolRunner,
                                    StickerRasterizer rasterizer,
                                    TimelineStickerReader stickerReader,
                                    TimelineScriptParser timelineScriptParser) {
        this.processToolRunner = processToolRunner;
        this.rasterizer = rasterizer;
        this.stickerReader = stickerReader;
        this.timelineScriptParser = timelineScriptParser;
    }

    public ComposeResult applyStickers(Path inputVideo, Path outputVideo, TimelineSpec spec) {
        List<TimelineSticker> stickers = stickerReader.fromSpec(spec);
        if (stickers.isEmpty()) {
            return ComposeResult.skipped("No stickers");
        }
        if (!Files.isRegularFile(inputVideo)) {
            return ComposeResult.failed("Input missing: " + inputVideo);
        }
        try {
            Path workDir = outputVideo.getParent().resolve("skia-stickers");
            Files.createDirectories(workDir);
            Path current = inputVideo;
            int index = 0;
            for (TimelineSticker sticker : stickers) {
                Path stickerPng = prepareStickerPng(workDir, sticker, ++index);
                Path stepOut = workDir.resolve("step-" + index + ".mp4");
                overlayOne(current, stickerPng, stepOut, sticker);
                current = stepOut;
            }
            Files.copy(current, outputVideo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return ComposeResult.success(outputVideo);
        } catch (Exception e) {
            log.error("Sticker overlay failed", e);
            return ComposeResult.failed(e.getMessage());
        }
    }

    private Path prepareStickerPng(Path workDir, TimelineSticker sticker, int index) throws Exception {
        String uri = sticker.imageUri();
        Path source = Path.of(timelineScriptParser.resolveLocalPath(uri, storageRoot));
        Path raw = workDir.resolve("raw-" + index + ".png");
        if (!Files.exists(source)) {
            Files.write(raw, minimalPng(sticker.width() > 0 ? (int) sticker.width() : 64,
                    sticker.height() > 0 ? (int) sticker.height() : 64));
        } else {
            Files.copy(source, raw, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        int w = Math.max(1, (int) sticker.width());
        int h = Math.max(1, (int) sticker.height());
        return rasterizer.rasterizeToPng(raw, workDir.resolve("sticker-" + index + ".png"), w, h);
    }

    private void overlayOne(Path video, Path stickerPng, Path output, TimelineSticker sticker) {
        int x = Math.max(0, (int) sticker.x());
        int y = Math.max(0, (int) sticker.y());
        double opacity = sticker.opacity() > 0 ? sticker.opacity() : 1.0;
        String filter = String.format(
                "[0:v][1:v]overlay=%d:%d:enable='between(t,%.3f,%.3f)':format=auto,format=yuv420p",
                x, y, sticker.startTime(), sticker.startTime() + sticker.duration());
        if (opacity < 0.99) {
            filter = String.format(
                    "[1:v]format=rgba,colorchannelmixer=aa=%.2f[stk];[0:v][stk]overlay=%d:%d:"
                            + "enable='between(t,%.3f,%.3f)'",
                    opacity, x, y, sticker.startTime(), sticker.startTime() + sticker.duration());
        }
        List<String> args = new ArrayList<>();
        args.add(ffmpegBinary);
        args.add("-y");
        args.add("-i");
        args.add(video.toString());
        args.add("-i");
        args.add(stickerPng.toString());
        args.add("-filter_complex");
        args.add(filter);
        args.add("-c:a");
        args.add("copy");
        args.add(output.toString());
        ToolExecutionResult result = processToolRunner.execute(
                ToolExecutionRequest.withTimeout("ffmpeg-sticker-overlay", args, timeoutMs));
        if (!result.isSuccess()) {
            throw new IllegalStateException("Sticker overlay ffmpeg failed: " + result.stderr());
        }
    }

    private static byte[] minimalPng(int w, int h) {
        try {
            var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public record ComposeResult(boolean success, boolean skipped, Path output, String errorMessage) {
        static ComposeResult success(Path output) {
            return new ComposeResult(true, false, output, null);
        }

        static ComposeResult skipped(String reason) {
            return new ComposeResult(true, true, null, reason);
        }

        static ComposeResult failed(String error) {
            return new ComposeResult(false, false, null, error);
        }
    }
}
