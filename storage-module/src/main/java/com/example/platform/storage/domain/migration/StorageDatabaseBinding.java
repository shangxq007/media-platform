package com.example.platform.storage.domain.migration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/** Trusted deployment binding to stable observed PostgreSQL and schema facts. */
public record StorageDatabaseBinding(
        String bindingId,
        DatabaseKind databaseKind,
        long databaseOid,
        String databaseName,
        String databaseIdentity,
        String deploymentIdentity,
        DeploymentEnvironment environmentIdentity,
        String schemaIdentity,
        String schemaVersion,
        String queryEvidenceVersion,
        String bindingEvidenceRef,
        boolean canonical,
        Instant firstSeenAt,
        Instant lastObservedAt) {

    public static final String TESTCONTAINERS_DATABASE_IS_CANONICAL_MIGRATION_DATABASE = "NO";

    public StorageDatabaseBinding {
        requireText(bindingId, "bindingId");
        Objects.requireNonNull(databaseKind, "databaseKind");
        if (databaseOid <= 0) {
            throw new IllegalArgumentException("databaseOid must be positive");
        }
        requireText(databaseName, "databaseName");
        requireText(databaseIdentity, "databaseIdentity");
        requireText(deploymentIdentity, "deploymentIdentity");
        Objects.requireNonNull(environmentIdentity, "environmentIdentity");
        requireText(schemaIdentity, "schemaIdentity");
        requireText(schemaVersion, "schemaVersion");
        requireText(queryEvidenceVersion, "queryEvidenceVersion");
        rejectConnectionMaterial(bindingEvidenceRef);
        Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        Objects.requireNonNull(lastObservedAt, "lastObservedAt");
        firstSeenAt = firstSeenAt.truncatedTo(ChronoUnit.MICROS);
        lastObservedAt = lastObservedAt.truncatedTo(ChronoUnit.MICROS);
        if (lastObservedAt.isBefore(firstSeenAt)) {
            throw new IllegalArgumentException("lastObservedAt must not precede firstSeenAt");
        }
        if (databaseKind == DatabaseKind.TESTCONTAINERS && canonical) {
            throw new IllegalArgumentException("Testcontainers database binding cannot be canonical");
        }
        if (canonical) {
            if (databaseKind != DatabaseKind.EXPLICIT
                    || environmentIdentity == DeploymentEnvironment.UNCONFIGURED
                    || environmentIdentity == DeploymentEnvironment.TESTCONTAINERS) {
                throw new IllegalArgumentException(
                        "canonical binding must name an explicit trusted deployment");
            }
            requireText(bindingEvidenceRef, "bindingEvidenceRef");
        }
    }

    public RowCountObservation observeCounts(long canonicalLogical, long legacyPhysical, long ambiguous) {
        if (!canonical) {
            return RowCountObservation.unknown();
        }
        return RowCountObservation.known(canonicalLogical, legacyPhysical, ambiguous);
    }

    public boolean hasSameStableFacts(StorageDatabaseBinding other) {
        return other != null
                && bindingId.equals(other.bindingId)
                && databaseKind == other.databaseKind
                && databaseOid == other.databaseOid
                && databaseName.equals(other.databaseName)
                && databaseIdentity.equals(other.databaseIdentity)
                && deploymentIdentity.equals(other.deploymentIdentity)
                && environmentIdentity == other.environmentIdentity
                && schemaIdentity.equals(other.schemaIdentity)
                && schemaVersion.equals(other.schemaVersion)
                && queryEvidenceVersion.equals(other.queryEvidenceVersion)
                && Objects.equals(bindingEvidenceRef, other.bindingEvidenceRef)
                && canonical == other.canonical;
    }

    public enum DatabaseKind { TESTCONTAINERS, EXPLICIT }

    public enum DeploymentEnvironment {
        UNCONFIGURED, TESTCONTAINERS, DEV, STAGING, PROD
    }

    public enum CountStatus { UNKNOWN, KNOWN }

    public record RowCountObservation(
            CountStatus status,
            Long canonicalLogical,
            Long legacyPhysicalEncoded,
            Long ambiguous,
            Long total) {

        public RowCountObservation {
            Objects.requireNonNull(status, "status");
            if (status == CountStatus.UNKNOWN) {
                if (canonicalLogical != null || legacyPhysicalEncoded != null
                        || ambiguous != null || total != null) {
                    throw new IllegalArgumentException("UNKNOWN row counts must not carry values");
                }
            } else {
                requireNonNegative(canonicalLogical, "canonicalLogical");
                requireNonNegative(legacyPhysicalEncoded, "legacyPhysicalEncoded");
                requireNonNegative(ambiguous, "ambiguous");
                if (!Long.valueOf(canonicalLogical + legacyPhysicalEncoded + ambiguous)
                        .equals(total)) {
                    throw new IllegalArgumentException(
                            "row-count total must equal the three outcomes");
                }
            }
        }

        public static RowCountObservation unknown() {
            return new RowCountObservation(CountStatus.UNKNOWN, null, null, null, null);
        }

        public static RowCountObservation known(long canonical, long legacy, long ambiguous) {
            return new RowCountObservation(
                    CountStatus.KNOWN, canonical, legacy, ambiguous,
                    canonical + legacy + ambiguous);
        }

        private static void requireNonNegative(Long value, String field) {
            if (value == null || value < 0) {
                throw new IllegalArgumentException(field + " must be non-negative");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public static void rejectConnectionMaterial(String evidenceRef) {
        if (evidenceRef == null) {
            return;
        }
        String normalized = evidenceRef.toLowerCase(Locale.ROOT);
        if (normalized.contains("jdbc:")
                || normalized.contains("r2dbc:")
                || normalized.contains("postgresql://")
                || normalized.contains("postgres://")
                || normalized.contains("password")
                || normalized.contains("passwd")
                || normalized.contains("pwd=")
                || normalized.contains("username=")
                || normalized.matches(".*://[^/\\s]*@.*")) {
            throw new IllegalArgumentException(
                    "binding evidence must not contain a database URL or credentials");
        }
    }
}
