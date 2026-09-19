package com.example.platform.media.api;
import java.util.List;
import java.util.Optional;
/** Media-owned registration and scoped asset projections. Callers establish actor authorization;
 * the owner checks tenant context and predicates every mutation on persisted scope. */
public interface MediaAssets {
    /** Trusted scoped coordination snapshot; requires an enclosing owner transaction. Not an authorization grant. */
    Asset publicationSnapshot(String tenantId, String projectId, String assetId);
    void requireReadScope(String tenantId, String projectId);
    void requireRegistrationScope(String tenantId, String projectId);
    Asset register(String tenantId, String projectId, String storageKey, String mediaType, String filename, Long sizeBytes, String checksum);
    Optional<Asset> findById(String tenantId, String assetId);
    List<Asset> listByProject(String tenantId, String projectId);
    boolean delete(String tenantId, String projectId, String assetId, String expectedVersion);
    /** Atomically withdraw publication metadata only for this exact scoped Media version.
     * False means the subject is missing, moved, or version-stale; the caller may withdraw
     * its own listing alone. Authorization and persistence failures are exceptions. */
    boolean archivePublicationIfCurrent(String tenantId, String projectId, String assetId, String expectedVersion);
    void updatePublishStatus(String tenantId, String projectId, String assetId, String expectedStatus, String publishStatus);
}
