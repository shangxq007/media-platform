package com.example.platform.workerfabric.domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * P1-1A.4 / WORKER_READINESS_EVIDENCE_V1 — bounded-V1 {@code Environment Readiness} probe.
 *
 * <p>Real observation only: for every configured execution root it creates the directory if needed,
 * writes a unique probe file, reads the bytes back and verifies them, then deletes the probe file. It
 * never infers availability from directory existence, static configuration, process liveness or
 * non-zero resources.
 *
 * <p>Verdict rules (owner ruling, option (i)):
 * <ul>
 *   <li>every root fully probed (write + read-back + cleanup) ⇒ {@link RuntimeEnvironmentAvailability#AVAILABLE};</li>
 *   <li>a deterministic inability (permission denied, or the root path is occupied by a file) ⇒
 *       {@link RuntimeEnvironmentAvailability#UNAVAILABLE};</li>
 *   <li>the probe could not be carried out reliably (any other I/O failure, or cleanup failure) ⇒
 *       {@link RuntimeEnvironmentAvailability#UNKNOWN}.</li>
 * </ul>
 * The probe never overwrites user files: it uses a unique dot-prefixed name and removes it in a
 * {@code finally} block. This is readiness observation only — it does not replace execution error
 * handling.
 */
public final class EnvironmentReadinessProbe {

    private static final String PROBE_PREFIX = ".platform-readiness-probe-";

    private final List<Path> requiredRoots;

    public EnvironmentReadinessProbe(List<Path> requiredRoots) {
        Objects.requireNonNull(requiredRoots, "requiredRoots");
        if (requiredRoots.isEmpty()) {
            throw new IllegalArgumentException("at least one required execution root is needed");
        }
        this.requiredRoots = requiredRoots.stream()
                .map(root -> Objects.requireNonNull(root, "requiredRoots element").toAbsolutePath().normalize())
                .toList();
    }

    /** The roots this probe observes (normalized, absolute). */
    public List<Path> requiredRoots() {
        return requiredRoots;
    }

    public Result probe() {
        List<String> diagnostics = new ArrayList<>();
        boolean inconclusive = false;
        for (Path root : requiredRoots) {
            Outcome outcome = probeRoot(root, diagnostics);
            if (outcome == Outcome.UNAVAILABLE) {
                return new Result(RuntimeEnvironmentAvailability.UNAVAILABLE, List.copyOf(diagnostics));
            }
            if (outcome == Outcome.INCONCLUSIVE) {
                inconclusive = true;
            }
        }
        return inconclusive
                ? new Result(RuntimeEnvironmentAvailability.UNKNOWN, List.copyOf(diagnostics))
                : new Result(RuntimeEnvironmentAvailability.AVAILABLE, List.copyOf(diagnostics));
    }

    private enum Outcome {
        OK,
        UNAVAILABLE,
        INCONCLUSIVE
    }

    private static Outcome probeRoot(Path root, List<String> diagnostics) {
        Path probeFile = root.resolve(PROBE_PREFIX + UUID.randomUUID());
        boolean written = false;
        try {
            Files.createDirectories(root);
            byte[] payload = ("readiness:" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
            Files.write(probeFile, payload);
            written = true;
            byte[] readBack = Files.readAllBytes(probeFile);
            if (!Arrays.equals(payload, readBack)) {
                diagnostics.add(root + ": probe read-back mismatch");
                return Outcome.UNAVAILABLE;
            }
            return Outcome.OK;
        } catch (AccessDeniedException denied) {
            diagnostics.add(root + ": permission denied");
            return Outcome.UNAVAILABLE;
        } catch (FileAlreadyExistsException occupied) {
            diagnostics.add(root + ": path is occupied by a non-directory entry");
            return Outcome.UNAVAILABLE;
        } catch (IOException | RuntimeException failure) {
            diagnostics.add(root + ": probe inconclusive (" + failure.getClass().getSimpleName() + ")");
            return Outcome.INCONCLUSIVE;
        } finally {
            if (written) {
                try {
                    Files.deleteIfExists(probeFile);
                } catch (IOException cleanupFailure) {
                    diagnostics.add(root + ": probe cleanup failed");
                }
            }
        }
    }

    /** Verdict plus non-sensitive diagnostics. {@code UNKNOWN} is never reported as available. */
    public record Result(RuntimeEnvironmentAvailability availability, List<String> diagnostics) {

        public Result {
            Objects.requireNonNull(availability, "availability");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
