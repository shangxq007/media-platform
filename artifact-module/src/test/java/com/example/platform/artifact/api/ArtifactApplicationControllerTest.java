package com.example.platform.artifact.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.example.platform.artifact.app.ArtifactAccess;
import com.example.platform.artifact.app.ArtifactApplicationService;
import com.example.platform.artifact.app.ArtifactIntegrityState;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.artifact.app.ArtifactScope;
import com.example.platform.artifact.app.ArtifactSummary;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

class ArtifactApplicationControllerTest {

    private FakeArtifactApplicationService service;
    private ArtifactProjectAuthorizationPort projectAuthorization;
    private ArtifactApplicationController controller;
    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new FakeArtifactApplicationService();
        projectAuthorization = mock(ArtifactProjectAuthorizationPort.class);
        doAnswer(invocation -> {
            events.add("authorize(" + invocation.getArgument(0) + "," + invocation.getArgument(1) + ")");
            return null;
        }).when(projectAuthorization).requireRead(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        controller = new ArtifactApplicationController(service, projectAuthorization);
    }

    @Test
    void listFlattensTypedIdentityAndDigestWithoutStorageCoordinates() {
        ArtifactScope scope = new ArtifactScope("tenant-a", "project-a", "job-a");
        service.summaries = List.of(new ArtifactSummary(
                new ArtifactId("artifact-a"), ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER,
                ContentDigest.sha256("a".repeat(64)), 42L, ArtifactState.AVAILABLE,
                ArtifactIntegrityState.DIGEST_RECORDED, Instant.parse("2026-08-29T00:00:00Z")));

        var result = controller.list("tenant-a", "project-a", "job-a", 25);

        assertEquals(1, result.total());
        assertEquals("artifact-a", result.items().getFirst().id());
        assertEquals("SHA_256:" + "a".repeat(64), result.items().getFirst().contentDigest());
        assertFalse(result.toString().contains("storage"));
    }

    @Test
    void accessResponseContainsOnlyEphemeralApplicationGrant() {
        ArtifactScope scope = new ArtifactScope("tenant-a", "project-a", "job-a");
        ArtifactId artifactId = new ArtifactId("artifact-a");
        service.access = new ArtifactAccess(
                artifactId, URI.create("https://access.example/artifact-a"),
                Instant.parse("2026-08-29T01:00:00Z"));

        ResponseEntity<?> result = controller.access(
                "tenant-a", "project-a", "job-a", "artifact-a");

        assertEquals(200, result.getStatusCode().value());
        var body = (ArtifactApplicationController.ArtifactAccessResponse) result.getBody();
        assertEquals("artifact-a", body.access().artifactId());
        assertEquals("https://access.example/artifact-a", body.access().accessUrl());
        assertFalse(body.toString().contains("bucket"));
    }

    @Test
    void listAndAccessAuthorizeProjectReadBeforeServiceAccess() {
        service.access = new ArtifactAccess(
                new ArtifactId("artifact-a"), URI.create("https://access.example/artifact-a"),
                Instant.parse("2026-08-29T01:00:00Z"));

        controller.list("tenant-a", "project-a", "job-a", 25);
        controller.access("tenant-a", "project-a", "job-a", "artifact-a");

        assertEquals(List.of(
                "authorize(tenant-a,project-a)", "service.list",
                "authorize(tenant-a,project-a)", "service.access"), events);
    }

    @Test
    void authorizationDenialPreventsServiceInteraction() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(projectAuthorization).requireRead("tenant-a", "project-a");

        assertThrows(ResponseStatusException.class,
                () -> controller.list("tenant-a", "project-a", "job-a", 25));
    }

    private final class FakeArtifactApplicationService extends ArtifactApplicationService {
        private List<ArtifactSummary> summaries = List.of();
        private ArtifactAccess access;

        private FakeArtifactApplicationService() {
            super(new ArtifactApplicationQueryStub(), new ArtifactQueryServiceStub(), List.of());
        }

        @Override
        public List<ArtifactSummary> listArtifacts(ArtifactScope scope, int requestedLimit) {
            events.add("service.list");
            return summaries;
        }

        @Override
        public long countArtifacts(ArtifactScope scope) {
            return summaries.size();
        }

        @Override
        public ArtifactAccess requestAccess(ArtifactScope scope, ArtifactId artifactId) {
            events.add("service.access");
            return access;
        }
    }

    private static final class ArtifactApplicationQueryStub
            implements com.example.platform.artifact.app.ArtifactApplicationQuery {
        @Override
        public List<com.example.platform.artifact.domain.Artifact> findArtifacts(
                ArtifactScope scope, int limit) { return List.of(); }

        @Override
        public long countArtifacts(ArtifactScope scope) { return 0; }

        @Override
        public java.util.Optional<com.example.platform.artifact.domain.Artifact> findArtifact(
                ArtifactScope scope, ArtifactId artifactId) { return java.util.Optional.empty(); }
    }

    private static final class ArtifactQueryServiceStub
            implements com.example.platform.artifact.domain.ArtifactQueryService {
        @Override
        public java.util.Optional<com.example.platform.artifact.domain.Artifact> getArtifact(
                String tenantId, ArtifactId artifactId) { return java.util.Optional.empty(); }
        @Override
        public List<com.example.platform.artifact.domain.ArtifactReplicaBinding> listReplicas(
                String tenantId, ArtifactId artifactId) { return List.of(); }
        @Override
        public java.util.Optional<com.example.platform.artifact.domain.ArtifactReplicaBinding> findReplica(
                String tenantId, ArtifactId artifactId,
                com.example.platform.storage.contract.StorageReplicaId replicaId) { return java.util.Optional.empty(); }
        @Override
        public List<ArtifactId> listParents(String tenantId, ArtifactId artifactId) { return List.of(); }
        @Override
        public List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId) { return List.of(); }
        @Override
        public List<com.example.platform.artifact.domain.ProvenanceEdge> getDirectProvenance(
                String tenantId, ArtifactId artifactId) { return List.of(); }
        @Override
        public List<ArtifactId> boundedAncestorTraversal(
                String tenantId, ArtifactId artifactId, int maxDepth) { return List.of(); }
        @Override
        public List<ArtifactId> boundedDescendantTraversal(
                String tenantId, ArtifactId artifactId, int maxDepth) { return List.of(); }
        @Override
        public List<com.example.platform.artifact.domain.Artifact> findByContentDigest(
                String tenantId, ContentDigest contentDigest, int limit) { return List.of(); }
    }
}
