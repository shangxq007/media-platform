package com.example.platform.storage.domain.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Observe-only classifier whose exhaustive persisted outcomes are fixed at exactly three. */
public final class PersistedStorageIdentityClassifier {

    public ClassificationResult classify(ClassificationInput input, Instant observedAt) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(observedAt, "observedAt");
        ClassificationEvidence evidence = input.evidence();

        boolean canonicalProof = evidence.collisionFreeStorageIssuanceProvenance()
                && evidence.normalizedStorageAuthorityCorroborated();
        boolean legacyProof = evidence.collisionFreePhysicalWriterProvenance()
                && evidence.storageOwnedPhysicalExistenceCorroborated();

        Outcome outcome;
        if (evidence.contradictoryEvidence() || canonicalProof == legacyProof) {
            outcome = Outcome.AMBIGUOUS;
        } else if (canonicalProof) {
            outcome = Outcome.CANONICAL_LOGICAL;
        } else {
            outcome = Outcome.LEGACY_PHYSICAL_ENCODED;
        }

        List<EvidenceReference> selectedReferences = evidence.references().stream()
                .sorted(Comparator.comparing(EvidenceReference::type)
                        .thenComparing(EvidenceReference::reference))
                .toList();
        String evidenceFingerprint = StableStorageMigrationFingerprint.sha256(evidenceParts(evidence));
        String classificationId = StableStorageMigrationFingerprint.sha256(List.of(
                "storage-identity-classification-v1",
                input.databaseBindingId(),
                input.sourceTable(),
                input.sourcePrimaryIdentity(),
                input.originalPersistedValue(),
                input.classifierVersion(),
                input.evidenceVersion(),
                evidenceFingerprint));
        return new ClassificationResult(
                classificationId,
                input.databaseBindingId(),
                input.sourceTable(),
                input.sourcePrimaryIdentity(),
                input.originalPersistedValue(),
                input.classifierVersion(),
                input.evidenceVersion(),
                evidenceFingerprint,
                selectedReferences,
                outcome,
                observedAt);
    }

    private static List<String> evidenceParts(ClassificationEvidence evidence) {
        List<String> parts = new ArrayList<>(List.of(
                "storage-identity-classifier-evidence-v1",
                Boolean.toString(evidence.collisionFreeStorageIssuanceProvenance()),
                Boolean.toString(evidence.normalizedStorageAuthorityCorroborated()),
                Boolean.toString(evidence.collisionFreePhysicalWriterProvenance()),
                Boolean.toString(evidence.storageOwnedPhysicalExistenceCorroborated()),
                Boolean.toString(evidence.typedProviderCompletionCorroborated()),
                Boolean.toString(evidence.contradictoryEvidence())));
        evidence.references().stream()
                .sorted(Comparator.comparing(EvidenceReference::type)
                        .thenComparing(EvidenceReference::reference))
                .forEach(reference -> {
                    parts.add(reference.type());
                    parts.add(reference.reference());
                });
        return parts;
    }

    public enum Outcome {
        CANONICAL_LOGICAL,
        LEGACY_PHYSICAL_ENCODED,
        AMBIGUOUS
    }

    public record ClassificationInput(
            String databaseBindingId,
            String sourceTable,
            String sourcePrimaryIdentity,
            String originalPersistedValue,
            String classifierVersion,
            String evidenceVersion,
            ClassificationEvidence evidence) {

        public ClassificationInput {
            requireText(databaseBindingId, "databaseBindingId");
            requireText(sourceTable, "sourceTable");
            requireText(sourcePrimaryIdentity, "sourcePrimaryIdentity");
            requireText(originalPersistedValue, "originalPersistedValue");
            requireText(classifierVersion, "classifierVersion");
            requireText(evidenceVersion, "evidenceVersion");
            Objects.requireNonNull(evidence, "evidence");
        }
    }

    public record ClassificationEvidence(
            boolean collisionFreeStorageIssuanceProvenance,
            boolean normalizedStorageAuthorityCorroborated,
            boolean collisionFreePhysicalWriterProvenance,
            boolean storageOwnedPhysicalExistenceCorroborated,
            boolean typedProviderCompletionCorroborated,
            boolean contradictoryEvidence,
            List<EvidenceReference> references) {

        public ClassificationEvidence {
            references = references == null ? List.of() : List.copyOf(references);
        }
    }

    public record EvidenceReference(String type, String reference) {
        public EvidenceReference {
            requireText(type, "type");
            requireText(reference, "reference");
        }
    }

    public record ClassificationResult(
            String classificationId,
            String databaseBindingId,
            String sourceTable,
            String sourcePrimaryIdentity,
            String originalPersistedValue,
            String classifierVersion,
            String evidenceVersion,
            String evidenceFingerprint,
            List<EvidenceReference> selectedEvidenceReferences,
            Outcome outcome,
            Instant observedAt) {

        public ClassificationResult {
            requireText(classificationId, "classificationId");
            requireText(databaseBindingId, "databaseBindingId");
            requireText(sourceTable, "sourceTable");
            requireText(sourcePrimaryIdentity, "sourcePrimaryIdentity");
            requireText(originalPersistedValue, "originalPersistedValue");
            requireText(classifierVersion, "classifierVersion");
            requireText(evidenceVersion, "evidenceVersion");
            requireText(evidenceFingerprint, "evidenceFingerprint");
            selectedEvidenceReferences = List.copyOf(selectedEvidenceReferences);
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(observedAt, "observedAt");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
