package com.example.platform.shared.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * InputStream wrapper that computes SHA-256 checksum and counts bytes on the fly.
 *
 * <p>Designed for streaming uploads — no need to buffer the entire file in memory.
 * The digest is finalized lazily on the first call to {@link #checksum()}.
 *
 * <pre>
 * try (var cis = new ChecksummingInputStream(originalStream)) {
 *     storage.put(uri, cis, ...);
 *     long size = cis.sizeBytes();
 *     String checksum = cis.checksum();  // sha256:<hex>
 * }
 * </pre>
 */
public final class ChecksummingInputStream extends FilterInputStream {

    private final MessageDigest digest;
    private long bytesRead;
    private String finalChecksum;

    public ChecksummingInputStream(InputStream in) {
        super(in);
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            digest.update((byte) b);
            bytesRead++;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            digest.update(b, off, n);
            bytesRead += n;
        }
        return n;
    }

    /**
     * Returns the total number of bytes read through this stream.
     */
    public long sizeBytes() {
        return bytesRead;
    }

    /**
     * Returns the SHA-256 checksum in format {@code sha256:<hex>}.
     * Must be called after the stream is fully consumed.
     * Subsequent calls return the cached result.
     */
    public String checksum() {
        if (finalChecksum == null) {
            finalChecksum = "sha256:" + toHex(digest.digest());
        }
        return finalChecksum;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
