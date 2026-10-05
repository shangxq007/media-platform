package com.example.platform.fontpipeline.sanitize;

import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.security.FontSecurityState;
import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxExecutionResult;
import com.example.platform.sandbox.SandboxFailure;
import com.example.platform.sandbox.SandboxFailureCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * OTS-backed {@link FontSanitizer}: runs the {@code ots-sanitize} CLI inside a bubblewrap sandbox.
 *
 * <p><b>Isolation.</b> The sanitizer never spawns a process directly; the only execution entry is
 * {@link LocalSandboxProcess}, which enforces the sandbox filesystem/network/environment/timeout
 * policy. OTS is file-based ({@code ots-sanitize FONT_FILE DEST_FONT_FILE}), so raw bytes are written
 * to a read-only workspace input and the sanitized bytes are read back from the writable
 * {@code .sandbox-output} staging root.
 *
 * <p><b>Outcome mapping.</b> A zero exit with non-empty output → {@code SANITIZED} with
 * {@code contentDigest = SHA-256(sanitized bytes)}. A non-zero exit caused by the workload itself
 * ({@code PROCESS_CRASHED}) → {@code REJECTED} (state {@code RAW}); any infrastructure failure
 * (sandbox unavailable, setup, timeout, launch, missing output) → {@code UNAVAILABLE} (state
 * {@code RAW}). Sanitization is never performed in the JVM.
 */
public final class OtsFontSanitizer implements FontSanitizer {

    /** Must live under {@code /usr/bin} or {@code /bin}: the sandbox host-binary allow-roots. */
    static final String OTS_EXECUTABLE = "/usr/bin/ots-sanitize";

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    static final long DEFAULT_CAPTURE_BYTES = 64L * 1024L;

    private static final String WORKING_DIR_NAME = "work";
    private static final String INPUT_FILE_NAME = "input.font";
    private static final String OUTPUT_FILE_NAME = "sanitized.font";

    private final Duration timeout;
    private final long captureBytes;

    public OtsFontSanitizer() {
        this(DEFAULT_TIMEOUT, DEFAULT_CAPTURE_BYTES);
    }

    public OtsFontSanitizer(Duration timeout, long captureBytes) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (captureBytes < 1) {
            throw new IllegalArgumentException("captureBytes must be positive");
        }
        this.timeout = timeout;
        this.captureBytes = captureBytes;
    }

    @Override
    public SanitizedFontResult sanitize(FontSanitizeRequest request) {
        Objects.requireNonNull(request, "request");
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("font-ots-sandbox-");
            Path workingDirectory = Files.createDirectories(workspace.resolve(WORKING_DIR_NAME));
            Path inputFile = workspace.resolve(INPUT_FILE_NAME);
            Files.write(inputFile, request.rawBytes(), StandardOpenOption.CREATE_NEW);
            Path outputFile = workspace.resolve(".sandbox-output").resolve(OUTPUT_FILE_NAME);

            SandboxExecutionResult execution = LocalSandboxProcess.execute(
                    List.of(OTS_EXECUTABLE, inputFile.toString(), outputFile.toString()),
                    workspace,
                    workingDirectory,
                    Set.of(inputFile),
                    Map.of(),
                    timeout,
                    captureBytes,
                    SandboxCancellation.never());

            return classify(request, execution, outputFile);
        } catch (IOException e) {
            throw new FontSanitizationException("OTS sanitization infrastructure failure", e);
        } finally {
            cleanup(workspace);
        }
    }

    private static SanitizedFontResult classify(
            FontSanitizeRequest request, SandboxExecutionResult execution, Path outputFile) throws IOException {
        List<String> diagnostics = diagnostics(execution);
        Optional<SandboxFailure> failure = execution.failure();
        boolean infrastructureFailure =
                failure.isPresent() && failure.get().code() != SandboxFailureCode.PROCESS_CRASHED;
        boolean hasExitCode = execution.exitCode().isPresent();
        int exitCode = hasExitCode ? execution.exitCode().getAsInt() : -1;

        if (!infrastructureFailure && hasExitCode && exitCode == 0) {
            byte[] sanitizedBytes = Files.exists(outputFile) ? Files.readAllBytes(outputFile) : new byte[0];
            if (sanitizedBytes.length == 0) {
                return unavailable(request, diagnostics, "ots-sanitize produced no output bytes");
            }
            return new SanitizedFontResult(
                    FontSecurityState.SANITIZED,
                    sanitizedBytes,
                    request.sourceDigest(),
                    FontContentDigest.ofBytes(sanitizedBytes),
                    SanitizationOutcome.SANITIZED,
                    diagnostics);
        }
        if (!infrastructureFailure && hasExitCode) {
            return new SanitizedFontResult(
                    FontSecurityState.RAW,
                    null,
                    request.sourceDigest(),
                    null,
                    SanitizationOutcome.REJECTED,
                    diagnostics);
        }
        return unavailable(request, diagnostics, failure.map(SandboxFailure::message).orElse(""));
    }

    private static SanitizedFontResult unavailable(
            FontSanitizeRequest request, List<String> diagnostics, String reason) {
        List<String> all = new ArrayList<>(diagnostics);
        if (reason != null && !reason.isBlank()) {
            all.add(reason);
        }
        return new SanitizedFontResult(
                FontSecurityState.RAW,
                null,
                request.sourceDigest(),
                null,
                SanitizationOutcome.UNAVAILABLE,
                all);
    }

    private static List<String> diagnostics(SandboxExecutionResult execution) {
        List<String> lines = new ArrayList<>();
        for (String line : execution.stderr().utf8().split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        for (String line : execution.stdout().utf8().split("\\R")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void cleanup(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(OtsFontSanitizer::deleteQuietly);
        } catch (IOException ignored) {
            // Best-effort cleanup of the sanitizer's own temporary workspace.
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort.
        }
    }
}
