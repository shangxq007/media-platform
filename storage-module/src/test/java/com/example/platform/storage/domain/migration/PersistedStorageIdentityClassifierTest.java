package com.example.platform.storage.domain.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationEvidence;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationInput;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.EvidenceReference;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.Outcome;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistedStorageIdentityClassifierTest {

    private final PersistedStorageIdentityClassifier classifier =
            new PersistedStorageIdentityClassifier();

    @Test
    void outcomeSetIsExactlyTheFrozenThree() {
        assertEquals(
                List.of("CANONICAL_LOGICAL", "LEGACY_PHYSICAL_ENCODED", "AMBIGUOUS"),
                List.of(Outcome.values()).stream().map(Enum::name).toList());
    }

    @Test
    void collisionFreeIssuancePlusNormalizedAuthorityIsCanonical() {
        assertEquals(Outcome.CANONICAL_LOGICAL, classify(new ClassificationEvidence(
                true, true, false, false, false, false,
                List.of(new EvidenceReference("issuance", "receipt:123")))));
    }

    @Test
    void provenPhysicalWriterPlusStorageExistenceIsLegacy() {
        assertEquals(Outcome.LEGACY_PHYSICAL_ENCODED, classify(new ClassificationEvidence(
                false, false, true, true, false, false,
                List.of(new EvidenceReference("writer", "writer:render:42")))));
    }

    @Test
    void conflictingEvidenceIsAmbiguous() {
        assertEquals(Outcome.AMBIGUOUS, classify(new ClassificationEvidence(
                true, true, true, true, true, true, List.of())));
    }

    @Test
    void stringShapeOrTypedProviderCompletionAloneIsInsufficient() {
        ClassificationInput shaped = new ClassificationInput(
                "binding-1", "artifact_replica", "artifact-1:replica-1",
                "obj-looking/provider/path", "classifier-v1", "evidence-v1",
                new ClassificationEvidence(false, false, false, false, true, false, List.of()));
        assertEquals(Outcome.AMBIGUOUS,
                classifier.classify(shaped, Instant.parse("2026-08-30T00:00:00Z")).outcome());
    }

    private Outcome classify(ClassificationEvidence evidence) {
        ClassificationInput input = new ClassificationInput(
                "binding-1", "artifact_replica", "artifact-1:replica-1", "original-value",
                "classifier-v1", "evidence-v1", evidence);
        return classifier.classify(input, Instant.parse("2026-08-30T00:00:00Z")).outcome();
    }
}
