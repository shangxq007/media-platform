package com.example.platform.artifact.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for deterministic canonical serialization.
 *
 * <p>Produces stable byte representations independent of HashMap iteration, Locale,
 * Timezone, database row order, OpenDAL runtime state, Storage endpoint, or machine architecture.
 */
public final class CanonicalSerializer {

    private CanonicalSerializer() {
    }

    /**
     * Computes SHA-256 hex digest of a string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes a deterministic digest for an Artifact.
     */
    public static String digestArtifact(Artifact artifact) {
        return sha256Hex(artifact.canonicalForm());
    }

    /**
     * Computes a deterministic digest for an ArtifactDescriptor.
     */
    public static String digestDescriptor(ArtifactDescriptor descriptor) {
        return sha256Hex(descriptor.canonicalForm());
    }

    /**
     * Computes a deterministic digest for a ProvenanceEdge.
     */
    public static String digestEdge(ProvenanceEdge edge) {
        return sha256Hex(edge.canonicalForm());
    }

    /**
     * Computes a deterministic digest for a ProvenanceOperation.
     */
    public static String digestOperation(ProvenanceOperation operation) {
        return sha256Hex(operation.canonicalForm());
    }
}
