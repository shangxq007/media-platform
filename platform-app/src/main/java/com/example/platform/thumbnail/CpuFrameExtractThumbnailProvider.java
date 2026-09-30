package com.example.platform.thumbnail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import com.example.platform.sandbox.*;
import com.example.platform.sandbox.execution.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Registered provider boundary for media.thumbnail. Process mechanics are confined here. */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class CpuFrameExtractThumbnailProvider implements ThumbnailCapabilityProvider {
    public static final String PROVIDER_ID = "ffmpeg.cpu.frame-extract.v1";
    public static final String TOOLCHAIN = "ffmpeg+ffprobe";
    private final Path root;
    private final String ffmpeg;
    private final String ffprobe;
    private final WorkerRuntime runtime;
    private static final Manifest MANIFEST = new Manifest(
            ThumbnailContracts.CAPABILITY, PROVIDER_ID, "1.0.0", TOOLCHAIN,
            Set.of("video/*"), Set.of("image/jpeg", "image/png"),
            0, 86_400, 16, 4096, 512L * 1024L * 1024L, 60,
            "trusted-provider", "worker-runtime.local-process");

    @Autowired
    public CpuFrameExtractThumbnailProvider(
            @Value("${app.storage.local-root:./.data/storage}") String root,
            @Value("${thumbnail.ffmpeg-path:ffmpeg}") String ffmpeg,
            @Value("${thumbnail.ffprobe-path:ffprobe}") String ffprobe,
            com.example.platform.sandbox.execution.ExecutionBackendRegistry backends) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
        this.runtime = new WorkerRuntime(backends);
    }
    public CpuFrameExtractThumbnailProvider(String root, String ffmpeg, String ffprobe) {
        this(root, ffmpeg, ffprobe, new com.example.platform.providerplugin.execution.RuntimeExecutionBackends(java.util.List.of(new ThumbnailExecutionBackend())));
    }

    @Override public Manifest manifest() { return MANIFEST; }

    @Override public Result extract(ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled) {
        Path work = root.resolve("thumbnail-work").resolve(request.idempotencyKey()).normalize();
        try {
            Files.createDirectories(work);
            Path source = work.resolve("input");
            Files.write(source, input);
            double duration = probe(source, cancelled);
            if (!Double.isFinite(duration) || request.timestampSeconds() > duration) {
                return Result.failure("TIMESTAMP_OUT_OF_RANGE");
            }
            if (cancelled.getAsBoolean()) return Result.failure("CANCELLED");
            var args = new java.util.ArrayList<String>(List.of(
                    "-hide_banner", "-nostdin", "-loglevel", "error",
                    "-ss", Double.toString(request.timestampSeconds()), "-i", source.toString(),
                    "-frames:v", "1", "-f", "image2pipe", "-vcodec", "png".equals(request.imageFormat()) ? "png" : "mjpeg"));
            if (request.width() != null) args.addAll(List.of("-vf", "scale=" + request.width() + ":-2"));
            if (request.quality() != null && "jpeg".equals(request.imageFormat())) {
                int at = args.size();
                args.add(at, "-q:v"); args.add(at + 1, Integer.toString(Math.max(2, Math.min(31, 32 - request.quality() / 4))));
            }
            args.add("pipe:1");
            Path output = work.resolve("output");
            args.set(args.size() - 1, output.toString());
            var execution = runtime.execute(PROVIDER_ID, new ExecutionRequest(request.idempotencyKey(), request.idempotencyKey(), TaskCapability.THUMBNAIL,
                    work.toString(), java.util.Map.of(), args, 60, request.tenantId(), request.projectId(),
                    java.util.Map.of("executable", Path.of(ffmpeg).toString(), "input", source.toString(), "providerId", PROVIDER_ID)));
            if (!execution.success() || !Files.exists(output) || Files.size(output) == 0) {
                return Result.failure("PROVIDER_FAILED");
            }
            return Result.success(Files.readAllBytes(output), "png".equals(request.imageFormat()) ? "image/png" : "image/jpeg");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.failure("CANCELLED");
        } catch (IOException failure) {
            return Result.failure("PROVIDER_FAILED");
        } finally {
            try { if (Files.exists(work)) Files.walk(work).sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) {} }); }
            catch (IOException ignored) {}
        }
    }

    private double probe(Path source, BooleanSupplier cancelled) throws IOException, InterruptedException {
        var execution = runtime.execute(PROVIDER_ID, new ExecutionRequest("probe", "probe", TaskCapability.THUMBNAIL,
                source.getParent().toString(), java.util.Map.of(), List.of("-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", source.toString()), 60, "", "", java.util.Map.of("executable", Path.of(ffprobe).toString(), "input", source.toString(), "providerId", PROVIDER_ID)));
        if (!execution.success()) throw new IllegalArgumentException("UNSUPPORTED_MEDIA");
        try { return Double.parseDouble(execution.stdout().trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("UNSUPPORTED_MEDIA", e); }
    }

}
