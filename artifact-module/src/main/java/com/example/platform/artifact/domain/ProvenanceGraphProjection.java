package com.example.platform.artifact.domain;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic graph digest for provenance graphs.
 *
 * <p>Same semantic provenance graph → same canonical representation → same digest.
 * Independent of HashMap iteration, edge insertion order, locale, timezone, or machine architecture.
 */
public final class ProvenanceGraphProjection implements Serializable {

    private final String tenantId;
    private final Set<String> artifactIds;
    private final List<String> canonicalEdges;
    private final String graphDigest;

    private ProvenanceGraphProjection(String tenantId, Set<String> artifactIds, List<String> canonicalEdges, String graphDigest) {
        this.tenantId = tenantId;
        this.artifactIds = artifactIds;
        this.canonicalEdges = canonicalEdges;
        this.graphDigest = graphDigest;
    }

    /**
     * Creates a deterministic projection from a set of edges.
     */
    public static ProvenanceGraphProjection fromEdges(String tenantId, Set<String> artifactIds, List<ProvenanceEdge> edges) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(artifactIds, "artifactIds");
        Objects.requireNonNull(edges, "edges");

        // Sort artifact IDs deterministically
        TreeSet<String> sortedArtifacts = new TreeSet<>(artifactIds);

        // Sort edges by canonical form for deterministic ordering
        List<String> sortedCanonical = new ArrayList<>();
        for (ProvenanceEdge edge : edges) {
            sortedCanonical.add(edge.canonicalForm());
        }
        sortedCanonical.sort(String::compareTo);

        // Build canonical representation
        StringBuilder sb = new StringBuilder();
        sb.append("provenanceGraph{tenant=").append(tenantId);
        sb.append(",artifacts=").append(sortedArtifacts);
        sb.append(",edges=[");
        for (int i = 0; i < sortedCanonical.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(sortedCanonical.get(i));
        }
        sb.append("]}");

        String digest = sha256Hex(sb.toString());

        return new ProvenanceGraphProjection(
                tenantId,
                Collections.unmodifiableSet(sortedArtifacts),
                Collections.unmodifiableList(sortedCanonical),
                digest
        );
    }

    public String tenantId() { return tenantId; }
    public Set<String> artifactIds() { return artifactIds; }
    public List<String> canonicalEdges() { return canonicalEdges; }
    public String graphDigest() { return graphDigest; }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProvenanceGraphProjection that)) return false;
        return tenantId.equals(that.tenantId) &&
                artifactIds.equals(that.artifactIds) &&
                canonicalEdges.equals(that.canonicalEdges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, artifactIds, canonicalEdges);
    }

    @Override
    public String toString() {
        return "ProvenanceGraphProjection{tenant=" + tenantId + ", digest=" + graphDigest + "}";
    }
}
