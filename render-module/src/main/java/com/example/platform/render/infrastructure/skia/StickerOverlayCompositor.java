package com.example.platform.render.infrastructure.skia;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.legacy.TimelineSticker;
import com.example.platform.render.domain.legacy.TimelineStickerReader;
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
        return ComposeResult.failed("TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED");
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
