package com.example.platform.render.domain.artifact;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, versioned artifact node in the artifact DAG.
 *
 * <p>Each artifact node represents a single output from a render job execution.
 * Nodes are connected via parentArtifactIds to form a directed acyclic graph (DAG)
 * that tracks lineage and enables incremental rendering.
 *
 * <p>Key properties:
 * <ul>
 *   <li>Immutable once created</li>
 *   <li>Versioned (version increments on re-render)</li>
 *   <li>Hash-based deduplication</li>
 *   <li>Parent lineage tracking</li>
 * </ul>
 */
public record ArtifactNode(
        String id,
        String jobId,
        ArtifactNodeType type,
        String uri,
        List<String> parentArtifactIds,
        Instant createdAt,
        int version,
        String hash,
        Map<String, Object> metadata
) {
    /**
     * Create a new artifact node.
     */
    public static ArtifactNode create(
            String id,
            String jobId,
            ArtifactNodeType type,
            String uri,
            List<String> parentArtifactIds,
            String hash
    ) {
        return new ArtifactNode(
                id,
                jobId,
                type,
                uri,
                parentArtifactIds != null ? List.copyOf(parentArtifactIds) : List.of(),
                Instant.now(),
                1,
                hash,
                Map.of()
        );
    }

    /**
     * Create with metadata.
     */
    public static ArtifactNode createWithMetadata(
            String id,
            String jobId,
            ArtifactNodeType type,
            String uri,
            List<String> parentArtifactIds,
            String hash,
            Map<String, Object> metadata
    ) {
        return new ArtifactNode(
                id,
                jobId,
                type,
                uri,
                parentArtifactIds != null ? List.copyOf(parentArtifactIds) : List.of(),
                Instant.now(),
                1,
                hash,
                metadata != null ? Map.copyOf(metadata) : Map.of()
        );
    }

    /**
     * Create a new version of this artifact (for re-rendering).
     */
    public ArtifactNode withNewVersion(String newUri, String newHash) {
        return new ArtifactNode(
                id,
                jobId,
                type,
                newUri,
                parentArtifactIds,
                Instant.now(),
                version + 1,
                newHash,
                metadata
        );
    }

    /**
     * Check if this artifact has parents.
     */
    public boolean hasParents() {
        return !parentArtifactIds.isEmpty();
    }

    /**
     * Check if this artifact is a root node (no parents).
     */
    public boolean isRoot() {
        return parentArtifactIds.isEmpty();
    }

    /**
     * Get the file extension from the URI.
     */
    public String getFileExtension() {
        if (uri == null) return "";
        int lastDot = uri.lastIndexOf('.');
        return lastDot >= 0 ? uri.substring(lastDot + 1) : "";
    }

    /**
     * Check if this artifact is a video type.
     */
    public boolean isVideo() {
        return type == ArtifactNodeType.VIDEO;
    }

    /**
     * Check if this artifact is an audio type.
     */
    public boolean isAudio() {
        return type == ArtifactNodeType.AUDIO;
    }

    /**
     * Check if this artifact is a timeline JSON.
     */
    public boolean isTimelineJson() {
        return type == ArtifactNodeType.TIMELINE_JSON;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactNode that = (ArtifactNode) o;
        return Objects.equals(id, that.id) && version == that.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
