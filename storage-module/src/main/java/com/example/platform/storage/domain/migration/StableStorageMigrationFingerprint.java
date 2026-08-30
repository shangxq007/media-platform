package com.example.platform.storage.domain.migration;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Length-delimited SHA-256 derivation for deterministic migration keys and fingerprints. */
public final class StableStorageMigrationFingerprint {

    private StableStorageMigrationFingerprint() {}

    public static String sha256(List<String> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part == null) {
                    throw new IllegalArgumentException("fingerprint parts must not be null");
                }
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
