package com.example.platform.storage.app.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.storage.app.migration.TrustedStorageDatabaseBindingPolicy.TrustLevel;
import com.example.platform.storage.app.migration.TrustedStorageDatabaseBindingPolicy.TrustedDeploymentConfiguration;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.CountStatus;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import com.example.platform.storage.domain.migration.StorageDatabaseObservation;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrustedStorageDatabaseBindingPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void onlyAttestedExplicitConfigurationCanAuthorizeCanonicalBinding() {
        StorageDatabaseObservation observation = observation(42, "db-a", "public", "host-a:5432");
        StorageDatabaseBinding unattested = new TrustedStorageDatabaseBindingPolicy(
                configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                        TrustLevel.NON_CANONICAL, 42L, "db-a", "public", "evidence:deploy-a"))
                .bind(observation);
        StorageDatabaseBinding attested = new TrustedStorageDatabaseBindingPolicy(
                configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                        TrustLevel.CANONICAL_ATTESTED, 42L, "db-a", "public",
                        "evidence:deploy-a"))
                .bind(observation);

        assertFalse(unattested.canonical());
        assertTrue(attested.canonical());
        assertEquals(CountStatus.UNKNOWN, unattested.observeCounts(1, 2, 3).status());
    }

    @Test
    void testcontainersIsNoncanonicalEvenWithAttestationToken() {
        StorageDatabaseBinding binding = new TrustedStorageDatabaseBindingPolicy(
                configuration(DatabaseKind.TESTCONTAINERS,
                        DeploymentEnvironment.TESTCONTAINERS,
                        TrustLevel.CANONICAL_ATTESTED, null, "test-db", "test-schema", null))
                .bind(observation(100, "test-db", "test-schema", "127.0.0.1:55432"));
        assertFalse(binding.canonical());
        assertEquals(CountStatus.UNKNOWN, binding.observeCounts(10, 2, 1).status());
    }

    @Test
    void databaseSchemaAndStableDatabaseFactsFailClosed() {
        assertThrows(StorageDatabaseBindingMismatchException.class,
                () -> new TrustedStorageDatabaseBindingPolicy(
                        configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                                TrustLevel.CANONICAL_ATTESTED, 42L,
                                "db-a", "public", "evidence:a"))
                        .bind(observation(42, "db-b", "public", "host:5432")));
        assertThrows(StorageDatabaseBindingMismatchException.class,
                () -> new TrustedStorageDatabaseBindingPolicy(
                        configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                                TrustLevel.CANONICAL_ATTESTED, 42L,
                                "db-a", "public", "evidence:a"))
                        .bind(observation(43, "db-a", "public", "host:5432")));
        assertThrows(StorageDatabaseBindingMismatchException.class,
                () -> new TrustedStorageDatabaseBindingPolicy(
                        configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                                TrustLevel.CANONICAL_ATTESTED, 42L,
                                "db-a", "public", "evidence:a"))
                        .bind(observation(42, "db-a", "other", "host:5432")));

        StorageDatabaseBinding dbA = new TrustedStorageDatabaseBindingPolicy(
                configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                        TrustLevel.CANONICAL_ATTESTED, 42L,
                        "db-a", "public", "evidence:a"))
                .bind(observation(42, "db-a", "public", "same:5432"));
        StorageDatabaseBinding dbB = new TrustedStorageDatabaseBindingPolicy(
                configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                        TrustLevel.CANONICAL_ATTESTED, 43L,
                        "db-b", "public", "evidence:b"))
                .bind(observation(43, "db-b", "public", "same:5432"));
        assertNotEquals(dbA.databaseIdentity(), dbB.databaseIdentity());
    }

    @Test
    void endpointAndObservationTimeDoNotParticipateInStableIdentity() {
        TrustedStorageDatabaseBindingPolicy policy = new TrustedStorageDatabaseBindingPolicy(
                configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.STAGING,
                        TrustLevel.CANONICAL_ATTESTED, 42L,
                        "db-a", "public", "evidence:a"));
        StorageDatabaseBinding first = policy.bind(
                observation(42, "db-a", "public", "host-a:5432"));
        StorageDatabaseBinding failover = policy.bind(new StorageDatabaseObservation(
                42, "db-a", "public", "host-b:6432", NOW.plusSeconds(60)));
        assertEquals(first.databaseIdentity(), failover.databaseIdentity());
        assertTrue(first.hasSameStableFacts(failover));
    }

    @Test
    void trustedEvidenceRejectsConnectionMaterial() {
        assertThrows(IllegalArgumentException.class,
                () -> configuration(DatabaseKind.EXPLICIT, DeploymentEnvironment.PROD,
                        TrustLevel.CANONICAL_ATTESTED, 42L, "db-a", "public",
                        "jdbc:postgresql://user:password@host/db"));
    }

    private static StorageDatabaseObservation observation(
            long oid, String database, String schema, String endpoint) {
        return new StorageDatabaseObservation(oid, database, schema, endpoint, NOW);
    }

    private static TrustedDeploymentConfiguration configuration(
            DatabaseKind kind,
            DeploymentEnvironment environment,
            TrustLevel trust,
            Long expectedOid,
            String database,
            String schema,
            String evidence) {
        return new TrustedDeploymentConfiguration(
                "binding-a", kind, "deployment-a", environment, expectedOid,
                database, schema, "V1", "query-v1", evidence, trust);
    }
}
