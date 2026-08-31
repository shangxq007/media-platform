package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArtifactPinIdentityTest {

    @Test
    void uuidSizedInputsProduceDeterministicVersionedBoundedDistinctIdentities() {
        String artifactId = UUID.randomUUID().toString();
        String revisionId = UUID.randomUUID().toString();
        String identity = ArtifactPinIdentity.forRevisionArtifact(
                "tenant-a", "project-a", revisionId, artifactId);

        assertEquals(identity, ArtifactPinIdentity.forRevisionArtifact(
                "tenant-a", "project-a", revisionId, artifactId));
        assertTrue(identity.startsWith("p1"));
        assertEquals(64, identity.length());
        assertNotEquals(identity, ArtifactPinIdentity.forRevisionArtifact(
                "tenant-a", "project-a", UUID.randomUUID().toString(), artifactId));
        assertNotEquals(identity, ArtifactPinIdentity.forRevisionArtifact(
                "tenant-a", "project-a", revisionId, UUID.randomUUID().toString()));
    }
}
