package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.infrastructure.RenderProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone smoke test for Golden Render.
 * Run: cd platform && ./gradlew :render-module:execute -PmainClass=... (or use IDE)
 */
public class GoldenRenderSmokeTest {

    public static void main(String[] args) throws Exception {
        Path assetsBase = Path.of("test-assets/golden-render-project-v1/assets");
        Path tempDir = Files.createTempDirectory("golden-render");

        System.out.println("assetsBase=" + assetsBase.toAbsolutePath());
        System.out.println("tempDir=" + tempDir.toAbsolutePath());

        // Check assets
        Path colorBars = assetsBase.resolve("video/color_bars_1080p.mp4");
        System.out.println("colorBars exists=" + Files.exists(colorBars));

        // Build timeline
        String uri = "file://" + colorBars.toAbsolutePath();
        String timelineJson = String.format("""
                {"id":"golden-smoke","name":"Smoke","outputSpec":{"format":"mp4","width":1920,"height":1080,"frameRate":30,"videoCodec":"h264","audioCodec":"aac","videoBitrateKbps":5000,"audioBitrateKbps":192},
                 "tracks":[{"id":"v1","name":"Video","type":"VIDEO","children":[{"id":"c1","name":"color_bars","media_reference":"%s","source_range":{"start_time":0,"duration":5}]}],
                 "duration":5}
                """, uri);

        System.out.println("timelineJson=" + timelineJson);

        // Create provider
        ProcessToolRunner toolRunner = new RealFfmpegRunner();
        FFmpegRenderProvider provider = GoldenRenderPlanAdapter.createLocalProvider(tempDir, toolRunner);

        // Render
        System.out.println("Starting render...");
        RenderProvider.RenderResult result = provider.render("golden-smoke", timelineJson, "default_1080p");
        System.out.println("Render complete: artifactId=" + result.artifactId() + " format=" + result.format());

        // Check output
        Path outputFile = tempDir.resolve("artifacts/golden-smoke/output.mp4");
        System.out.println("output exists=" + Files.exists(outputFile) + " size=" + (Files.exists(outputFile) ? Files.size(outputFile) : 0));
    }

    static class RealFfmpegRunner implements ProcessToolRunner {
        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            return execute(request, null);
        }
        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request,
                com.example.platform.extension.domain.ToolSandboxPolicy policy) {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(request.toolKey());
                cmd.addAll(request.args());
                System.out.println("[FfmpegRunner] cmd=" + String.join(" ", cmd));
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(false);
                Process p = pb.start();
                String stdout = new String(p.getInputStream().readAllBytes());
                String stderr = new String(p.getErrorStream().readAllBytes());
                long timeout = policy != null && policy.timeoutMillis() > 0 ? policy.timeoutMillis() : 120_000;
                boolean done = p.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (!done) { p.destroyForcibly(); return ToolExecutionResult.timedOut("", "timeout", Instant.now(), Instant.now()); }
                int ec = p.exitValue();
                Instant now = Instant.now();
                if (ec == 0) return ToolExecutionResult.success(ec, stdout, stderr, now, now);
                System.out.println("[FfmpegRunner] FAILED exit=" + ec + " stderr=" + stderr.substring(0, Math.min(500, stderr.length())));
                return ToolExecutionResult.failed(ec, stdout, stderr, now, now);
            } catch (Exception e) {
                Instant now = Instant.now();
                return ToolExecutionResult.failed(-1, "", e.getMessage(), now, now);
            }
        }
    }
}
