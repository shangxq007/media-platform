package com.example.platform.artifact.app;

import com.example.platform.artifact.contract.ArtifactSourceFacts;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;

/** Project-scoped source browser and accepted display metadata; no physical storage coordinates. */
public interface ArtifactSourceCatalog {
    List<SourceEntry> list(String tenantId, String projectId, int limit);
    void recordDisplayName(String tenantId, String projectId, ArtifactId artifactId, ContentDigest digest, String name);
    record SourceEntry(ArtifactId artifactId, ContentDigest digest, long byteLength,
                       String name, ArtifactSourceFacts facts) {}
}
