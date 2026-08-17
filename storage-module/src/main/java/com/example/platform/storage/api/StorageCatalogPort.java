package com.example.platform.storage.api;

import java.util.List;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C17): storage data-plane port.
 *
 * <p>The artifact-registration methods (registerArtifact / findArtifacts* /
 * nested ArtifactRef record) are REMOVED — canonical Artifact persistence is
 * owned exclusively by artifact-module. Render producers/consumers now depend
 * on artifact-module's ArtifactCommitService / ArtifactQueryService contracts.</p>
 */
public interface StorageCatalogPort {

    /** Physical storage provider codes available on the data plane. */
    List<String> providerCodes();
}
