package com.example.platform.artifact.domain;
import com.example.platform.shared.identity.ArtifactId;

import java.util.List;
import java.util.Optional;

/**
 * Read-only query service for Artifacts with bounded traversal.
 *
 * <p>All traversals MUST have explicit depth or result limit.
 * No unbounded graph queries.
 * Never returns OpenDAL Operator, cloud SDK objects, raw credentials, or temporary signed URLs.
 */
public interface ArtifactQueryService {

    /**
     * Gets an Artifact by its ID.
     *
     * @param tenantId the tenant (for isolation)
     * @param artifactId the artifact ID
     * @return the artifact, or empty if not found
     */
    Optional<Artifact> getArtifact(String tenantId, ArtifactId artifactId);

    /**
     * Lists replica bindings for an artifact.
     *
     * @param tenantId the tenant
     * @param artifactId the artifact ID
     * @return list of replica bindings
     */
    List<ArtifactReplicaBinding> listReplicas(String tenantId, ArtifactId artifactId);

    /**
     * Lists direct parent artifacts (artifacts this artifact was derived from).
     *
     * @param tenantId the tenant
     * @param artifactId the artifact ID
     * @return list of parent artifact IDs
     */
    List<ArtifactId> listParents(String tenantId, ArtifactId artifactId);

    /**
     * Lists direct child artifacts (artifacts derived from this one).
     *
     * @param tenantId the tenant
     * @param artifactId the artifact ID
     * @return list of child artifact IDs
     */
    List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId);

    /**
     * Gets direct provenance edges for an artifact (both parent and child edges).
     *
     * @param tenantId the tenant
     * @param artifactId the artifact ID
     * @return list of provenance edges
     */
    List<ProvenanceEdge> getDirectProvenance(String tenantId, ArtifactId artifactId);

    /**
     * Bounded ancestor traversal — walks parent links up to maxDepth.
     *
     * @param tenantId the tenant
     * @param artifactId the starting artifact
     * @param maxDepth maximum depth to traverse (must be >= 1)
     * @return list of ancestor artifact IDs (ordered by distance, nearest first)
     */
    List<ArtifactId> boundedAncestorTraversal(String tenantId, ArtifactId artifactId, int maxDepth);

    /**
     * Bounded descendant traversal — walks child links up to maxDepth.
     *
     * @param tenantId the tenant
     * @param artifactId the starting artifact
     * @param maxDepth maximum depth to traverse (must be >= 1)
     * @return list of descendant artifact IDs (ordered by distance, nearest first)
     */
    List<ArtifactId> boundedDescendantTraversal(String tenantId, ArtifactId artifactId, int maxDepth);

    /**
     * Finds artifacts by content digest.
     *
     * @param tenantId the tenant
     * @param contentDigest the content digest to search for
     * @param limit maximum number of results
     * @return list of artifacts with matching digest
     */
    List<Artifact> findByContentDigest(String tenantId, com.example.platform.shared.digest.ContentDigest contentDigest, int limit);
}
