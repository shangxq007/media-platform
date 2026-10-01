package com.example.platform.coverimage;

import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.TaskCapability;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Real FFmpeg cover-image provider: extracts exactly one frame at the requested timestamp and writes
 * a single still image. All FFmpeg mechanics stay inside this provider; execution goes through the
 * registered sandbox backend for {@code TaskCapability.COVER_IMAGE}.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class CpuFrameExtractCoverImageProvider implements CoverImageCapabilityProvider {

    static final long MAXIMUM_INPUT_BYTES = 512L * 1024L * 1024L;

    private final ExecutionBackendRegistry backends;
    private final Path workRoot;

    public CpuFrameExtractCoverImageProvider(
            ExecutionBackendRegistry backends,
            @Value("${app.cover-image.work-root:./.data/cover-image-work}") String workRoot) {
        this.backends = backends;
        this.workRoot = Path.of(workRoot).toAbsolutePath().normalize();
    }

    @Override
    public Manifest manifest() {
        return new Manifest(
                CoverImageContracts.PROVIDER,
                CoverImageContracts.PROVIDER_IMPLEMENTATION,
                CoverImageContracts.PROVIDER_VERSION,
                List.of(new CapabilityDeclaration(
                        CoverImageContracts.CAPABILITY, CoverImageContracts.CAPABILITY_VERSION)),
                "ffmpeg",
                Set.of("video/mp4", "video/quicktime", "video/webm", "video/x-matroska"),
                Set.of("png", "jpeg"),
                0d,
                86_400d,
                16,
                8192,
                MAXIMUM_INPUT_BYTES,
                120,
                "sandbox-bwrap",
                "ffmpeg");
    }

    @Override
    public Result render(
            String capabilityId,
            CoverImageContracts.Request request,
            Path inputPath,
            BooleanSupplier cancelled) {
        // Fail closed: this implementation only executes capabilities it declares.
        if (!manifest().supports(capabilityId)) {
            return Result.failure("UNSUPPORTED_CAPABILITY");
        }
        if (cancelled.getAsBoolean()) {
            return Result.failure("CANCELLED");
        }
        // Parameter authority: the shared capability parameter contract must accept this request
        // before any execution. A request the transport admitted but the shared contract rejects
        // (e.g. a source instant that is not an exact, non-negative rational) fails closed here
        // instead of reaching FFmpeg. The transport DTO no longer owns the parameter vocabulary.
        try {
            request.capabilityParameters();
        } catch (IllegalArgumentException rejectedParameters) {
            return Result.failure("UNSUPPORTED_PARAMETERS");
        }
        try {
            Path output = workRoot.resolve(request.idempotencyKey())
                    .resolve("provider-output")
                    .resolve("cover." + request.imageFormat())
                    .normalize();
            if (!output.startsWith(workRoot)) {
                return Result.failure("INVALID_OUTPUT_PATH");
            }
            Files.createDirectories(output.getParent());
            var backend = backends.resolve(TaskCapability.COVER_IMAGE)
                    .orElseThrow(() -> new IllegalStateException(
                            "cover-image execution backend is not registered"));
            List<String> arguments = new ArrayList<>(List.of(
                    "-hide_banner", "-nostdin", "-y",
                    "-ss", Double.toString(request.timestampSeconds()),
                    "-i", inputPath.toString(),
                    "-frames:v", "1"));
            if (request.width() != null) {
                arguments.addAll(List.of("-vf", "scale=" + request.width() + ":-2"));
            }
            if ("jpeg".equals(request.imageFormat()) && request.quality() != null) {
                arguments.addAll(List.of("-q:v", Integer.toString(jpegQuality(request.quality()))));
            }
            arguments.add(output.toString());
            var execution = backend.execute(ExecutionRequest.of(
                    "cover-image:" + request.idempotencyKey(),
                    request.idempotencyKey(),
                    TaskCapability.COVER_IMAGE,
                    arguments,
                    120));
            if (cancelled.getAsBoolean()) {
                return Result.failure("CANCELLED");
            }
            if (!execution.success() || !Files.isRegularFile(output)) {
                return Result.failure(execution.errorCode() == null
                        ? "PROVIDER_EXECUTION_FAILED"
                        : execution.errorCode());
            }
            byte[] bytes = Files.readAllBytes(output);
            if (bytes.length == 0) {
                return Result.failure("PROVIDER_EMPTY_OUTPUT");
            }
            return Result.success(bytes, "png".equals(request.imageFormat()) ? "image/png" : "image/jpeg");
        } catch (IOException failure) {
            return Result.failure("PROVIDER_IO_FAILED");
        }
    }

    /** Maps user quality (1..100) onto FFmpeg's JPEG qscale (1..31). */
    private static int jpegQuality(int quality) {
        int scaled = (int) Math.round(31.0 - (quality / 100.0) * 30.0);
        return Math.max(1, Math.min(31, scaled));
    }
}
