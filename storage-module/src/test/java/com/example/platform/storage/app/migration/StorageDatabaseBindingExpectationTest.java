package com.example.platform.storage.app.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import org.junit.jupiter.api.Test;

class StorageDatabaseBindingExpectationTest {

    @Test
    void testcontainersMayRequestCanonicalButObserverMustDecide() {
        assertDoesNotThrow(() -> expectation(
                DatabaseKind.TESTCONTAINERS, DeploymentEnvironment.TESTCONTAINERS,
                "evidence:test-run", true));
    }

    @Test
    void explicitCanonicalRequiresNonTestEnvironmentAndEvidence() {
        assertThrows(IllegalArgumentException.class, () -> expectation(
                DatabaseKind.EXPLICIT, DeploymentEnvironment.TESTCONTAINERS,
                "evidence:test-run", true));
        assertThrows(IllegalArgumentException.class, () -> expectation(
                DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD, null, true));
    }

    @Test
    void connectionMaterialCannotBecomeBindingEvidence() {
        assertThrows(IllegalArgumentException.class, () -> expectation(
                DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                "jdbc:postgresql://db/media?password=secret", true));
        assertThrows(IllegalArgumentException.class, () -> expectation(
                DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                "postgresql://admin:secret@db/media", true));
    }

    private static StorageDatabaseBindingExpectation expectation(
            DatabaseKind kind,
            DeploymentEnvironment environment,
            String evidenceRef,
            boolean canonicalRequested) {
        return new StorageDatabaseBindingExpectation(
                "binding-1", kind, "deployment-a", environment, "media", "public",
                "V1", "query-v1", evidenceRef, canonicalRequested);
    }
}
