package com.example.platform.storage.domain.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Inactive, restartable process-state foundation for later authorized migration phases. */
public final class StorageMigrationJournal {

    private StorageMigrationJournal() {}

    public static JournalSeed seed(
            String databaseBindingId,
            String sourceTable,
            String sourcePrimaryIdentity,
            String expectedOriginalValue,
            String classifierVersion,
            String evidenceVersion,
            List<String> semanticInputs) {
        requireText(databaseBindingId, "databaseBindingId");
        requireText(sourceTable, "sourceTable");
        requireText(sourcePrimaryIdentity, "sourcePrimaryIdentity");
        requireText(expectedOriginalValue, "expectedOriginalValue");
        requireText(classifierVersion, "classifierVersion");
        requireText(evidenceVersion, "evidenceVersion");
        List<String> copiedSemanticInputs = semanticInputs == null ? List.of() : List.copyOf(semanticInputs);

        String migrationKey = StableStorageMigrationFingerprint.sha256(List.of(
                "storage-identity-migration-key-v1",
                databaseBindingId,
                sourceTable,
                sourcePrimaryIdentity,
                expectedOriginalValue,
                classifierVersion,
                evidenceVersion));
        List<String> fingerprintParts = new ArrayList<>(List.of(
                "storage-identity-migration-semantics-v1",
                databaseBindingId,
                sourceTable,
                sourcePrimaryIdentity,
                expectedOriginalValue,
                classifierVersion,
                evidenceVersion));
        fingerprintParts.addAll(copiedSemanticInputs);
        String semanticFingerprint = StableStorageMigrationFingerprint.sha256(fingerprintParts);
        return new JournalSeed(
                migrationKey,
                semanticFingerprint,
                databaseBindingId,
                sourceTable,
                sourcePrimaryIdentity,
                expectedOriginalValue,
                classifierVersion,
                evidenceVersion);
    }

    public enum State {
        PENDING_CLASSIFICATION,
        CLASSIFIED_CANONICAL,
        CLASSIFIED_LEGACY,
        QUARANTINED_AMBIGUOUS,
        ADOPTION_PENDING,
        ADOPTED,
        RECEIPT_RECORDED,
        RECONCILED,
        CAS_PENDING,
        CAS_APPLIED,
        TERMINAL,
        FAILED_REVIEW_REQUIRED
    }

    public record JournalSeed(
            String migrationKey,
            String semanticFingerprint,
            String databaseBindingId,
            String sourceTable,
            String sourcePrimaryIdentity,
            String expectedOriginalValue,
            String classifierVersion,
            String evidenceVersion) {

        public JournalSeed {
            requireSha256(migrationKey, "migrationKey");
            requireSha256(semanticFingerprint, "semanticFingerprint");
            requireText(databaseBindingId, "databaseBindingId");
            requireText(sourceTable, "sourceTable");
            requireText(sourcePrimaryIdentity, "sourcePrimaryIdentity");
            requireText(expectedOriginalValue, "expectedOriginalValue");
            requireText(classifierVersion, "classifierVersion");
            requireText(evidenceVersion, "evidenceVersion");
        }
    }

    public record JournalRecord(
            JournalSeed seed,
            State state,
            long version,
            Instant createdAt,
            Instant updatedAt) {

        public JournalRecord {
            Objects.requireNonNull(seed, "seed");
            Objects.requireNonNull(state, "state");
            if (version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex characters");
        }
    }
}
