package com.example.platform.sandbox.worker.app;

import com.example.platform.sandbox.worker.config.SandboxWorkerProperties;
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

    public SandboxExecutionService(SandboxWorkerProperties properties) {
        this.properties = properties;
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

            // Build subprocess with minimal environment
            ProcessBuilder pb = new ProcessBuilder(interpreter, scriptFile.toString());
            pb.directory(tempDir.toFile());

            // Minimal environment — no PATH leaks, no secrets
            pb.environment().clear();
            pb.environment().put("PATH", "/usr/bin:/bin");
            pb.environment().put("HOME", tempDir.toString());
            pb.environment().put("LANG", "C.UTF-8");
            pb.environment().put("PYTHONUNBUFFERED", "1");
            pb.environment().put("PYTHONDONTWRITEBYTECODE", "1");

            // Redirect stderr to stdout for capture
            pb.redirectErrorStream(true);

            // Start process
            Process process = pb.start();

            // Read output with timeout
            Future<String> outputFuture = CompletableFuture.supplyAsync(() -> {
                try (InputStream is = process.getInputStream();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                        if (baos.size() > properties.maxOutputBytes() * 2) {
                            // Stop reading if output is way too large
                            break;
                        }
                    }
                    return baos.toString(StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return "";
                }
            });

            String output;
            boolean timedOut = false;
            try {
                output = outputFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut = true;
                process.destroyForcibly();
                output = "";
            } catch (Exception e) {
                process.destroyForcibly();
                output = "";
            }

            // Wait for process to finish (with buffer after timeout)
            if (!timedOut) {
                boolean finished = process.waitFor(
                        properties.timeoutBufferMs(), TimeUnit.MILLISECONDS);
                if (!finished) {
                    timedOut = true;
                    process.destroyForcibly();
                }
            }

            int exitCode = -1;
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException ignored) {
                // Process was killed
            }

            // Truncate output
            String truncatedOutput = truncateOutput(output, properties.maxOutputBytes());
            boolean wasTruncated = output.getBytes(StandardCharsets.UTF_8).length > properties.maxOutputBytes();

            if (timedOut) {
                log.warn("SandboxExecutionService: execution timed out (timeout={}ms)", timeoutMs);
                return SandboxExecutionResult.timeout(timeoutMs);
            }

            if (exitCode == 0) {
                log.info("SandboxExecutionService: execution succeeded (exitCode=0, outputSize={})",
                        truncatedOutput.length());
                return SandboxExecutionResult.success(truncatedOutput, wasTruncated);
            } else {
                log.info("SandboxExecutionService: execution failed (exitCode={}, outputSize={})",
                        exitCode, truncatedOutput.length());
                return SandboxExecutionResult.failed(truncatedOutput, exitCode);
            }

        } catch (IOException e) {
            log.error("SandboxExecutionService: I/O error during execution", e);
            return SandboxExecutionResult.error("I/O error: " + e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("SandboxExecutionService: execution interrupted", e);
            return SandboxExecutionResult.error("Execution interrupted");

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
