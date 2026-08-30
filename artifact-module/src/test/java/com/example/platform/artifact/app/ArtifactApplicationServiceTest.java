package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.app.ArtifactApplicationService.ArtifactAccessException;
import com.example.platform.artifact.app.ArtifactApplicationService.ArtifactAccessFailure;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ProvenanceEdge;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactApplicationServiceTest {

    private static final ArtifactScope SCOPE = new ArtifactScope("tenant-a", "project-a", "job-a");
    private static final ArtifactId ARTIFACT_ID = new ArtifactId("artifact-a");
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));
    private static final Artifact ARTIFACT = new Artifact(
            ARTIFACT_ID, "tenant-a", DIGEST, 42L, ArtifactMediaType.VIDEO,
            ArtifactKind.RENDER_MASTER, ArtifactState.AVAILABLE, 1, Instant.parse("2026-08-29T00:00:00Z"));
    private static final ArtifactReplicaBinding REPLICA = new ArtifactReplicaBinding(
            "binding-a", ARTIFACT_ID, new StorageObjectId("bucket/key"),
            new StorageReplicaId("replica-a"), new StorageProviderId("s3-primary"),
            ReplicaRole.PRIMARY, "default", Instant.parse("2026-08-29T00:00:00Z"));

    private FakeApplicationQuery query;
    private FakeArtifactQuery artifactQuery;
    private RecordingGrantProvider grantProvider;
    private ArtifactApplicationService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tenant-a");
        query = new FakeApplicationQuery();
        artifactQuery = new FakeArtifactQuery();
        grantProvider = new RecordingGrantProvider();
        service = new ArtifactApplicationService(query, artifactQuery, List.of(grantProvider));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listReturnsRedactedCanonicalSummaryAndBoundsLimit() {
        query.artifacts = List.of(ARTIFACT);

        List<ArtifactSummary> result = service.listArtifacts(SCOPE, 10_000);

        assertEquals(1, result.size());
        assertEquals(ARTIFACT_ID, result.getFirst().artifactId());
        assertEquals(DIGEST, result.getFirst().contentDigest());
        assertEquals(ArtifactIntegrityState.DIGEST_RECORDED, result.getFirst().integrityState());
        assertEquals(200, query.lastLimit);
        assertEquals(1L, service.countArtifacts(SCOPE));
        assertFalse(result.getFirst().toString().contains("bucket/key"));
    }

    @Test
    void crossTenantRequestFailsBeforeQuery() {
        TenantContext.set("tenant-b");

        assertThrows(RuntimeException.class, () -> service.listArtifacts(SCOPE, 10));

        assertEquals(0, query.calls);
    }

    @Test
    void explicitAccessChecksScopeAndArtifactStateBeforeStorageGrant() {
        query.artifact = Optional.of(ARTIFACT.withState(ArtifactState.QUARANTINED));

        ArtifactAccessException error = assertThrows(
                ArtifactAccessException.class, () -> service.requestAccess(SCOPE, ARTIFACT_ID));

        assertEquals(ArtifactAccessFailure.NOT_AVAILABLE, error.failure());
        assertEquals(0, grantProvider.calls);
    }

    @Test
    void explicitAccessReturnsEphemeralGrantWithoutCoordinates() {
        query.artifact = Optional.of(ARTIFACT);
        artifactQuery.replicas = List.of(REPLICA);

        ArtifactAccess result = service.requestAccess(SCOPE, ARTIFACT_ID);

        assertEquals(ARTIFACT_ID, result.artifactId());
        assertEquals(URI.create("https://access.example/artifact-a"), result.accessUrl());
        assertEquals(1, grantProvider.calls);
        assertFalse(result.toString().contains("bucket/key"));
    }

    @Test
    void missingScopedArtifactNeverEnumeratesReplicasOrCallsGrantProvider() {
        query.artifact = Optional.empty();

        ArtifactAccessException error = assertThrows(
                ArtifactAccessException.class, () -> service.requestAccess(SCOPE, ARTIFACT_ID));

        assertEquals(ArtifactAccessFailure.NOT_FOUND, error.failure());
        assertEquals(0, artifactQuery.replicaCalls);
        assertEquals(0, grantProvider.calls);
    }

    @Test
    void storageGrantFailureIsRedactedAndFailsClosed() {
        query.artifact = Optional.of(ARTIFACT);
        artifactQuery.replicas = List.of(REPLICA);
        grantProvider.failure = new IllegalStateException("bucket secret must not escape");

        ArtifactAccessException error = assertThrows(
                ArtifactAccessException.class, () -> service.requestAccess(SCOPE, ARTIFACT_ID));

        assertEquals(ArtifactAccessFailure.ACCESS_FAILED, error.failure());
        assertFalse(error.getMessage().contains("bucket secret"));
    }

    private static final class FakeApplicationQuery implements ArtifactApplicationQuery {
        private List<Artifact> artifacts = List.of();
        private Optional<Artifact> artifact = Optional.empty();
        private int lastLimit;
        private int calls;

        @Override
        public List<Artifact> findArtifacts(ArtifactScope scope, int limit) {
            calls++;
            lastLimit = limit;
            return artifacts;
        }

        @Override
        public long countArtifacts(ArtifactScope scope) {
            calls++;
            return artifacts.size();
        }

        @Override
        public Optional<Artifact> findArtifact(ArtifactScope scope, ArtifactId artifactId) {
            calls++;
            return artifact;
        }
    }

    private static final class RecordingGrantProvider implements ArtifactAccessGrantProvider {
        private int calls;
        private RuntimeException failure;

        @Override
        public Optional<Grant> grant(Artifact artifact, ArtifactReplicaBinding replica) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return Optional.of(new Grant(
                    URI.create("https://access.example/artifact-a"),
                    Instant.parse("2026-08-29T01:00:00Z")));
        }
    }

    private static final class FakeArtifactQuery implements ArtifactQueryService {
        private List<ArtifactReplicaBinding> replicas = List.of();
        private int replicaCalls;

        @Override
        public Optional<Artifact> getArtifact(String tenantId, ArtifactId artifactId) { return Optional.empty(); }

        @Override
        public List<ArtifactReplicaBinding> listReplicas(String tenantId, ArtifactId artifactId) {
            replicaCalls++;
            return replicas;
        }

        @Override
        public Optional<ArtifactReplicaBinding> findReplica(
                String tenantId, ArtifactId artifactId, StorageReplicaId replicaId) { return Optional.empty(); }

        @Override
        public List<ArtifactId> listParents(String tenantId, ArtifactId artifactId) { return List.of(); }

        @Override
        public List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId) { return List.of(); }

        @Override
        public List<ProvenanceEdge> getDirectProvenance(String tenantId, ArtifactId artifactId) { return List.of(); }

        @Override
        public List<ArtifactId> boundedAncestorTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
            return List.of();
        }

        @Override
        public List<ArtifactId> boundedDescendantTraversal(String tenantId, ArtifactId artifactId, int maxDepth) {
            return List.of();
        }

        @Override
        public List<Artifact> findByContentDigest(String tenantId, ContentDigest contentDigest, int limit) {
            return List.of();
        }
    }
}
