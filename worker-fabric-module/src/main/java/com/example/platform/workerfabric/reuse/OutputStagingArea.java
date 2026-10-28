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
    private final Object rootIdentity;

    public OutputStagingArea(Path root) {
        Objects.requireNonNull(root, "root");
        this.root = root.toAbsolutePath().normalize();
        try {
            for (Path component=this.root; component!=null; component=component.getParent()) {
                if (Files.isSymbolicLink(component)) throw new IOException("staging root contains a symbolic link");
            }
            Files.createDirectories(this.root);
            var attributes=Files.readAttributes(this.root,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isDirectory() || !this.root.toRealPath().equals(this.root))throw new IOException("staging root is not a canonical directory");
            rootIdentity=attributes.fileKey();
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot create staging root", exception);
        }
    }

    public StagedExecutionOutput stage(InputStream providerOutput) throws IOException {
        Objects.requireNonNull(providerOutput, "providerOutput");
        var attributes=Files.readAttributes(root,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if(!attributes.isDirectory() || !root.toRealPath().equals(root) || !Objects.equals(rootIdentity,attributes.fileKey())) {
            throw new IOException("staging root identity changed");
        }
        Path staged = Files.createTempFile(root, "provider-output-", ".staged");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        long length = 0;
        boolean complete = false;
        try {
          try (InputStream input = providerOutput; var output = Files.newOutputStream(staged,java.nio.file.StandardOpenOption.WRITE,java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while (true) {
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Output staging cancelled");
                read=input.read(buffer);
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Output staging cancelled");
                if(read<0)break;
                if (read > 0) {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    length += read;
                }
            }
          }
            if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Output staging cancelled");
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
