package com.example.platform.render.infrastructure.ffmpeg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegDualInputOverlayServiceTest {

    @Test
    void buildOverlayCommandIncludesFilterComplex(@TempDir Path tempDir) throws Exception {
        ProcessToolRunner runner = mock(ProcessToolRunner.class);
        FfmpegDualInputOverlayService service = new FfmpegDualInputOverlayService(runner);
        Path base = tempDir.resolve("base.mp4");
        Path overlay = tempDir.resolve("overlay.webm");
        Path out = tempDir.resolve("out.mp4");
        Files.write(base, new byte[] {1});
        Files.write(overlay, new byte[] {1});

        List<String> args = service.buildOverlayCommand(base, overlay, out, 10, 20, 0.75);
        assertTrue(args.contains("-filter_complex"));
        String filter = args.get(args.indexOf("-filter_complex") + 1);
        assertTrue(filter.contains("overlay=10:20"));
        assertTrue(filter.contains("colorchannelmixer=aa=0.750"));
    }

    @Test
    void composeSucceedsWhenFfmpegOk(@TempDir Path tempDir) throws Exception {
        ProcessToolRunner runner = mock(ProcessToolRunner.class);
        Instant now = Instant.now();
        when(runner.execute(any(ToolExecutionRequest.class)))
                .thenAnswer(inv -> {
                    Path out = inv.getArgument(0, ToolExecutionRequest.class).args().stream()
                            .filter(a -> a.endsWith(".mp4"))
                            .map(Path::of)
                            .findFirst()
                            .orElse(tempDir.resolve("out.mp4"));
                    Files.write(out, new byte[] {0, 0, 0, 8});
                    return ToolExecutionResult.success(0, "", "", now, now.plusMillis(5));
                });

        FfmpegDualInputOverlayService service = new FfmpegDualInputOverlayService(runner);
        Path base = tempDir.resolve("base.mp4");
        Path overlay = tempDir.resolve("ov.mp4");
        Path out = tempDir.resolve("composed.mp4");
        Files.write(base, new byte[] {1});
        Files.write(overlay, new byte[] {1});

        when(runner.execute(any())).thenAnswer(inv -> {
            Files.write(out, new byte[] {0, 0, 0, 8});
            return ToolExecutionResult.success(0, "", "", now, now.plusMillis(1));
        });

        var result = service.compose(base, overlay, out, 0, 0, 1.0);
        assertTrue(result.success());
        assertEquals(out, result.outputPath());
    }
}
