package com.example.platform.workerfabric.reuse;

import com.example.platform.shared.digest.ContentDigest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Bounded-root runtime staging mechanics before Artifact authority commit. */
public final class OutputStagingArea {

    private final Path root;

    public OutputStagingArea(Path root) {
        Objects.requireNonNull(root, "root");
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot create staging root", exception);
        }
    }

    public StagedExecutionOutput stage(InputStream providerOutput) throws IOException {
        Objects.requireNonNull(providerOutput, "providerOutput");
        Path staged = Files.createTempFile(root, "provider-output-", ".staged");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        long length = 0;
        boolean complete = false;
        try (InputStream input = providerOutput; var output = Files.newOutputStream(staged)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    length += read;
                }
            }
            complete = true;
        } finally {
            if (!complete) {
                Files.deleteIfExists(staged);
            }
        }
        return new StagedExecutionOutput(
                staged,
                ContentDigest.sha256(HexFormat.of().formatHex(digest.digest())),
                length);
    }
}
