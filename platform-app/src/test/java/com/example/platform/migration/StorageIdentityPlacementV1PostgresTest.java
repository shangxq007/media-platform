package com.example.platform.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceCommand;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.app.identity.CanonicalStorageObjectIssuanceService;
import com.example.platform.storage.app.identity.StorageIssuanceConflictException;
import com.example.platform.storage.app.identity.StorageObjectAuthorityRepository;
import com.example.platform.storage.app.migration.StorageDatabaseBindingExpectation;
import com.example.platform.storage.app.migration.StorageDatabaseBindingMismatchException;
import com.example.platform.storage.app.migration.StorageDatabaseBindingObserver;
import com.example.platform.storage.app.migration.StorageIdentityObservationService;
import com.example.platform.storage.app.migration.StorageMigrationObservationRepository;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.replica.ReplicaState;
import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationEvidence;
import com.example.platform.storage.domain.migration.PersistedStorageIdentityClassifier.ClassificationInput;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.CountStatus;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import com.example.platform.storage.infrastructure.identity.JdbcStorageObjectAuthorityRepository;
import com.example.platform.storage.infrastructure.migration.JdbcStorageDatabaseBindingObserver;
import com.example.platform.storage.infrastructure.migration.JdbcStorageMigrationObservationRepository;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Real-PostgreSQL proof for the additive M0/M1 canonical V1 schema and transaction boundary. */
class StorageIdentityPlacementV1PostgresTest extends PostgresTestContainerSupport {

    private static final String SCHEMA = isolatedSchemaName();
    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);
    private static DataSource adminDataSource;
    private static DataSource testDataSource;
    private static JdbcTemplate jdbc;
    private static StorageObjectIssuance issuance;
    private static StorageDatabaseBindingObserver bindingObserver;
    private static StorageIdentityObservationService observationService;
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void migrateCanonicalV1AndCreateTransactionalBoundary() throws Exception {
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
        bindingObserver = context.getBean(StorageDatabaseBindingObserver.class);
        observationService = context.getBean(StorageIdentityObservationService.class);
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
    void normalizedTablesConstraintsAndNoGenericJsonArePresent() {
        for (String table : new String[] {
                "storage_database_binding",
                "storage_logical_object",
                "storage_object_placement",
                "storage_placement_receipt",
                "storage_identity_classification",
                "storage_identity_classification_evidence",
                "storage_identity_migration_journal"
        }) {
            assertEquals(1, jdbc.queryForObject("""
                    select count(*) from information_schema.tables
                     where table_schema = ? and table_name = ?
                    """, Integer.class, SCHEMA, table));
        }
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.columns
                 where table_schema = ?
                   and table_name in (
                       'storage_logical_object', 'storage_object_placement',
                       'storage_placement_receipt', 'storage_identity_classification',
                       'storage_identity_migration_journal'
                   )
                   and data_type in ('json', 'jsonb')
                """, Integer.class, SCHEMA));
        assertEquals(0, jdbc.queryForObject("""
                select count(*)
                  from information_schema.table_constraints
                 where table_schema = ? and table_name = 'artifact_replica'
                   and constraint_type = 'FOREIGN KEY'
                   and constraint_name like '%storage_logical%'
                """, Integer.class, SCHEMA),
                "M5 Artifact FK transition must remain absent");
    }

    @Test
    void objectSupportsMultiplePlacementsAndPlacementFkChecksFailClosed() {
        IssuanceResult issued = issuance.issue(command("multi-placement", "replica-multi-1"));
        insertPlacement(issued.objectId().value(), "replica-multi-2", "correlation-multi-2");
        assertEquals(2, jdbc.queryForObject(
                "select count(*) from storage_object_placement where object_id = ?",
                Integer.class, issued.objectId().value()));

        assertThrows(DataIntegrityViolationException.class,
                () -> insertPlacement("unknown-object", "replica-orphan", "correlation-orphan"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        update storage_object_placement set placement_state = 'NOT_A_STATE'
                         where replica_id = ?
                        """, "replica-multi-2"));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertPlacement(issued.objectId().value(),
                        "replica-duplicate-correlation", "correlation-multi-2"));
    }

    @Test
    void issuanceReceiptIsIdempotentImmutableAndConflictSafe() {
        IssuanceCommand command = command("receipt-idempotency", "replica-receipt");
        IssuanceResult first = issuance.issue(command);
        IssuanceResult replay = issuance.issue(command);
        assertEquals(first, replay);
        assertNotNull(first.receipt().receiptId());

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update storage_placement_receipt set committed_length = 99 "
                        + "where receipt_id = ?", first.receipt().receiptId()));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from storage_placement_receipt where receipt_id = ?",
                        first.receipt().receiptId()));

        assertThrows(StorageIssuanceConflictException.class,
                () -> issuance.issue(new IssuanceCommand(
                        command.idempotencyKey(), "b".repeat(64), command.backendPlacement())));
    }

    @Test
    void transactionalFailureLeavesNoPartialObjectPlacementOrReceipt() {
        issuance.issue(command("rollback-primer", "replica-rollback-collision"));
        IssuanceCommand failing = command("rollback-target", "replica-rollback-collision");

        assertThrows(DataIntegrityViolationException.class, () -> issuance.issue(failing));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from storage_logical_object where issuance_idempotency_key = ?",
                Integer.class, failing.idempotencyKey()));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from storage_placement_receipt where idempotency_key = ?",
                Integer.class, failing.idempotencyKey()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from storage_object_placement where replica_id = ?",
                Integer.class, "replica-rollback-collision"));
    }

    @Test
    void mismatchedDatabaseOrSchemaCannotMasqueradeAsDeclaredBinding() {
        String actualDatabase = jdbc.queryForObject("select current_database()", String.class);

        assertThrows(StorageDatabaseBindingMismatchException.class,
                () -> observationService.observe(
                        bindingExpectation(
                                "binding-wrong-database", "unrelated_" + actualDatabase, SCHEMA),
                        classificationInput("binding-wrong-database")));
        assertThrows(StorageDatabaseBindingMismatchException.class,
                () -> observationService.observe(
                        bindingExpectation(
                                "binding-wrong-schema", actualDatabase, "unrelated_schema"),
                        classificationInput("binding-wrong-schema")));

        assertEquals(0, jdbc.queryForObject("""
                select count(*) from storage_database_binding
                 where binding_id in ('binding-wrong-database', 'binding-wrong-schema')
                """, Integer.class));
    }

    @Test
    void testcontainersObservationIsPersistedNoncanonicalEvenWhenCanonicalRequested() {
        String actualDatabase = jdbc.queryForObject("select current_database()", String.class);
        StorageDatabaseBindingExpectation expectation = bindingExpectation(
                "binding-testcontainers-observed", actualDatabase, SCHEMA);

        StorageDatabaseBinding first = bindingObserver.observe(expectation);
        StorageDatabaseBinding second = bindingObserver.observe(expectation);
        assertEquals(first.databaseIdentity(), second.databaseIdentity());
        assertTrue(first.databaseIdentity().matches("postgresql:sha256:[0-9a-f]{64}"));
        assertFalse(first.canonical());
        assertEquals(CountStatus.UNKNOWN, first.observeCounts(10, 2, 1).status());

        observationService.observe(
                expectation, classificationInput("binding-testcontainers-observed"));

        assertFalse(jdbc.queryForObject("""
                select is_canonical from storage_database_binding where binding_id = ?
                """, Boolean.class, "binding-testcontainers-observed"));
        assertEquals(first.databaseIdentity(), jdbc.queryForObject("""
                select database_identity from storage_database_binding where binding_id = ?
                """, String.class, "binding-testcontainers-observed"));
        String evidenceRef = jdbc.queryForObject("""
                select binding_evidence_ref from storage_database_binding where binding_id = ?
                """, String.class, "binding-testcontainers-observed");
        assertEquals("evidence:testcontainers-observation", evidenceRef);
        assertFalse(evidenceRef.contains("jdbc:"));
        assertFalse(evidenceRef.contains("password"));
    }

    private static IssuanceCommand command(String key, String replicaId) {
        StorageNamespace namespace = new StorageNamespace(
                "tenant-test", "project-test", NamespaceClass.DERIVED,
                RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        BackendPlacementResult placement = new BackendPlacementResult(
                new StorageReplicaId(replicaId),
                new StorageObjectLocation(
                        new StorageProviderId("provider-test"), namespace,
                        "opaque/" + key, "version-1", "region-test"),
                ReplicaState.AVAILABLE,
                ContentDigest.sha256("0".repeat(64)),
                42,
                "correlation-" + key);
        return new IssuanceCommand(key, FINGERPRINT, placement);
    }

    private static void insertPlacement(String objectId, String replicaId, String correlationId) {
        jdbc.update("""
                insert into storage_object_placement (
                    replica_id, object_id, provider_id, namespace_tenant_id,
                    namespace_project_id, namespace_class, region_policy,
                    data_classification, opaque_locator, provider_version_token,
                    region, placement_state, committed_digest_algorithm,
                    committed_digest, committed_length, provider_correlation_id, created_at
                ) values (?, ?, 'provider-test', 'tenant-test', 'project-test', 'DERIVED',
                          'SINGLE_REGION', 'INTERNAL', ?, 'version-1', 'region-test',
                          'AVAILABLE', 'SHA_256', ?, 42, ?, now())
                """, replicaId, objectId, "opaque/" + replicaId, "0".repeat(64), correlationId);
    }

    private static StorageDatabaseBindingExpectation bindingExpectation(
            String bindingId, String expectedDatabase, String expectedSchema) {
        return new StorageDatabaseBindingExpectation(
                bindingId,
                DatabaseKind.TESTCONTAINERS,
                "local-test-run",
                DeploymentEnvironment.TESTCONTAINERS,
                expectedDatabase,
                expectedSchema,
                "V1",
                "query-v1",
                "evidence:testcontainers-observation",
                true);
    }

    private static ClassificationInput classificationInput(String bindingId) {
        return new ClassificationInput(
                bindingId,
                "storage_object",
                "source-" + bindingId,
                "persisted-value",
                "classifier-v1",
                "evidence-v1",
                new ClassificationEvidence(
                        false, false, false, false, false, false, List.of()));
    }

    @Configuration
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
        StorageObjectAuthorityRepository repository(JdbcTemplate jdbcTemplate) {
            return new JdbcStorageObjectAuthorityRepository(jdbcTemplate);
        }

        @Bean
        StorageObjectIssuance issuance(
                CanonicalStorageObjectIdAllocator allocator,
                StorageObjectAuthorityRepository repository,
                Clock clock) {
            return new CanonicalStorageObjectIssuanceService(allocator, repository, clock);
        }

        @Bean
        PersistedStorageIdentityClassifier classifier() {
            return new PersistedStorageIdentityClassifier();
        }

        @Bean
        StorageDatabaseBindingObserver bindingObserver(JdbcTemplate jdbcTemplate, Clock clock) {
            return new JdbcStorageDatabaseBindingObserver(jdbcTemplate, clock);
        }

        @Bean
        StorageMigrationObservationRepository observationRepository(JdbcTemplate jdbcTemplate) {
            return new JdbcStorageMigrationObservationRepository(jdbcTemplate);
        }

        @Bean
        StorageIdentityObservationService observationService(
                PersistedStorageIdentityClassifier classifier,
                StorageDatabaseBindingObserver bindingObserver,
                StorageMigrationObservationRepository observationRepository,
                Clock clock) {
            return new StorageIdentityObservationService(
                    classifier, bindingObserver, observationRepository, clock);
        }
    }
}
