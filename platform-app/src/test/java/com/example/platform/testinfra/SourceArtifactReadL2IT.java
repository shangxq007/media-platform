package com.example.platform.testinfra;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.artifact.app.ArtifactApplicationQuery;
import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.app.ArtifactApplicationService;
import com.example.platform.artifact.app.ArtifactContentRead;
import com.example.platform.artifact.app.ArtifactProjectScope;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactOutputFormat;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.artifact.infrastructure.ArtifactContentReadService;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.artifact.infrastructure.JooqArtifactApplicationQuery;
import com.example.platform.artifact.infrastructure.JooqArtifactCommitService;
import com.example.platform.artifact.infrastructure.JooqArtifactQueryService;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageObjectIssuance;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.api.StoragePlacementQuery;
import com.example.platform.storage.api.StorageReferenceStore;
import com.example.platform.storage.api.StorageSourceUploadPort;
import com.example.platform.storage.api.StorageWriteIntentRecovery;
import com.example.platform.storage.api.VerifiedStorageRead;
import com.example.platform.storage.app.OutputStorageProperties;
import com.example.platform.storage.app.StorageOutputService;
import com.example.platform.storage.app.VerifiedStorageReadService;
import com.example.platform.storage.app.identity.CanonicalStorageWriteIntentRecoveryService;
import com.example.platform.storage.app.identity.StorageObjectAuthorityRepository;
import com.example.platform.storage.app.identity.StorageWriteIntentRepository;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.domain.BlobStorage;
import com.example.platform.storage.domain.identity.CanonicalStorageObjectIdAllocator;
import com.example.platform.storage.infrastructure.LocalFsStorageProvider;
import com.example.platform.storage.infrastructure.StorageReferenceRepository;
import com.example.platform.storage.infrastructure.StorageS3Properties;
import com.example.platform.storage.infrastructure.identity.JdbcStorageObjectAuthorityRepository;
import com.example.platform.storage.infrastructure.identity.JdbcStorageWriteIntentRepository;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Real L2 proof for SOURCE_MEDIA content reads: real PostgreSQL persistence, real
 * StorageOutputService/LocalFsStorageProvider write path, real canonical Storage receipt,
 * real Artifact + replica commit, and a VerifiedStorageRead registry with no provider for
 * the recorded BlobStorage provider id. The API content service must use the committed
 * receipt fallback and return the exact uploaded bytes.
 */
class SourceArtifactReadL2IT extends PostgresTestContainerSupport {
    private static final String TENANT = "tenant-l2-source";
    private static final String PROJECT = "project-l2-source";
    private static final String WORKSPACE = "workspace-l2-source";
    private static final String SCHEMA = isolatedSchemaName();

    private static Path storageRoot;
    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static StorageSourceUploadPort storage;
    private static ArtifactCommitService commits;
    private static ArtifactContentRead contents;

    @BeforeAll
    static void migrateAndWireRealComponents() throws Exception {
        TenantContext.set(TENANT);
        storageRoot = Files.createTempDirectory("l2-source-storage-");
        dataSource = scopedDataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("public")
                .defaultSchema("public")
                .createSchemas(true)
                .load()
                .migrate();
        context = new AnnotationConfigApplicationContext();
        context.register(RealComponents.class);
        context.refresh();
        storage = context.getBean(StorageSourceUploadPort.class);
        commits = context.getBean(ArtifactCommitService.class);
        contents = context.getBean(ArtifactContentRead.class);
    }

    @AfterAll
    static void closeRealComponents() throws Exception {
        TenantContext.clear();
        if (context != null) context.close();
        if (dataSource != null) {
            try (Connection admin = DriverManager.getConnection(jdbcUrl(), username(), password());
                 Statement statement = admin.createStatement()) {
                statement.execute("drop schema if exists " + SCHEMA + " cascade");
            }
            closeDataSource(dataSource);
        }
        if (storageRoot != null) deleteRecursively(storageRoot);
    }

    @BeforeEach
    void tenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void receiptFallbackServesExactUploadedMp4WithoutProviderRegistration() {
        byte[] mp4 = mp4();
        ArtifactId id = commitSource(mp4, "video/mp4", ArtifactMediaType.VIDEO);
        var content = contents.readProjectSourceArtifact(new ArtifactProjectScope(TENANT, PROJECT), id);
        assertArrayEquals(mp4, content.bytes());
        assertEquals("video/mp4", content.contentType());
        assertEquals("output.mp4", content.fileName());
        assertFalse(content.toString().contains("so-"));
        assertFalse(content.toString().contains("local-storage"));
        assertFalse(content.toString().contains("artifacts/sources"));
    }

    @Test
    void legacyProjectRouteSharesTheSameReceiptFallback() {
        byte[] mp4 = mp4();
        ArtifactId id = commitSource(mp4, "video/mp4", ArtifactMediaType.VIDEO);
        var content = contents.readProjectArtifact(new ArtifactProjectScope(TENANT, PROJECT), id);
        assertArrayEquals(mp4, content.bytes());
        assertEquals("video/mp4", content.contentType());
    }

    @Test
    void nonMp4AcceptedFormatUsesTheContainerAuthority() {
        byte[] wav = wav();
        ArtifactId id = commitSource(wav, "audio/wav", ArtifactMediaType.AUDIO);
        var content = contents.readProjectSourceArtifact(new ArtifactProjectScope(TENANT, PROJECT), id);
        assertArrayEquals(wav, content.bytes());
        assertEquals("audio/wav", content.contentType());
        assertEquals("output.wav", content.fileName());
    }

    @Test
    void crossProjectSourceIsNotFound() {
        ArtifactId id = commitSource(mp4(), "video/mp4", ArtifactMediaType.VIDEO);
        var error = assertThrows(ArtifactApplicationService.ArtifactAccessException.class,
                () -> contents.readProjectSourceArtifact(
                        new ArtifactProjectScope(TENANT, "other-project"), id));
        assertEquals(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND, error.failure());
    }

    @Test
    void missingArtifactIsNotFound() {
        var error = assertThrows(ArtifactApplicationService.ArtifactAccessException.class,
                () -> contents.readProjectSourceArtifact(
                        new ArtifactProjectScope(TENANT, PROJECT), new ArtifactId("art-l2-missing")));
        assertEquals(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND, error.failure());
    }

    @Test
    void crossTenantIsRejectedBeforeProjectLookup() {
        TenantContext.set("other-tenant");
        assertThrows(RuntimeException.class,
                () -> contents.readProjectSourceArtifact(
                        new ArtifactProjectScope(TENANT, PROJECT), new ArtifactId("art-l2-any")));
    }

    @Test
    void sourceRouteRejectsDerivedArtifact() {
        byte[] image = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        ArtifactId id = new ArtifactId("art-l2-derived-" + UUID.randomUUID());
        Instant now = Instant.now();
        commits.commit(new ArtifactCommitRequest(
                id, TENANT, digest(image), image.length, ArtifactMediaType.IMAGE,
                ArtifactKind.DERIVED_MEDIA, 1, new StorageObjectId("obj-derived"),
                new StorageReplicaId("rep-derived"), new StorageProviderId("local-object-store"),
                ReplicaRole.PRIMARY, "local", "l2-derived-" + id.value(), List.of(),
                now, now, null, PROJECT, WORKSPACE));
        var error = assertThrows(ArtifactApplicationService.ArtifactAccessException.class,
                () -> contents.readProjectSourceArtifact(new ArtifactProjectScope(TENANT, PROJECT), id));
        assertEquals(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND, error.failure());
    }

    private static ArtifactId commitSource(byte[] bytes, String mime, ArtifactMediaType mediaType) {
        var owner = new StorageOwnershipScope(TENANT, PROJECT);
        var key = new IssuanceIdempotencyKey("l2-source-" + UUID.randomUUID());
        var written = storage.writeSource(owner, key, new ByteArrayInputStream(bytes), bytes.length, mime);
        var placement = written.issuance().placement();
        ArtifactId id = new ArtifactId("art-l2-source-" + UUID.randomUUID());
        Instant now = Instant.now();
        commits.commit(new ArtifactCommitRequest(
                id, TENANT, digest(bytes), bytes.length, mediaType, ArtifactKind.SOURCE_MEDIA, 1,
                written.issuance().objectId(), placement.replicaId(),
                placement.location().providerId(), ReplicaRole.PRIMARY,
                placement.location().region(), "l2-commit-" + id.value(), List.of(),
                now, now, null, PROJECT, WORKSPACE));
        return id;
    }

    private static DataSource scopedDataSource() {
        DriverManagerDataSource scoped = new DriverManagerDataSource();
        String separator = jdbcUrl().contains("?") ? "&" : "?";
        scoped.setUrl(jdbcUrl() + separator + "currentSchema=" + SCHEMA);
        scoped.setUsername(username());
        scoped.setPassword(password());
        scoped.setDriverClassName(driverClassName());
        return scoped;
    }

    private static ContentDigest digest(byte[] bytes) {
        try {
            return ContentDigest.sha256(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] mp4() {
        return java.util.HexFormat.of().parseHex(
                "000000186674797069736f6d0000020069736f6d6d703432");
    }

    private static byte[] wav() {
        return new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
    }

    private static void deleteRecursively(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
            });
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class RealComponents {
        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean DSLContext dsl(DataSource source) {
            return DSL.using(source, SQLDialect.POSTGRES, new Settings()
                    .withRenderNameCase(RenderNameCase.LOWER)
                    .withRenderMapping(new RenderMapping().withSchemata(
                            new MappedSchema().withInput("public").withOutput(SCHEMA))));
        }
        @Bean CanonicalStorageObjectIdAllocator allocator() {
            return new CanonicalStorageObjectIdAllocator();
        }
        @Bean StorageObjectAuthorityRepository objectRepository(JdbcTemplate template) {
            return new JdbcStorageObjectAuthorityRepository(template);
        }
        @Bean StorageWriteIntentRepository intentRepository(JdbcTemplate template) {
            return new JdbcStorageWriteIntentRepository(template);
        }
        @Bean StorageWriteIntentRecovery recovery(CanonicalStorageObjectIdAllocator allocator,
                StorageWriteIntentRepository intents, StorageObjectAuthorityRepository objects, Clock clock) {
            return new CanonicalStorageWriteIntentRecoveryService(allocator, intents, objects, clock);
        }
        @Bean StorageReferenceStore references(DSLContext dsl) {
            return new StorageReferenceRepository(dsl);
        }
        @Bean BlobStorage blobStorage() {
            return new LocalFsStorageProvider(storageRoot.toString());
        }
        @Bean OutputStorageProperties outputStorageProperties() { return new OutputStorageProperties(); }
        @Bean StorageS3Properties storageS3Properties() { return new StorageS3Properties(); }
        @Bean StorageOutputService storageOutputService(BlobStorage blobs, StorageWriteIntentRecovery recovery,
                StorageObjectAuthorityRepository objects, StorageReferenceStore references,
                OutputStorageProperties properties, StorageS3Properties s3) {
            return new StorageOutputService(blobs, recovery, objects, references,
                    storageRoot.toString(), properties, s3);
        }
        @Bean ArtifactRepository artifactRepository(DSLContext dsl) { return new ArtifactRepository(dsl); }
        @Bean ArtifactRelationRepository relationRepository(DSLContext dsl) {
            return new ArtifactRelationRepository(dsl);
        }
        @Bean ArtifactCommitService commits(ArtifactRepository artifacts,
                ArtifactRelationRepository relations, DSLContext dsl) {
            return new JooqArtifactCommitService(artifacts, relations, dsl);
        }
        @Bean ArtifactApplicationQuery scopedArtifacts(DSLContext dsl) {
            return new JooqArtifactApplicationQuery(dsl);
        }
        @Bean com.example.platform.artifact.domain.ArtifactQueryService artifacts(
                ArtifactRepository repository, ArtifactRelationRepository relations) {
            return new JooqArtifactQueryService(repository, relations);
        }
        @Bean VerifiedStorageRead verifiedStorageRead() {
            return new VerifiedStorageReadService(List.of());
        }
        @Bean ArtifactContentRead artifactContentRead(ArtifactApplicationQuery scopedArtifacts,
                com.example.platform.artifact.domain.ArtifactQueryService artifacts,
                VerifiedStorageRead verifiedStorageRead, StoragePlacementQuery placements) {
            return new ArtifactContentReadService(scopedArtifacts, artifacts, verifiedStorageRead,
                    placements);
        }
    }
}
