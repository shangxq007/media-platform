package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GCR-2 TEST GROUP T — Timeline Artifact pin reference-integrity validation
 * (Roadmap #14 closure).
 *
 * T1 valid pin → PASS
 * T2 missing Artifact → FAIL CLOSED
 * T3 digest mismatch → FAIL CLOSED
 * T4 cross-tenant → FAIL CLOSED
 */
class TimelineArtifactPinValidatorTest {

    private static final String TENANT = "tenant-1";
    private static final ArtifactId ARTIFACT_ID = new ArtifactId("art-pin-1");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    private ArtifactQueryService queryService;
    private TimelineArtifactPinValidator validator;

    @BeforeEach
    void setUp() {
        queryService = mock(ArtifactQueryService.class);
        validator = new TimelineArtifactPinValidator(queryService);
    }

    private TimelineArtifactPinExtractor.ArtifactPin pin(ArtifactId id, ContentDigest digest) {
        return new TimelineArtifactPinExtractor.ArtifactPin(id, digest);
    }

    @Test
    void t1_validPinPasses() {
        Artifact artifact = new Artifact(ARTIFACT_ID, TENANT, DIGEST, 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER,
                ArtifactState.AVAILABLE, 1, Instant.now());
        when(queryService.getArtifact(TENANT, ARTIFACT_ID)).thenReturn(Optional.of(artifact));

        var result = validator.validate(TENANT, List.of(pin(ARTIFACT_ID, DIGEST)));
        assertTrue(result.valid());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void t2_missingArtifactFailsClosed() {
        when(queryService.getArtifact(TENANT, ARTIFACT_ID)).thenReturn(Optional.empty());

        var result = validator.validate(TENANT, List.of(pin(ARTIFACT_ID, DIGEST)));
        assertFalse(result.valid());
        assertEquals(1, result.violations().size());
        assertTrue(result.violations().get(0).contains("does not exist"));
    }

    @Test
    void t3_digestMismatchFailsClosed() {
        Artifact artifact = new Artifact(ARTIFACT_ID, TENANT, DIGEST, 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER,
                ArtifactState.AVAILABLE, 1, Instant.now());
        when(queryService.getArtifact(TENANT, ARTIFACT_ID)).thenReturn(Optional.of(artifact));
        ContentDigest wrongDigest = ContentDigest.sha256("b".repeat(64));

        var result = validator.validate(TENANT, List.of(pin(ARTIFACT_ID, wrongDigest)));
        assertFalse(result.valid());
        assertTrue(result.violations().get(0).contains("digest mismatch"));
    }

    @Test
    void t4_crossTenantFailsClosed() {
        // Artifact belongs to tenant-2; request is for tenant-1 -> the query
        // (tenant-scoped contract) resolves nothing -> FAIL CLOSED.
        when(queryService.getArtifact(TENANT, ARTIFACT_ID)).thenReturn(Optional.empty());
        when(queryService.getArtifact("tenant-2", ARTIFACT_ID)).thenReturn(Optional.of(
                new Artifact(ARTIFACT_ID, "tenant-2", DIGEST, 1024L,
                        ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER,
                        ArtifactState.AVAILABLE, 1, Instant.now())));

        var result = validator.validate(TENANT, List.of(pin(ARTIFACT_ID, DIGEST)));
        assertFalse(result.valid());
        assertTrue(result.violations().get(0).contains("does not exist for tenant tenant-1"));
    }

    @Test
    void t5_noPinsIsValid() {
        var result = validator.validate(TENANT, List.of());
        assertTrue(result.valid());
        var resultNull = validator.validate(TENANT, null);
        assertTrue(resultNull.valid());
    }
}
