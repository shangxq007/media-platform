package com.example.platform.storage.app;

import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * GCR-2 (ARTIFACT_AUTHORITY_CONTRACT_V1 C17): storage catalog service is now
 * DATA-PLANE ONLY. The render-output Artifact registration path
 * (registerArtifact / findArtifactsByJob / findArtifact / ArtifactRef record)
 * is REPLACED: canonical Artifact persistence is owned exclusively by
 * artifact-module (JooqArtifactCommitService + ArtifactRepository). Storage
 * keeps physical operations (BlobStorage providers, object read/write) and
 * provider inventory.
 */
@Service
public class StorageCatalogService implements StorageCatalogPort {

    private final List<BlobStorage> providers;

    public StorageCatalogService(List<BlobStorage> providers) {
        this.providers = providers;
    }

    @Override
    public List<String> providerCodes() {
        return providers.stream().map(BlobStorage::code).toList();
    }
}
