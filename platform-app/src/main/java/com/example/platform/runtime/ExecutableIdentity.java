package com.example.platform.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * P1-1A.7: the observable identity of one provider executable — its normalized path and the SHA-256 of
 * its exact bytes.
 *
 * <p><b>Internal to the admission-evidence producer.</b> The identity is deliberately not written into
 * a semantic digest, a durable record, or the public runtime-dependency contract (C18 / RA-018). It
 * exists so the producer can prove that the artifact it probed is still the artifact it is reporting
 * on. It is never a claim about the artifact the executor eventually runs.
 */
public record ExecutableIdentity(Path path, String sha256Hex) {

    private static final String SHA_256_HEX = "[0-9a-f]{64}";

    public ExecutableIdentity {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (!sha256Hex.matches(SHA_256_HEX)) {
            throw new IllegalArgumentException("executable identity requires a lowercase SHA-256");
        }
    }

    /**
     * Hashes the exact executable. Returns {@link Optional#empty()} when the artifact is missing,
     * not a regular file, or unreadable, so the caller fails closed rather than guessing.
     */
    public static Optional<ExecutableIdentity> of(Path executable) {
        Objects.requireNonNull(executable, "executable");
        Path normalized = executable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            return Optional.empty();
        }
        try (InputStream bytes = Files.newInputStream(normalized)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = bytes.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return Optional.of(new ExecutableIdentity(
                    normalized, HexFormat.of().formatHex(digest.digest())));
        } catch (IOException | NoSuchAlgorithmException failure) {
            return Optional.empty();
        }
    }
}
