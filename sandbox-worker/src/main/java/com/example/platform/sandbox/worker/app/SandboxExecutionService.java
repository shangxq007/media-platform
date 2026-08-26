package com.example.platform.sandbox.worker.app;

import com.example.platform.sandbox.worker.config.SandboxWorkerProperties;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.sandbox.SandboxProcessExecutionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.*;

/**
 * Executes user-submitted code in an isolated subprocess.
 *
 * <p>Security model:
 * <ul>
 *   <li>Code is written to a temporary directory and executed via `python3` subprocess</li>
 *   <li>Subprocess has a strict timeout (wall clock)</li>
 *   <li>stdout/stderr are truncated to maxOutputBytes</li>
 *   <li>Temporary directory is cleaned up after execution</li>
 *   <li>Environment variables are minimized (no PATH leaks, no secrets)</li>
 *   <li>Code size is limited to maxCodeBytes</li>
 *   <li>Only whitelisted languages are allowed</li>
 * </ul>
 *
 * <p>This is process-level isolation, not container-level. For stronger isolation,
 * run this service in a container with:
 * <ul>
 *   <li>runAsNonRoot</li>
 *   <li>readOnlyRootFilesystem</li>
 *   <li>capabilities.drop: ALL</li>
 *   <li>seccomp RuntimeDefault</li>
 *   <li>resource limits (CPU, memory, PIDs)</li>
 *   <li>networkPolicy denying egress (if code doesn't need network)</li>
 * </ul>
 */
@Service
public class SandboxExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SandboxExecutionService.class);

    private final SandboxWorkerProperties properties;
    private final SandboxProcessExecutionPort processExecution;

    public SandboxExecutionService(
            SandboxWorkerProperties properties, SandboxProcessExecutionPort processExecution) {
        this.properties = properties;
        this.processExecution = processExecution;
    }

    /**
     * Execute code in an isolated subprocess.
     *
     * @param language  the programming language (e.g., "python")
     * @param code      the source code to execute
     * @param timeoutMs maximum wall-clock time in milliseconds
     * @return the execution result
     */
    public SandboxExecutionResult execute(String language, String code, long timeoutMs) {
        // Validate inputs
        if (language == null || language.isBlank()) {
            return SandboxExecutionResult.denied("Language must be specified");
        }
        if (code == null || code.isBlank()) {
            return SandboxExecutionResult.denied("Code must not be blank");
        }
        if (code.getBytes(StandardCharsets.UTF_8).length > properties.maxCodeBytes()) {
            return SandboxExecutionResult.denied(
                    "Code exceeds maximum size of " + properties.maxCodeBytes() + " bytes");
        }

        String normalizedLang = language.toLowerCase().trim();
        if (!isLanguageAllowed(normalizedLang)) {
            return SandboxExecutionResult.denied(
                    "Language '" + language + "' is not supported. Allowed: " + properties.allowedLanguages());
        }

        long effectiveTimeoutMs = Math.min(
                Math.max(timeoutMs, 100),
                properties.maxExecutionSeconds() * 1000L);

        log.info("SandboxExecutionService: executing {} code ({} bytes, timeout={}ms)",
                normalizedLang, code.length(), effectiveTimeoutMs);

        return executeInSubprocess(normalizedLang, code, effectiveTimeoutMs);
    }

    private boolean isLanguageAllowed(String language) {
        return properties.allowedLanguages().stream()
                .anyMatch(lang -> lang.equalsIgnoreCase(language));
    }

    private SandboxExecutionResult executeInSubprocess(String language, String code, long timeoutMs) {
        Path tempDir = null;
        try {
            // Create isolated temporary directory
            tempDir = Files.createTempDirectory("sandbox-");

            // Determine script extension and interpreter
            String extension = getExtension(language);
            String interpreter = getInterpreter(language);

            // Write code to file (not passed via stdin to avoid injection)
            Path scriptFile = tempDir.resolve("main." + extension);
            Files.writeString(scriptFile, code, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            var process = processExecution.execute(
                    java.util.List.of(interpreter, scriptFile.toString()), tempDir, tempDir,
                    java.util.Set.of(scriptFile),
                    java.util.Map.of(
                            "PATH", "/usr/bin:/bin", "HOME", tempDir.toString(), "LANG", "C.UTF-8",
                            "PYTHONUNBUFFERED", "1", "PYTHONDONTWRITEBYTECODE", "1"),
                    java.time.Duration.ofMillis(timeoutMs), properties.maxOutputBytes(),
                    SandboxCancellation.never());
            int exitCode = process.exitCode().orElse(-1);
            String output = process.stdout().utf8();
            String errors = process.stderr().utf8();
            if (process.failure().isPresent()) {
                var failure = process.failure().orElseThrow();
                if (failure.code() == com.example.platform.sandbox.SandboxFailureCode.PROCESS_TIMEOUT) {
                log.warn("SandboxExecutionService: execution timed out (timeout={}ms)", timeoutMs);
                return SandboxExecutionResult.timeout(timeoutMs);
                }
                if (failure.code() == com.example.platform.sandbox.SandboxFailureCode.PROCESS_CRASHED) {
                    return SandboxExecutionResult.failed(process.stderr().utf8(), exitCode);
                }
                return SandboxExecutionResult.error(failure.code() + ": " + failure.message());
            }
            boolean wasTruncated = process.stdout().truncated() || process.stderr().truncated();
            if (exitCode == 0) {
                log.info("SandboxExecutionService: execution succeeded (exitCode=0, outputSize={})",
                        output.length());
                return SandboxExecutionResult.success(output, wasTruncated);
            } else {
                return SandboxExecutionResult.failed(errors, exitCode);
            }

        } catch (IOException e) {
            log.error("SandboxExecutionService: I/O error during execution", e);
            return SandboxExecutionResult.error("I/O error: " + e.getMessage());

        } finally {
            // Clean up temporary directory
            cleanupTempDir(tempDir);
        }
    }

    private static String getExtension(String language) {
        return switch (language) {
            case "python", "py" -> "py";
            default -> "txt";
        };
    }

    private static String getInterpreter(String language) {
        return switch (language) {
            case "python", "py" -> "python3";
            default -> "echo";
        };
    }

    private static String truncateOutput(String output, int maxBytes) {
        if (output == null) return "";
        byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return output;
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8) + "\n[TRUNCATED]";
    }

    private static void cleanupTempDir(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) return;
        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best effort cleanup
                        }
                    });
        } catch (IOException e) {
            log.warn("SandboxExecutionService: failed to cleanup temp dir: {}", tempDir);
        }
    }
}
