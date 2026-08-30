package com.example.platform.storage.domain.migration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/** Explicit identity of the database and deployment observed by M0/M1 evidence. */
public record StorageDatabaseBinding(
        String bindingId,
        DatabaseKind databaseKind,
        String databaseIdentity,
        String deploymentIdentity,
        DeploymentEnvironment environmentIdentity,
        String schemaIdentity,
        String schemaVersion,
        String queryEvidenceVersion,
        String bindingEvidenceRef,
        boolean canonical,
        Instant observedAt) {

    public static final String TESTCONTAINERS_DATABASE_IS_CANONICAL_MIGRATION_DATABASE = "NO";

    public StorageDatabaseBinding {
        requireText(bindingId, "bindingId");
        Objects.requireNonNull(databaseKind, "databaseKind");
        requireText(databaseIdentity, "databaseIdentity");
        requireText(deploymentIdentity, "deploymentIdentity");
        Objects.requireNonNull(environmentIdentity, "environmentIdentity");
        requireText(schemaIdentity, "schemaIdentity");
        requireText(schemaVersion, "schemaVersion");
        requireText(queryEvidenceVersion, "queryEvidenceVersion");
        rejectConnectionMaterial(bindingEvidenceRef);
        Objects.requireNonNull(observedAt, "observedAt");
        observedAt = observedAt.truncatedTo(ChronoUnit.MICROS);
        if (databaseKind == DatabaseKind.TESTCONTAINERS && canonical) {
            throw new IllegalArgumentException("Testcontainers database binding cannot be canonical");
        }
        if (canonical) {
            if (databaseKind != DatabaseKind.EXPLICIT
                    || environmentIdentity == DeploymentEnvironment.UNCONFIGURED
                    || environmentIdentity == DeploymentEnvironment.TESTCONTAINERS) {
                throw new IllegalArgumentException("canonical binding must name an explicit deployment environment");
            }
            requireText(bindingEvidenceRef, "bindingEvidenceRef");
        }
    }

    public static StorageDatabaseBinding testcontainers(
            String bindingId,
            String databaseIdentity,
            String deploymentIdentity,
            String schemaIdentity,
            String schemaVersion,
            String queryEvidenceVersion,
            Instant observedAt) {
        return new StorageDatabaseBinding(
                bindingId,
                DatabaseKind.TESTCONTAINERS,
                databaseIdentity,
                deploymentIdentity,
                DeploymentEnvironment.TESTCONTAINERS,
                schemaIdentity,
                schemaVersion,
                queryEvidenceVersion,
                null,
                false,
                observedAt);
    }

    public static StorageDatabaseBinding unconfigured(String bindingId, Instant observedAt) {
        return new StorageDatabaseBinding(
                bindingId,
                DatabaseKind.UNCONFIGURED,
                "UNCONFIGURED",
                "UNCONFIGURED",
                DeploymentEnvironment.UNCONFIGURED,
                "UNCONFIGURED",
                "UNCONFIGURED",
                "UNCONFIGURED",
                null,
                false,
                observedAt);
    }

    public static StorageDatabaseBinding explicitCanonical(
            String bindingId,
            String databaseIdentity,
            String deploymentIdentity,
            DeploymentEnvironment environmentIdentity,
            String schemaIdentity,
            String schemaVersion,
            String queryEvidenceVersion,
            String bindingEvidenceRef,
            Instant observedAt) {
        return new StorageDatabaseBinding(
                bindingId,
                DatabaseKind.EXPLICIT,
                databaseIdentity,
                deploymentIdentity,
                environmentIdentity,
                schemaIdentity,
                schemaVersion,
                queryEvidenceVersion,
                bindingEvidenceRef,
                true,
                observedAt);
    }

    public RowCountObservation observeCounts(long canonicalLogical, long legacyPhysical, long ambiguous) {
        if (!canonical) {
            return RowCountObservation.unknown();
        }
        return RowCountObservation.known(canonicalLogical, legacyPhysical, ambiguous);
    }

    public enum DatabaseKind {
        UNCONFIGURED,
        TESTCONTAINERS,
        EXPLICIT
    }

    public enum DeploymentEnvironment {
        UNCONFIGURED,
        TESTCONTAINERS,
        DEV,
        STAGING,
        PROD
    }

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
                if (!Long.valueOf(canonicalLogical + legacyPhysicalEncoded + ambiguous).equals(total)) {
                    throw new IllegalArgumentException("row-count total must equal the three outcomes");
                }
            }
        }

        public static RowCountObservation unknown() {
            return new RowCountObservation(CountStatus.UNKNOWN, null, null, null, null);
        }

        public static RowCountObservation known(long canonical, long legacy, long ambiguous) {
            return new RowCountObservation(
                    CountStatus.KNOWN, canonical, legacy, ambiguous, canonical + legacy + ambiguous);
        }

        private static void requireNonNegative(Long value, String field) {
            if (value == null || value < 0) {
                throw new IllegalArgumentException(field + " must be non-negative");
            }
        }
    }

    public enum CountStatus {
        UNKNOWN,
        KNOWN
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void rejectConnectionMaterial(String evidenceRef) {
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
                    "bindingEvidenceRef must not contain a JDBC URL or credentials");
        }
    }
}
