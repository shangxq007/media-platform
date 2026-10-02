package com.example.platform.frameextract;

import com.example.platform.contract.media.FrameExtractProvider;
import com.example.platform.contract.media.FrameExtractManifest;
import com.example.platform.contract.media.FrameExtractResult;
import com.example.platform.contract.media.FrameExtractCapabilityProfile;
import com.example.platform.contract.media.FrameExtractCapabilityDeclaration;

import com.example.platform.contract.media.CoverImageContracts;
import com.example.platform.sandbox.execution.ExecutionBackendRegistry;
import com.example.platform.sandbox.execution.ExecutionRequest;
import com.example.platform.sandbox.execution.TaskCapability;
import com.example.platform.shared.capability.FrameWidth;
import com.example.platform.shared.capability.JpegQuality;
import com.example.platform.shared.capability.MediaFrameExtractParametersV1;
import com.example.platform.shared.capability.RasterImageEncoding;
import com.example.platform.contract.media.ThumbnailContracts;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The single capability-neutral FFmpeg frame-extract provider for the {@code platform.ffmpeg} family,
 * implementation {@code ffmpeg.cpu.frame-extract.v1}.
 *
 * <p>It replaces {@code CpuFrameExtractCoverImageProvider} and
 * {@code CpuFrameExtractThumbnailProvider}. The provider identity, the manifest and the FFmpeg
 * mechanics are capability-neutral; everything that genuinely differed between the two capabilities —
 * the width bound, the timeout and which registered sandbox backend the command runs through — is
 * carried by a {@link FrameExtractCapabilityProfile} selected from the {@code capabilityId} parameter
 * the caller passes in. One provider therefore declares a capability <em>list</em>
 * ({@code media.cover-image@1.0} and {@code media.thumbnail@1.0}) and serves both, instead of two
 * capability-scoped provider classes.
 *
 * <p>What is deliberately <b>not</b> here: neither accepted input form nor provenance intent changes
 * the extracted frame, so the input is always the digest-verified subject file on disk and the
 * provenance edge ({@code COVER_OF} / {@code THUMBNAIL_OF}) stays in the commit services that own it.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class FfmpegCpuProvider implements FrameExtractProvider {

    /** Provider/backend family identity (capability-independent); never a capability identity. */
    public static final String PROVIDER_ID = CoverImageContracts.PROVIDER;
    /** Implementation identity of this runtime/adapter within the {@link #PROVIDER_ID} family. */
    public static final String PROVIDER_IMPLEMENTATION_ID = CoverImageContracts.PROVIDER_IMPLEMENTATION;
    public static final String PROVIDER_VERSION = CoverImageContracts.PROVIDER_VERSION;

    public static final Set<String> OUTPUT_FORMATS = Set.of("image/jpeg", "image/png");
    public static final Set<String> INPUT_FORMATS =
            Set.of("video/mp4", "video/quicktime", "video/webm", "video/x-matroska", "video/*");

    public static final int COVER_TIMEOUT_SECONDS = 120;
    public static final int THUMBNAIL_TIMEOUT_SECONDS = 60;
    public static final int COVER_MAXIMUM_WIDTH = 8192;
    public static final int THUMBNAIL_MAXIMUM_WIDTH = 4096;
    public static final int MINIMUM_WIDTH = 16;
    public static final long MAXIMUM_INPUT_BYTES = 512L * 1024L * 1024L;

    private static final Map<String, FrameExtractCapabilityProfile> PROFILES = profiles();
    private static final FrameExtractManifest MANIFEST = manifest(PROFILES);

    private final ExecutionBackendRegistry backends;
    private final Path workRoot;
    private final String ffmpeg;
    private final String ffprobe;

    /**
     * Worker composition: the provider takes the worker's {@link ExecutionBackendRegistry} so both
     * capability profiles run through the registered sandbox backend for their task capability.
     */
    @Autowired
    public FfmpegCpuProvider(
            ExecutionBackendRegistry backends,
            @Value("${app.cover-image.work-root:./.data/cover-image-work}") String workRoot,
            @Value("${platform.ffmpeg-worker.sandbox.ffmpeg:/usr/bin/ffmpeg}") String ffmpeg,
            @Value("${platform.ffmpeg-worker.sandbox.ffprobe:/usr/bin/ffprobe}") String ffprobe) {
        this.backends = java.util.Objects.requireNonNull(backends, "backends");
        this.workRoot = Path.of(workRoot).toAbsolutePath().normalize();
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    /**
     * Non-Spring composition for integrations and focused tests: a registry backed by the thumbnail
     * worker-runtime backend, which is enough to exercise the thumbnail profile. The cover profile
     * still requires the worker context's cover sandbox backend and fails closed without it.
     */
    public FfmpegCpuProvider(String ffmpeg, String ffprobe) {
        this(new com.example.platform.providerplugin.execution.RuntimeExecutionBackends(
                        java.util.List.of(new com.example.platform.thumbnail.ThumbnailExecutionBackend())),
                "./.data/cover-image-work", ffmpeg, ffprobe);
    }

    /**
     * Registry-backed composition for tests that supply their own execution backends and do not need
     * to override the worker scratch root.
     */
    public FfmpegCpuProvider(
            ExecutionBackendRegistry backends, String ffmpeg, String ffprobe) {
        this(backends, "./.data/cover-image-work", ffmpeg, ffprobe);
    }

    /**
     * Worker scratch root this provider owns for one invocation. The cover capability runs inside the
     * bubblewrap profile that binds exactly this root read-write, so callers staging cover work must
     * use it (the merged provider's own read-back path is derived from it).
     */
    public Path workDirectory(String idempotencyKey) {
        Path work = workRoot.resolve(idempotencyKey).normalize();
        if (!work.startsWith(workRoot)) {
            throw new IllegalArgumentException("invalid frame-extract work key");
        }
        return work;
    }

    @Override
    public FrameExtractManifest manifest() {
        return MANIFEST;
    }

    /** The capability profiles this provider selects from by {@code capabilityId}. */
    public static Map<String, FrameExtractCapabilityProfile> profiles() {
        Map<String, FrameExtractCapabilityProfile> profiles = new LinkedHashMap<>();
        profiles.put(CoverImageContracts.CAPABILITY, new FrameExtractCapabilityProfile(
                CoverImageContracts.CAPABILITY,
                CoverImageContracts.CAPABILITY_VERSION,
                COVER_TIMEOUT_SECONDS,
                MINIMUM_WIDTH,
                COVER_MAXIMUM_WIDTH,
                MAXIMUM_INPUT_BYTES,
                "ffmpeg",
                Set.of("video/mp4", "video/quicktime", "video/webm", "video/x-matroska"),
                Set.of("image/png", "image/jpeg"),
                "sandbox-bwrap",
                "cover-image-sandbox"));
        profiles.put(ThumbnailContracts.CAPABILITY, new FrameExtractCapabilityProfile(
                ThumbnailContracts.CAPABILITY,
                ThumbnailContracts.CAPABILITY_VERSION,
                THUMBNAIL_TIMEOUT_SECONDS,
                MINIMUM_WIDTH,
                THUMBNAIL_MAXIMUM_WIDTH,
                MAXIMUM_INPUT_BYTES,
                "ffmpeg+ffprobe",
                Set.of("video/*"),
                Set.of("image/png", "image/jpeg"),
                "trusted-provider",
                "worker-runtime.local-process"));
        return Map.copyOf(profiles);
    }

    private static FrameExtractManifest manifest(Map<String, FrameExtractCapabilityProfile> profiles) {
        List<FrameExtractCapabilityDeclaration> declarations = profiles.values().stream()
                .map(profile -> new FrameExtractCapabilityDeclaration(
                        profile.capabilityId(), profile.contractVersion()))
                .toList();
        List<FrameExtractCapabilityProfile> all = List.copyOf(profiles.values());
        Set<String> inputFormats = new java.util.TreeSet<>();
        Set<String> outputFormats = new java.util.TreeSet<>();
        int maximumWidth = 0;
        int timeoutSeconds = 0;
        long maximumInputBytes = 0L;
        for (FrameExtractCapabilityProfile profile : all) {
            inputFormats.addAll(profile.inputFormats());
            outputFormats.addAll(profile.outputFormats());
            maximumWidth = Math.max(maximumWidth, profile.maximumWidth());
            timeoutSeconds = Math.max(timeoutSeconds, profile.timeoutSeconds());
            maximumInputBytes = Math.max(maximumInputBytes, profile.maximumInputBytes());
        }
        return new FrameExtractManifest(
                PROVIDER_ID,
                PROVIDER_IMPLEMENTATION_ID,
                PROVIDER_VERSION,
                declarations,
                "ffmpeg+ffprobe",
                inputFormats,
                outputFormats,
                0d,
                86_400d,
                MINIMUM_WIDTH,
                maximumWidth,
                maximumInputBytes,
                timeoutSeconds,
                "platform.ffmpeg",
                "sandbox-or-worker-runtime");
    }

    @Override
    public FrameExtractResult render(
            String capabilityId,
            Path input,
            Path workDirectory,
            String imageFormat,
            Integer width,
            Integer quality,
            double timestampSeconds,
            BooleanSupplier cancelled) {
        // Fail closed: this implementation only executes capabilities it declares.
        if (!manifest().supports(capabilityId)) {
            return FrameExtractResult.failure("UNSUPPORTED_CAPABILITY");
        }
        FrameExtractCapabilityProfile profile = PROFILES.get(capabilityId);
        if (profile == null) {
            return FrameExtractResult.failure("UNSUPPORTED_CAPABILITY");
        }
        if (cancelled.getAsBoolean()) {
            return FrameExtractResult.failure("CANCELLED");
        }
        if (!isSupportedFormat(imageFormat)) {
            return FrameExtractResult.failure("UNSUPPORTED_PARAMETERS");
        }
        // Parameter authority: the shared capability parameter contract must accept this request
        // before any execution. A request the transport admitted but the shared contract rejects
        // (e.g. a source instant that is not an exact, non-negative rational) fails closed here
        // instead of reaching FFmpeg.
        try {
            typedParameters(timestampSeconds, imageFormat, width, quality);
        } catch (IllegalArgumentException rejectedParameters) {
            return FrameExtractResult.failure("UNSUPPORTED_PARAMETERS");
        }
        if (width != null && (width < profile.minimumWidth() || width > profile.maximumWidth())) {
            return FrameExtractResult.failure("UNSUPPORTED_PARAMETERS");
        }
        try {
            if (!Files.isRegularFile(input)) {
                return FrameExtractResult.failure("INPUT_UNAVAILABLE");
            }
            Files.createDirectories(workDirectory);
            Path output = workDirectory.resolve("frame." + imageFormat).normalize();
            if (!output.startsWith(workDirectory)) {
                return FrameExtractResult.failure("INVALID_OUTPUT_PATH");
            }
            if (CoverImageContracts.CAPABILITY.equals(capabilityId)) {
                return renderCover(profile, input, output, imageFormat, width, quality,
                        timestampSeconds, cancelled);
            }
            return renderThumbnail(profile, input, output, imageFormat, width, quality,
                    timestampSeconds, cancelled);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return FrameExtractResult.failure("CANCELLED");
        } catch (IOException failure) {
            return FrameExtractResult.failure("PROVIDER_IO_FAILED");
        }
    }

    private FrameExtractResult renderCover(
            FrameExtractCapabilityProfile profile,
            Path input,
            Path output,
            String imageFormat,
            Integer width,
            Integer quality,
            double timestampSeconds,
            BooleanSupplier cancelled) throws IOException {
        Files.createDirectories(output.getParent());
        var backend = backends.resolve(TaskCapability.COVER_IMAGE)
                .orElseThrow(() -> new IllegalStateException(
                        "cover-image execution backend is not registered"));
        List<String> arguments = new ArrayList<>(List.of(
                "-hide_banner", "-nostdin", "-y",
                "-ss", Double.toString(timestampSeconds),
                "-i", input.toString(),
                "-frames:v", "1"));
        if (width != null) {
            arguments.addAll(List.of("-vf", "scale=" + width + ":-2"));
        }
        if ("jpeg".equals(imageFormat) && quality != null) {
            arguments.addAll(List.of("-q:v", Integer.toString(jpegQuality(quality))));
        }
        arguments.add(output.toString());
        var execution = backend.execute(ExecutionRequest.of(
                "frame-extract:cover-image:" + output.getParent().getFileName(),
                output.getParent().getFileName().toString(),
                TaskCapability.COVER_IMAGE,
                arguments,
                profile.timeoutSeconds()));
        if (cancelled.getAsBoolean()) {
            return FrameExtractResult.failure("CANCELLED");
        }
        if (!execution.success() || !Files.isRegularFile(output)) {
            return FrameExtractResult.failure(execution.errorCode() == null
                    ? "PROVIDER_EXECUTION_FAILED"
                    : execution.errorCode());
        }
        return read(output, imageFormat);
    }

    private FrameExtractResult renderThumbnail(
            FrameExtractCapabilityProfile profile,
            Path input,
            Path output,
            String imageFormat,
            Integer width,
            Integer quality,
            double timestampSeconds,
            BooleanSupplier cancelled) throws IOException, InterruptedException {
        double duration = probe(input, profile, cancelled);
        if (!Double.isFinite(duration) || timestampSeconds > duration) {
            return FrameExtractResult.failure("TIMESTAMP_OUT_OF_RANGE");
        }
        if (cancelled.getAsBoolean()) {
            return FrameExtractResult.failure("CANCELLED");
        }
        var backend = backends.resolve(TaskCapability.THUMBNAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "thumbnail execution backend is not registered"));
        List<String> arguments = new ArrayList<>(List.of(
                "-hide_banner", "-nostdin", "-loglevel", "error",
                "-ss", Double.toString(timestampSeconds),
                "-i", input.toString(),
                "-frames:v", "1"));
        if (width != null) {
            arguments.addAll(List.of("-vf", "scale=" + width + ":-2"));
        }
        if ("jpeg".equals(imageFormat) && quality != null) {
            arguments.addAll(List.of("-q:v", Integer.toString(jpegQuality(quality))));
        }
        arguments.add(output.toString());
        var execution = backend.execute(new ExecutionRequest(
                "frame-extract:thumbnail:" + output.getParent().getFileName(),
                output.getParent().getFileName().toString(),
                TaskCapability.THUMBNAIL,
                output.getParent().toString(),
                Map.of(),
                arguments,
                profile.timeoutSeconds(),
                "",
                "",
                Map.of("executable", Path.of(ffmpeg).toString(),
                        "input", input.toString(),
                        "providerId", PROVIDER_ID)));
        if (cancelled.getAsBoolean()) {
            return FrameExtractResult.failure("CANCELLED");
        }
        if (!execution.success() || !Files.isRegularFile(output) || Files.size(output) == 0) {
            return FrameExtractResult.failure("PROVIDER_FAILED");
        }
        return read(output, imageFormat);
    }

    private double probe(
            Path input, FrameExtractCapabilityProfile profile, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        var backend = backends.resolve(TaskCapability.THUMBNAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "thumbnail execution backend is not registered"));
        var execution = backend.execute(new ExecutionRequest(
                "frame-extract:thumbnail:probe",
                "probe",
                TaskCapability.THUMBNAIL,
                input.getParent().toString(),
                Map.of(),
                List.of("-v", "error", "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1", input.toString()),
                profile.timeoutSeconds(),
                "",
                "",
                Map.of("executable", Path.of(ffprobe).toString(),
                        "input", input.toString(),
                        "providerId", PROVIDER_ID)));
        if (!execution.success()) {
            throw new IllegalArgumentException("UNSUPPORTED_MEDIA");
        }
        if (cancelled.getAsBoolean()) {
            throw new InterruptedException("cancelled during probe");
        }
        try {
            return Double.parseDouble(execution.stdout().trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("UNSUPPORTED_MEDIA", failure);
        }
    }

    private static FrameExtractResult read(Path output, String imageFormat) throws IOException {
        byte[] bytes = Files.readAllBytes(output);
        if (bytes.length == 0) {
            return FrameExtractResult.failure("PROVIDER_EMPTY_OUTPUT");
        }
        return FrameExtractResult.success(bytes, contentType(imageFormat));
    }

    private static String contentType(String imageFormat) {
        return "png".equals(imageFormat) ? "image/png" : "image/jpeg";
    }

    /** Maps user quality (1..100) onto FFmpeg's JPEG qscale (1..31). */
    private static int jpegQuality(int quality) {
        int scaled = (int) Math.round(31.0 - (quality / 100.0) * 30.0);
        return Math.max(1, Math.min(31, scaled));
    }

    /**
     * Typed capability parameter authority (shared {@code media.frame-extract} vocabulary). The
     * transport {@code double} is decoded through the shortest round-trip decimal string and the
     * exact {@code decimal -> rational} rule, never through a floating time authority; a value the
     * shared contract rejects fails the render closed before FFmpeg runs.
     */
    private static MediaFrameExtractParametersV1 typedParameters(
            double timestampSeconds, String imageFormat, Integer width, Integer quality) {
        return MediaFrameExtractParametersV1.ofExactSeconds(
                BigDecimal.valueOf(timestampSeconds).toPlainString(),
                encoding(imageFormat, quality),
                width == null ? FrameWidth.Native.NATIVE : new FrameWidth.ExplicitPixels(width));
    }

    private static RasterImageEncoding encoding(String imageFormat, Integer quality) {
        if ("png".equals(imageFormat)) {
            return RasterImageEncoding.Png.PNG;
        }
        return quality == null
                ? RasterImageEncoding.Jpeg.documentedDefault()
                : new RasterImageEncoding.Jpeg(new JpegQuality(quality));
    }

    private static boolean isSupportedFormat(String imageFormat) {
        return "png".equals(imageFormat) || "jpeg".equals(imageFormat);
    }
}
