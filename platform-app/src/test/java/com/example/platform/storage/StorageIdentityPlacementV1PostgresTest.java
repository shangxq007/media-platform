package com.example.platform.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceCommand;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StorageWriteIntentRecovery;
import com.example.platform.storage.api.StorageWriteIntentRecovery.BeginWriteIntentCommand;
import com.example.platform.storage.api.StorageWriteIntentRecovery.CompleteWriteIntentCommand;
import com.example.platform.storage.app.identity.CanonicalStorageObjectIssuanceService;
import com.example.platform.storage.app.identity.CanonicalStorageWriteIntentRecoveryService;
import com.example.platform.storage.app.identity.StorageIssuanceConflictException;
import com.example.platform.storage.app.identity.StorageObjectAuthorityRepository;
import com.example.platform.storage.app.identity.StorageWriteIntentRepository;
import com.example.platform.storage.app.migration.StorageDatabaseBindingObserver;
import com.example.platform.storage.app.migration.StorageDatabaseBindingRepository;
import com.example.platform.storage.app.migration.TrustedStorageDatabaseBindingPolicy;
import com.example.platform.storage.app.migration.TrustedStorageDatabaseBindingPolicy.TrustLevel;
import com.example.platform.storage.app.migration.TrustedStorageDatabaseBindingPolicy.TrustedDeploymentConfiguration;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import com.example.platform.storage.domain.identity.StorageWriteIntent.State;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.CountStatus;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import com.example.platform.storage.domain.migration.StorageDatabaseObservation;
import com.example.platform.storage.infrastructure.identity.JdbcStorageObjectAuthorityRepository;
import com.example.platform.storage.infrastructure.identity.JdbcStorageWriteIntentRepository;
import com.example.platform.storage.infrastructure.migration.JdbcStorageDatabaseBindingObserver;
import com.example.platform.storage.infrastructure.migration.JdbcStorageDatabaseBindingRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Real-PostgreSQL proof for the corrected bounded M0/M1 canonical V1 schema. */
class StorageIdentityPlacementV1PostgresTest extends PostgresTestContainerSupport {

    private static final String SCHEMA = isolatedSchemaName();
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);
    private static DataSource adminDataSource;
    private static DataSource testDataSource;
    private static JdbcTemplate jdbc;
    private static StorageObjectIssuance issuance;
    private static StorageWriteIntentRecovery recovery;
    private static StorageDatabaseBindingObserver bindingObserver;
    private static StorageDatabaseBindingRepository bindingRepository;
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void migrateCanonicalV1AndCreateTransactionalBoundaries() throws Exception {
        adminDataSource = createDataSource();
        try (Connection connection = adminDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create schema " + SCHEMA);
        }
        Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations("classpath:db/migration")
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .load()
                .migrate();

        DriverManagerDataSource scoped = new DriverManagerDataSource();
        String separator = jdbcUrl().contains("?") ? "&" : "?";
        scoped.setUrl(jdbcUrl() + separator + "currentSchema=" + SCHEMA);
        scoped.setUsername(username());
        scoped.setPassword(password());
        scoped.setDriverClassName(driverClassName());
        testDataSource = scoped;

        context = new AnnotationConfigApplicationContext();
        context.register(TestTransactionConfiguration.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        issuance = context.getBean(StorageObjectIssuance.class);
        recovery = context.getBean(StorageWriteIntentRecovery.class);
        bindingObserver = context.getBean(StorageDatabaseBindingObserver.class);
        bindingRepository = context.getBean(StorageDatabaseBindingRepository.class);
    }

    @AfterAll
    static void closeResources() throws Exception {
        if (context != null) {
            context.close();
        }
        if (adminDataSource != null) {
            try (Connection connection = adminDataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + SCHEMA + " cascade");
            }
        }
        closeDataSource(adminDataSource);
    }

    @Test
    void canonicalTablesAreNormalizedAndCompatibilityScaffoldingIsAbsent() {
        for (String table : new String[] {
                "storage_database_binding", "storage_logical_object",
                "storage_object_placement", "storage_placement_receipt",
                "storage_write_intent"
        }) {
            assertEquals(1, tableCount(table));
        }
        for (String removed : new String[] {
                "storage_identity_classification",
                "storage_identity_classification_evidence",
                "storage_identity_migration_journal"
        }) {
            assertEquals(0, tableCount(removed));
        }
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = ?
                   and table_name in ('storage_logical_object', 'storage_object_placement',
                                      'storage_placement_receipt', 'storage_write_intent')
                   and data_type in ('json', 'jsonb')
                """, Integer.class, SCHEMA));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                 where table_schema = ? and table_name = 'artifact_replica'
                   and constraint_type = 'FOREIGN KEY'
                   and constraint_name like '%storage_logical%'
                """, Integer.class, SCHEMA), "M2+ Artifact cutover must remain absent");
    }

    @Test
    void ownerScopedIssuanceSupportsOptionalProjectAndNamespaceHasNoAuthority() {
        IssuanceResult noProject = issuance.issue(command(
                StorageOwnershipScope.tenant("tenant-owner-a"), "owner-key",
                "replica-owner-a", "unrelated-placement-tenant"));
        IssuanceResult otherTenant = issuance.issue(command(
                new StorageOwnershipScope("tenant-owner-b", "project-b"), "owner-key",
                "replica-owner-b", "unrelated-placement-tenant"));

        assertNotEquals(noProject.objectId(), otherTenant.objectId());
        assertEquals("tenant-owner-a", jdbc.queryForObject("""
                select o.tenant_id from storage_placement_receipt r
                  join storage_logical_object o on o.object_id = r.object_id
                 where r.receipt_id = ?
                """, String.class, noProject.receipt().receiptId()));
        assertEquals("unrelated-placement-tenant", jdbc.queryForObject("""
                select namespace_tenant_id from storage_object_placement where replica_id = ?
                """, String.class, noProject.placement().replicaId().value()));
        assertThrows(StorageIssuanceConflictException.class, () -> issuance.issue(command(
                new StorageOwnershipScope("tenant-owner-a", "other-project"), "owner-key",
                "replica-owner-other", "tenant-owner-a")));
    }

    @Test
    void originalReplayUsesExactReceiptWhenObjectHasMultiplePlacementsAndReceipts() {
        IssuanceCommand command = command(
                new StorageOwnershipScope("tenant-multi", "project-multi"),
                "multi-key", "replica-multi-a", "namespace-a");
        IssuanceResult original = issuance.issue(command);
        insertPlacement(original.objectId().value(), "replica-multi-b", "correlation-multi-b");
        insertAdditionalReceipt(
                original.objectId().value(), "replica-multi-b", "receipt-multi-b",
                "receipt-key-b", "correlation-multi-b");
        DataIntegrityViolationException impersonation = assertThrows(
                DataIntegrityViolationException.class,
                () -> insertReceipt(
                        original.objectId().value(), "replica-multi-b",
                        "receipt-multi-b-impersonation", "receipt-key-b",
                        "correlation-multi-b", "ORIGINAL_ISSUANCE"));
        assertSqlState23514(impersonation);

        IssuanceResult replay = issuance.issue(command);
        assertEquals(original, replay);
        assertEquals("replica-multi-a", replay.placement().replicaId().value());
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from storage_object_placement where object_id = ?",
                Integer.class, original.objectId().value()));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from storage_placement_receipt
                 where receipt_id = 'receipt-multi-b'
                   and receipt_purpose = 'ADDITIONAL_PLACEMENT'
                """, Integer.class));
    }

    @Test
    void receiptExactMatchAndUpdateDeleteImmutabilityFailClosed() {
        IssuanceResult issued = issuance.issue(command(
                new StorageOwnershipScope("tenant-receipt", "project-receipt"),
                "receipt-key", "replica-receipt", "different-namespace-owner"));
        insertPlacement(issued.objectId().value(), "replica-extra", "correlation-extra");

        DataIntegrityViolationException mismatch = assertThrows(
                DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into storage_placement_receipt (
                    receipt_id, idempotency_key, semantic_fingerprint, receipt_purpose,
                    object_id, replica_id, provider_id, namespace_tenant_id,
                    namespace_project_id, namespace_class, region_policy,
                    data_classification, opaque_locator, provider_version_token, region,
                    placement_state, committed_digest_algorithm, committed_digest, committed_length,
                    provider_correlation_id, issued_at
                ) values ('receipt-mismatch', 'mismatch-key', ?, 'ADDITIONAL_PLACEMENT',
                          ?, 'replica-extra', 'provider-test', 'namespace-fixture', null,
                          'DERIVED', 'SINGLE_REGION', 'INTERNAL', 'opaque/replica-extra',
                          'version-1', 'region-test', 'AVAILABLE', 'SHA_256', ?, 999,
                          'correlation-extra', now())
                """, FINGERPRINT, issued.objectId().value(), "0".repeat(64)));
        assertSqlState23514(mismatch);
        DataIntegrityViolationException update = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("update storage_placement_receipt set committed_length = 99 "
                        + "where receipt_id = ?", issued.receipt().receiptId()));
        assertSqlState23514(update);
        DataIntegrityViolationException delete = assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbc.update("delete from storage_placement_receipt where receipt_id = ?",
                        issued.receipt().receiptId()));
        assertSqlState23514(delete);
    }

    @Test
    void providerCompletedIntentSurvivesCanonicalFailureAndCompletesWithoutNewObject() {
        IssuanceResult fixtureOwner = issuance.issue(command(
                StorageOwnershipScope.tenant("tenant-fixture"), "fixture-key",
                "replica-fixture-owner", "namespace-fixture"));
        insertPlacement(fixtureOwner.objectId().value(),
                "replica-recovery-collision", "fixture-correlation");

        BeginWriteIntentCommand begin = new BeginWriteIntentCommand(
                StorageOwnershipScope.tenant("tenant-recovery"),
                new IssuanceIdempotencyKey("recovery-key"), FINGERPRINT,
                "provider-request-recovery");
        StorageWriteIntent intent = recovery.beginOrResume(begin);
        BackendPlacementResult placement = placement(
                "replica-recovery-collision", "namespace-recovery", "correlation-recovery");
        recovery.recordProviderCompleted(intent.writeIntentId(), placement);

        assertThrows(DataIntegrityViolationException.class,
                () -> recovery.complete(new CompleteWriteIntentCommand(
                        intent.writeIntentId(), placement)));
        assertEquals(State.PROVIDER_COMPLETED, intentState(intent.writeIntentId()));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from storage_placement_receipt where object_id = ?",
                Integer.class, intent.objectId().value()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from storage_object_placement where replica_id = ?",
                Integer.class, "replica-recovery-collision"),
                "canonical failure must not delete physical placement facts automatically");

        jdbc.update("delete from storage_object_placement where replica_id = ?",
                "replica-recovery-collision");
        StorageWriteIntent resumed = recovery.beginOrResume(begin);
        assertEquals(intent.objectId(), resumed.objectId());
        IssuanceResult completed = recovery.complete(new CompleteWriteIntentCommand(
                intent.writeIntentId(), placement));
        assertEquals(intent.objectId(), completed.objectId());
        assertEquals(completed, recovery.complete(new CompleteWriteIntentCommand(
                intent.writeIntentId(), placement)));
        assertEquals(State.CANONICAL_COMMITTED, intentState(intent.writeIntentId()));
    }

    @Test
    void trustedDatabaseBindingIsStableAcrossObservationTimeAndEndpointFailover() {
        StorageDatabaseObservation observed = bindingObserver.observe();
        TrustedDeploymentConfiguration trusted = configuration(
                "binding-canonical", DatabaseKind.EXPLICIT, DeploymentEnvironment.STAGING,
                observed.databaseOid(), observed.databaseName(), observed.schemaName(),
                TrustLevel.CANONICAL_ATTESTED, "evidence:trusted-deployment");
        TrustedStorageDatabaseBindingPolicy policy =
                new TrustedStorageDatabaseBindingPolicy(trusted);
        StorageDatabaseBinding first = bindingRepository.recordObservation(policy.bind(observed));
        StorageDatabaseObservation laterEndpoint = new StorageDatabaseObservation(
                observed.databaseOid(), observed.databaseName(), observed.schemaName(),
                "failover-host:6432", observed.observedAt().plusSeconds(60));
        StorageDatabaseBinding replay =
                bindingRepository.recordObservation(policy.bind(laterEndpoint));

        assertTrue(first.canonical());
        assertEquals(first.databaseIdentity(), replay.databaseIdentity());
        assertEquals(first.firstSeenAt(), replay.firstSeenAt());
        assertEquals(laterEndpoint.observedAt(), replay.lastObservedAt());

        TrustedStorageDatabaseBindingPolicy changedDatabase =
                new TrustedStorageDatabaseBindingPolicy(configuration(
                        "binding-canonical", DatabaseKind.EXPLICIT,
                        DeploymentEnvironment.STAGING, observed.databaseOid() + 1,
                        "different-database", observed.schemaName(),
                        TrustLevel.CANONICAL_ATTESTED, "evidence:other-deployment"));
        StorageDatabaseObservation sameEndpointDifferentFacts = new StorageDatabaseObservation(
                observed.databaseOid() + 1, "different-database", observed.schemaName(),
                observed.endpointDiagnostic(), observed.observedAt().plusSeconds(120));
        assertThrows(IllegalStateException.class,
                () -> bindingRepository.recordObservation(
                        changedDatabase.bind(sameEndpointDifferentFacts)));
    }

    @Test
    void testcontainersBindingRemainsNoncanonicalAndCountsUnattested() {
        StorageDatabaseObservation observed = bindingObserver.observe();
        TrustedStorageDatabaseBindingPolicy policy = new TrustedStorageDatabaseBindingPolicy(
                configuration("binding-testcontainers", DatabaseKind.TESTCONTAINERS,
                        DeploymentEnvironment.TESTCONTAINERS, null,
                        observed.databaseName(), observed.schemaName(),
                        TrustLevel.CANONICAL_ATTESTED, "evidence:test-run"));
        StorageDatabaseBinding binding =
                bindingRepository.recordObservation(policy.bind(observed));
        assertFalse(binding.canonical());
        assertEquals(CountStatus.UNKNOWN, binding.observeCounts(10, 2, 1).status());
        assertNotNull(binding.databaseIdentity());
        assertFalse(binding.bindingEvidenceRef().contains("jdbc:"));
    }

    private static int tableCount(String table) {
        return jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema = ? and table_name = ?
                """, Integer.class, SCHEMA, table);
    }

    private static State intentState(String writeIntentId) {
        return State.valueOf(jdbc.queryForObject("""
                select intent_state from storage_write_intent where write_intent_id = ?
                """, String.class, writeIntentId));
    }

    private static void assertSqlState23514(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                assertEquals("23514", sqlException.getSQLState());
                return;
            }
            current = current.getCause();
        }
        throw new AssertionError("expected nested SQLException with SQLSTATE 23514", failure);
    }

    private static IssuanceCommand command(
            StorageOwnershipScope owner, String key, String replicaId, String namespaceTenant) {
        return new IssuanceCommand(owner, new IssuanceIdempotencyKey(key), FINGERPRINT,
                placement(replicaId, namespaceTenant, "correlation-" + replicaId));
    }

    private static BackendPlacementResult placement(
            String replicaId, String namespaceTenant, String correlationId) {
        StorageNamespace namespace = new StorageNamespace(
                namespaceTenant, null, NamespaceClass.DERIVED,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        return new BackendPlacementResult(
                new StorageReplicaId(replicaId),
                new StorageObjectLocation(
                        new StorageProviderId("provider-test"), namespace,
                        "opaque/" + replicaId, "version-1", "region-test"),
                ReplicaState.AVAILABLE,
                ContentDigest.sha256("0".repeat(64)),
                42,
                correlationId);
    }

    private static void insertPlacement(String objectId, String replicaId, String correlationId) {
        jdbc.update("""
                insert into storage_object_placement (
                    replica_id, object_id, provider_id, namespace_tenant_id,
                    namespace_project_id, namespace_class, region_policy,
                    data_classification, opaque_locator, provider_version_token,
                    region, placement_state, committed_digest_algorithm,
                    committed_digest, committed_length, provider_correlation_id, created_at
                ) values (?, ?, 'provider-test', 'namespace-fixture', null, 'DERIVED',
                          'SINGLE_REGION', 'INTERNAL', ?, 'version-1', 'region-test',
                          'AVAILABLE', 'SHA_256', ?, 42, ?, now())
                """, replicaId, objectId, "opaque/" + replicaId,
                "0".repeat(64), correlationId);
    }

    private static void insertAdditionalReceipt(
            String objectId, String replicaId, String receiptId,
            String receiptKey, String correlationId) {
        insertReceipt(objectId, replicaId, receiptId, receiptKey, correlationId,
                "ADDITIONAL_PLACEMENT");
    }

    private static void insertReceipt(
            String objectId, String replicaId, String receiptId,
            String receiptKey, String correlationId, String purpose) {
        jdbc.update("""
                insert into storage_placement_receipt (
                    receipt_id, idempotency_key, semantic_fingerprint, receipt_purpose,
                    object_id, replica_id, provider_id, namespace_tenant_id,
                    namespace_project_id, namespace_class, region_policy,
                    data_classification, opaque_locator, provider_version_token, region,
                    placement_state, committed_digest_algorithm, committed_digest, committed_length,
                    provider_correlation_id, issued_at
                ) values (?, ?, ?, ?, ?, ?, 'provider-test',
                          'namespace-fixture', null, 'DERIVED', 'SINGLE_REGION', 'INTERNAL',
                          ?, 'version-1', 'region-test', 'AVAILABLE', 'SHA_256', ?, 42, ?, now())
                """, receiptId, receiptKey, FINGERPRINT, purpose, objectId, replicaId,
                "opaque/" + replicaId, "0".repeat(64), correlationId);
    }

    private static TrustedDeploymentConfiguration configuration(
            String bindingId,
            DatabaseKind kind,
            DeploymentEnvironment environment,
            Long expectedOid,
            String database,
            String schema,
            TrustLevel trust,
            String evidence) {
        return new TrustedDeploymentConfiguration(
                bindingId, kind, "deployment-test", environment, expectedOid,
                database, schema, "V1", "query-v1", evidence, trust);
    }

    @EnableTransactionManagement
    static class TestTransactionConfiguration {

        @Bean
        DataSource dataSource() {
            return testDataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        CanonicalStorageObjectIdAllocator allocator() {
            return new CanonicalStorageObjectIdAllocator();
        }

        @Bean
        StorageObjectAuthorityRepository objectRepository(JdbcTemplate template) {
            return new JdbcStorageObjectAuthorityRepository(template);
        }

        @Bean
        StorageWriteIntentRepository intentRepository(JdbcTemplate template) {
            return new JdbcStorageWriteIntentRepository(template);
        }

        @Bean
        StorageWriteIntentRecovery recovery(
                CanonicalStorageObjectIdAllocator allocator,
                StorageWriteIntentRepository intents,
                StorageObjectAuthorityRepository objects,
                Clock clock) {
            return new CanonicalStorageWriteIntentRecoveryService(
                    allocator, intents, objects, clock);
        }

        @Bean
        StorageObjectIssuance issuance(StorageWriteIntentRecovery recovery) {
            return new CanonicalStorageObjectIssuanceService(recovery);
        }

        @Bean
        StorageDatabaseBindingObserver bindingObserver(JdbcTemplate template, Clock clock) {
            return new JdbcStorageDatabaseBindingObserver(template, clock);
        }

        @Bean
        StorageDatabaseBindingRepository bindingRepository(JdbcTemplate template) {
            return new JdbcStorageDatabaseBindingRepository(template);
        }
    }
}
