package com.example.platform.artifact.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.time.Instant;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactRepositoryTenantDestructiveMutationTest extends PostgresTestContainerSupport {

    private static final String OWNER = "tenant-owner";
    private static final String OTHER = "tenant-other";
    private static DataSource dataSource;
    private static DSLContext dsl;

    private ArtifactRepository repository;

    @BeforeAll
    static void createDataSourceFixture() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES,
                new Settings().withRenderNameCase(RenderNameCase.LOWER));
        com.example.platform.artifact.testutil.ArtifactSchemaFixture.createCanonicalTables(
                new org.springframework.jdbc.core.JdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE artifact_pin CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_replica CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");
        repository = new ArtifactRepository(dsl);
    }

    @Test
    void crossTenantUpdateStateAndMarkPurgedAreRejectedWithoutMutation() {
        ArtifactId updateId = insertArtifact("art-update", ArtifactState.AVAILABLE);
        ArtifactId purgeId = insertArtifact("art-purge", ArtifactState.DELETING);

        assertFalse(repository.updateState(
                OTHER, updateId.value(), ArtifactState.DELETING, LocalDateTime.now()));
        assertFalse(repository.markPurged(OTHER, purgeId.value()));

        assertEquals(ArtifactState.AVAILABLE,
                repository.findById(OWNER, updateId).orElseThrow().state());
        assertEquals(ArtifactState.DELETING,
                repository.findById(OWNER, purgeId).orElseThrow().state());
    }

    @Test
    void crossTenantDeleteReplicaCannotDeleteOwnersReplica() {
        ArtifactId artifactId = insertArtifact("art-replica", ArtifactState.AVAILABLE);
        insertReplica(artifactId, "rep-1");

        assertFalse(repository.deleteReplica(OTHER, artifactId.value(), "rep-1"));
        assertEquals(1L, repository.countReplicas(OWNER, artifactId.value()));
    }

    @Test
    void sameTenantLifecycleMutationsSucceed() {
        ArtifactId updateId = insertArtifact("art-update", ArtifactState.AVAILABLE);
        ArtifactId purgeId = insertArtifact("art-purge", ArtifactState.DELETING);
        ArtifactId replicaId = insertArtifact("art-replica", ArtifactState.AVAILABLE);
        insertReplica(replicaId, "rep-1");

        assertTrue(repository.updateState(
                OWNER, updateId.value(), ArtifactState.DELETING, LocalDateTime.now()));
        assertTrue(repository.markPurged(OWNER, purgeId.value()));
        assertTrue(repository.deleteReplica(OWNER, replicaId.value(), "rep-1"));

        assertEquals(ArtifactState.DELETING,
                repository.findById(OWNER, updateId).orElseThrow().state());
        assertEquals(ArtifactState.DELETED,
                repository.findById(OWNER, purgeId).orElseThrow().state());
        assertEquals(0L, repository.countReplicas(OWNER, replicaId.value()));
    }

    @Test
    void destructiveOperationsRejectNullBlankAndWildcardTenant() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findTombstonedBefore(null, Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.updateState(" ", "art-1", ArtifactState.DELETING, LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.markPurged("*", "art-1"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.deleteReplica(null, "art-1", "rep-1"));
    }

    private ArtifactId insertArtifact(String value, ArtifactState state) {
        ArtifactId artifactId = new ArtifactId(value);
        repository.insertRaw(artifactId, OWNER, ContentDigest.sha256("a".repeat(64)), 10L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, state,
                state == ArtifactState.DELETING ? Instant.now().minusSeconds(864_000) : null);
        return artifactId;
    }

    private void insertReplica(ArtifactId artifactId, String replicaId) {
        repository.insertReplica(new ArtifactReplicaBinding(
                artifactId.value() + ":" + replicaId,
                artifactId,
                new StorageObjectId("opaque-" + artifactId.value()),
                new StorageReplicaId(replicaId),
                new StorageProviderId("provider-a"),
                ReplicaRole.PRIMARY,
                "default",
                Instant.now()));
    }
}
