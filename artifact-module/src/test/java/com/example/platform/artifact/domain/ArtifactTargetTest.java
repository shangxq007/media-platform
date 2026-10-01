package com.example.platform.artifact.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import org.junit.jupiter.api.Test;

class ArtifactTargetTest {

    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    @Test
    void carriesExactArtifactIdentityAndDigest() {
        ArtifactTarget target = new ArtifactTarget(new ArtifactId("art-1"), DIGEST);
        assertEquals("art-1", target.artifactId().value());
        assertEquals(DIGEST, target.contentDigest());
    }

    @Test
    void rejectsMissingFields() {
        assertThrows(NullPointerException.class, () -> new ArtifactTarget(null, DIGEST));
        assertThrows(NullPointerException.class, () -> new ArtifactTarget(new ArtifactId("art-1"), null));
    }

    @Test
    void contentDigestIsPartOfIdentity() {
        ArtifactTarget a = new ArtifactTarget(new ArtifactId("art-1"), DIGEST);
        ArtifactTarget b = new ArtifactTarget(new ArtifactId("art-1"),
                ContentDigest.sha256("b".repeat(64)));
        assertNotEquals(a, b);
    }
}
