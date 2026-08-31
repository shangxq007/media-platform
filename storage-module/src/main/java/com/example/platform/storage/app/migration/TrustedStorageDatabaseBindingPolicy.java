package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.identity.StableStorageFingerprint;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import com.example.platform.storage.domain.migration.StorageDatabaseObservation;
import java.util.List;
import java.util.Objects;

/** Decides canonicality from trusted deployment configuration plus observed JDBC facts. */
public final class TrustedStorageDatabaseBindingPolicy {

    private static final String IDENTITY_VERSION = "storage-postgresql-binding-v2";

    private final TrustedDeploymentConfiguration configuration;

    public TrustedStorageDatabaseBindingPolicy(TrustedDeploymentConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public StorageDatabaseBinding bind(StorageDatabaseObservation observation) {
        Objects.requireNonNull(observation, "observation");
        requireExactMatch(
                "database", configuration.expectedDatabaseName(), observation.databaseName());
        requireExactMatch("schema", configuration.expectedSchema(), observation.schemaName());
        if (configuration.expectedDatabaseOid() != null
                && configuration.expectedDatabaseOid() != observation.databaseOid()) {
            throw new StorageDatabaseBindingMismatchException(
                    "connected PostgreSQL database OID does not match trusted configuration");
        }

        boolean canonical = configuration.databaseKind() == DatabaseKind.EXPLICIT
                && configuration.trustLevel() == TrustLevel.CANONICAL_ATTESTED;
        String stableIdentity = StableStorageFingerprint.sha256(List.of(
                IDENTITY_VERSION,
                Long.toString(observation.databaseOid()),
                observation.databaseName()));
        return new StorageDatabaseBinding(
                configuration.bindingId(),
                configuration.databaseKind(),
                observation.databaseOid(),
                observation.databaseName(),
                "postgresql:sha256:" + stableIdentity,
                configuration.deploymentIdentity(),
                configuration.environmentIdentity(),
                observation.schemaName(),
                configuration.schemaVersion(),
                configuration.queryEvidenceVersion(),
                configuration.bindingEvidenceRef(),
                canonical,
                observation.observedAt(),
                observation.observedAt());
    }

    private static void requireExactMatch(String fact, String expected, String observed) {
        if (!expected.equals(observed)) {
            throw new StorageDatabaseBindingMismatchException(
                    "connected PostgreSQL " + fact + " does not match trusted configuration");
        }
    }

    public enum TrustLevel { NON_CANONICAL, CANONICAL_ATTESTED }

    public record TrustedDeploymentConfiguration(
            String bindingId,
            DatabaseKind databaseKind,
            String deploymentIdentity,
            DeploymentEnvironment environmentIdentity,
            Long expectedDatabaseOid,
            String expectedDatabaseName,
            String expectedSchema,
            String schemaVersion,
            String queryEvidenceVersion,
            String bindingEvidenceRef,
            TrustLevel trustLevel) {

        public TrustedDeploymentConfiguration {
            requireText(bindingId, "bindingId");
            Objects.requireNonNull(databaseKind, "databaseKind");
            requireText(deploymentIdentity, "deploymentIdentity");
            Objects.requireNonNull(environmentIdentity, "environmentIdentity");
            if (expectedDatabaseOid != null && expectedDatabaseOid <= 0) {
                throw new IllegalArgumentException("expectedDatabaseOid must be positive");
            }
            requireText(expectedDatabaseName, "expectedDatabaseName");
            requireText(expectedSchema, "expectedSchema");
            requireText(schemaVersion, "schemaVersion");
            requireText(queryEvidenceVersion, "queryEvidenceVersion");
            Objects.requireNonNull(trustLevel, "trustLevel");
            StorageDatabaseBinding.rejectConnectionMaterial(bindingEvidenceRef);
            if (databaseKind == DatabaseKind.TESTCONTAINERS
                    && environmentIdentity != DeploymentEnvironment.TESTCONTAINERS) {
                throw new IllegalArgumentException(
                        "TESTCONTAINERS kind requires TESTCONTAINERS environment");
            }
            if (trustLevel == TrustLevel.CANONICAL_ATTESTED) {
                if (databaseKind == DatabaseKind.EXPLICIT && expectedDatabaseOid == null) {
                    throw new IllegalArgumentException(
                            "canonical attestation requires expected database OID");
                }
                if (databaseKind == DatabaseKind.EXPLICIT
                        && (environmentIdentity == DeploymentEnvironment.UNCONFIGURED
                        || environmentIdentity == DeploymentEnvironment.TESTCONTAINERS)) {
                    throw new IllegalArgumentException(
                            "canonical attestation requires an explicit deployment environment");
                }
                if (databaseKind == DatabaseKind.EXPLICIT
                        && (bindingEvidenceRef == null || bindingEvidenceRef.isBlank())) {
                    throw new IllegalArgumentException(
                            "canonical attestation requires trusted evidence reference");
                }
            }
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
        }
    }
}
