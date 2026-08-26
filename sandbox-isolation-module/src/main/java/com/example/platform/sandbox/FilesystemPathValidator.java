package com.example.platform.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@org.springframework.modulith.NamedInterface("API")
public final class FilesystemPathValidator {
    private FilesystemPathValidator() {}

    public static Optional<SandboxFailureCode> validateWorkingDirectory(Path workspace, Path working) {
        return validateWithin(workspace, working);
    }

    public static Optional<SandboxFailureCode> validateOutput(Path outputRoot, Path output) {
        return validateWithin(outputRoot, output);
    }

    public static Optional<SandboxFailureCode> validateWithin(Path root, Path candidate) {
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedCandidate = candidate.toAbsolutePath().normalize();
            if (!normalizedCandidate.startsWith(normalizedRoot)) return violation();
            Path realRoot = nearestExisting(normalizedRoot).toRealPath();
            Path existingCandidate = nearestExisting(normalizedCandidate);
            Path realExistingCandidate = existingCandidate.toRealPath();
            if (!realExistingCandidate.startsWith(realRoot)) return violation();
            return Optional.empty();
        } catch (IOException | SecurityException failure) {
            return violation();
        }
    }

    public static Optional<SandboxFailureCode> validateExactNoSymlink(Path path) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.exists(normalized) && !normalized.equals(normalized.toRealPath())) return violation();
            return Optional.empty();
        } catch (IOException | SecurityException failure) {
            return violation();
        }
    }

    private static Path nearestExisting(Path path) throws IOException {
        Path current = path;
        while (current != null && !Files.exists(current)) current = current.getParent();
        if (current == null) throw new IOException("path has no existing ancestor");
        return current;
    }

    private static Optional<SandboxFailureCode> violation() {
        return Optional.of(SandboxFailureCode.FILESYSTEM_POLICY_VIOLATION);
    }
}
