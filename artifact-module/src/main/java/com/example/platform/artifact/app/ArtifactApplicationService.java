package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Canonical application boundary for Artifact discovery and explicit access.
 * Authorization remains owned by the platform identity/access boundary; this
 * service enforces the established request tenant and database scope before any
 * authenticated relative content route is returned.
 */
@Service
public class ArtifactApplicationService {

    private static final int MAX_PAGE_SIZE = 200;

    private final ArtifactApplicationQuery applicationQuery;
    private final ArtifactQueryService artifactQueryService;

    public ArtifactApplicationService(
            ArtifactApplicationQuery applicationQuery,
            ArtifactQueryService artifactQueryService) {
        this.applicationQuery = applicationQuery;
        this.artifactQueryService = artifactQueryService;
    }

    public List<ArtifactSummary> listArtifacts(ArtifactScope scope, int requestedLimit) {
        requireScope(scope);
        int limit = Math.min(MAX_PAGE_SIZE, Math.max(1, requestedLimit));
        return applicationQuery.findArtifacts(scope, limit).stream()
                .map(ArtifactApplicationService::toSummary)
                .toList();
    }

    public long countArtifacts(ArtifactScope scope) {
        requireScope(scope);
        return applicationQuery.countArtifacts(scope);
    }

    public ArtifactAccess requestAccess(ArtifactScope scope, ArtifactId artifactId) {
        requireScope(scope);
        Artifact artifact = applicationQuery.findArtifact(scope, artifactId)
                .orElseThrow(() -> new ArtifactAccessException(
                        ArtifactAccessFailure.NOT_FOUND, "Artifact not found in the requested scope"));
        if (artifact.state() != ArtifactState.AVAILABLE) {
            throw new ArtifactAccessException(
                    ArtifactAccessFailure.NOT_AVAILABLE, "Artifact is not available for access");
        }

        var replicas = artifactQueryService.listReplicas(scope.tenantId(), artifactId);
        if (replicas.isEmpty()) {
            throw new ArtifactAccessException(
                    ArtifactAccessFailure.NOT_AVAILABLE, "Artifact has no usable replica");
        }
        String path="/api/tenants/"+segment(scope.tenantId())+"/projects/"+segment(scope.projectId())+"/render-jobs/"+segment(scope.renderJobId())+"/artifacts/"+segment(artifactId.value())+"/content";
        return new ArtifactAccess(artifactId,java.net.URI.create(path),null);
    }

    private static String segment(String id) {
        return org.springframework.web.util.UriUtils.encodePathSegment(id,java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void requireScope(ArtifactScope scope) {
        TenantGuard.assertSameTenant(scope.tenantId());
    }

    private static ArtifactSummary toSummary(Artifact artifact) {
        return new ArtifactSummary(
                artifact.artifactId(),
                artifact.mediaType(),
                artifact.artifactKind(),
                artifact.contentDigest(),
                artifact.byteLength(),
                artifact.state(),
                integrityState(artifact.state()),
                artifact.createdAt());
    }

    private static ArtifactIntegrityState integrityState(ArtifactState state) {
        return switch (state) {
            case AVAILABLE, REGISTERING -> ArtifactIntegrityState.DIGEST_RECORDED;
            case QUARANTINED -> ArtifactIntegrityState.QUARANTINED;
            case FAILED -> ArtifactIntegrityState.FAILED;
            case DELETING, DELETED -> ArtifactIntegrityState.UNAVAILABLE;
        };
    }

    public enum ArtifactAccessFailure {
        NOT_FOUND,
        NOT_AVAILABLE,
        UNSUPPORTED,
        ACCESS_FAILED
    }

    public static final class ArtifactAccessException extends RuntimeException {
        private final ArtifactAccessFailure failure;

        public ArtifactAccessException(ArtifactAccessFailure failure, String message) {
            super(message);
            this.failure = failure;
        }

        public ArtifactAccessFailure failure() {
            return failure;
        }
    }
}
