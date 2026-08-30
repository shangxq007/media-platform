package com.example.platform.storage.domain.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StorageDatabaseBindingTest {

    @Test
    void unrelatedTestcontainersDatabaseIsNeverCanonicalAndCountsStayUnknown() {
        StorageDatabaseBinding binding = StorageDatabaseBinding.testcontainers(
                "binding-test", "container:ephemeral", "local-test-run", "isolated_schema",
                "V1", "query-v1", Instant.parse("2026-08-30T00:00:00Z"));

        assertEquals("NO",
                StorageDatabaseBinding.TESTCONTAINERS_DATABASE_IS_CANONICAL_MIGRATION_DATABASE);
        assertFalse(binding.canonical());
        assertEquals(StorageDatabaseBinding.CountStatus.UNKNOWN,
                binding.observeCounts(12, 3, 1).status());
        assertEquals(null, binding.observeCounts(12, 3, 1).total());
    }

    @Test
    void unconfiguredBindingAlsoKeepsCountsUnknown() {
        StorageDatabaseBinding binding = StorageDatabaseBinding.unconfigured(
                "binding-unconfigured", Instant.parse("2026-08-30T00:00:00Z"));
        assertFalse(binding.canonical());
        assertEquals(StorageDatabaseBinding.CountStatus.UNKNOWN,
                binding.observeCounts(0, 0, 0).status());
    }

    @Test
    void directBindingConstructionCannotPersistConnectionMaterialAsEvidence() {
        assertThrows(IllegalArgumentException.class,
                () -> StorageDatabaseBinding.explicitCanonical(
                        "binding-explicit", "postgresql:sha256:" + "a".repeat(64),
                        "deployment-a", StorageDatabaseBinding.DeploymentEnvironment.PROD,
                        "public", "V1", "query-v1",
                        "jdbc:postgresql://db/media?password=secret",
                        Instant.parse("2026-08-30T00:00:00Z")));
    }
}
