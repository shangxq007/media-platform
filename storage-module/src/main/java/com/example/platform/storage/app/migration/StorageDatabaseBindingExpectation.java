package com.example.platform.storage.app.migration;

import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import java.util.Locale;
import java.util.Objects;

/** Declared binding facts that must be checked against the database actually in use. */
public record StorageDatabaseBindingExpectation(
        String bindingId,
        DatabaseKind databaseKind,
        String deploymentIdentity,
        DeploymentEnvironment environmentIdentity,
        String expectedDatabaseName,
        String expectedSchema,
        String schemaVersion,
        String queryEvidenceVersion,
        String bindingEvidenceRef,
        boolean canonicalRequested) {

    public StorageDatabaseBindingExpectation {
        requireText(bindingId, "bindingId");
        Objects.requireNonNull(databaseKind, "databaseKind");
        requireText(deploymentIdentity, "deploymentIdentity");
        Objects.requireNonNull(environmentIdentity, "environmentIdentity");
        requireText(expectedDatabaseName, "expectedDatabaseName");
        requireText(expectedSchema, "expectedSchema");
        requireText(schemaVersion, "schemaVersion");
        requireText(queryEvidenceVersion, "queryEvidenceVersion");

        if (databaseKind == DatabaseKind.UNCONFIGURED) {
            throw new IllegalArgumentException(
                    "actual database observation requires EXPLICIT or TESTCONTAINERS kind");
        }
        if (databaseKind == DatabaseKind.TESTCONTAINERS
                && environmentIdentity != DeploymentEnvironment.TESTCONTAINERS) {
            throw new IllegalArgumentException(
                    "TESTCONTAINERS kind requires TESTCONTAINERS environment");
        }
        if (databaseKind == DatabaseKind.EXPLICIT && canonicalRequested) {
            if (environmentIdentity == DeploymentEnvironment.UNCONFIGURED
                    || environmentIdentity == DeploymentEnvironment.TESTCONTAINERS) {
                throw new IllegalArgumentException(
                        "explicit canonical binding requires a non-test explicit environment");
            }
            requireText(bindingEvidenceRef, "bindingEvidenceRef");
        }
        rejectConnectionMaterial(bindingEvidenceRef);
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

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
