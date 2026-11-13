package com.example.platform.web.artifact;

import com.example.platform.artifact.contract.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.artifact.app.ArtifactSourceFactsQuery;
import com.example.platform.artifact.app.ArtifactSourceCatalog;
import com.example.platform.entitlement.api.commercial.*;
import com.example.platform.entitlement.app.QuotaUsageAuthority;
import com.example.platform.entitlement.app.QuotaPolicyService;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.storage.api.*;
import com.example.platform.web.render.TimelineProjectAuthorizationService;
import java.io.IOException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

/** Authenticated source upload; Storage and Artifact retain their own issuance authorities. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/source-media")
public class SourceMediaUploadController {
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private final TimelineProjectAuthorizationService authorization;
    private final ProjectReadQuery projects;
    private final CanonicalActorResolver actors;
    private final CommercialAdmissionPort commercial;
    private final QuotaUsageAuthority quota;
    private final QuotaPolicyService quotaPolicies;
    private final StorageSourceUploadPort storage;
    private final ArtifactUploadAdmissionPort artifacts;
    private final ArtifactSourceFactsQuery sourceFacts;
    private final ArtifactSourceCatalog sourceCatalog;

    public SourceMediaUploadController(TimelineProjectAuthorizationService authorization,
            ProjectReadQuery projects, CanonicalActorResolver actors,
            CommercialAdmissionPort commercial, QuotaUsageAuthority quota,
            QuotaPolicyService quotaPolicies, StorageSourceUploadPort storage,
            ArtifactUploadAdmissionPort artifacts, ArtifactSourceFactsQuery sourceFacts,
            ArtifactSourceCatalog sourceCatalog) {
        this.authorization = authorization;
        this.projects = projects;
        this.actors = actors;
        this.commercial = commercial;
        this.quota = quota;
        this.quotaPolicies = quotaPolicies;
        this.storage = storage;
        this.artifacts = artifacts;
        this.sourceFacts = sourceFacts;
        this.sourceCatalog = sourceCatalog;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UploadResult upload(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("file") MultipartFile file) throws IOException {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bounded Idempotency-Key required");
        }
        authorization.requireWrite(tenantId, projectId);
        var actor = actors.resolveCurrentActor().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        var project = projects.getProject(tenantId, projectId);
        if (!tenantId.equals(project.tenantId()) || !projectId.equals(project.id())
                || project.workspaceId() == null || project.workspaceId().isBlank()
                || !"ACTIVE".equals(project.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "project scope unavailable");
        }
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source must contain at most 64 MiB");
        }
        // Reject malformed client metadata before quota, storage, or Artifact side effects.
        displayName(file.getOriginalFilename(), "pending");
        String mime = file.getContentType();
        final ArtifactOutputFormat format;
        try {
            format = ArtifactOutputFormat.require(mime);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported source MIME");
        }
        if (format.mediaType() != ArtifactMediaType.VIDEO && format.mediaType() != ArtifactMediaType.AUDIO) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "timed video or audio required");
        }
        byte[] bytes = file.getBytes();
        try {
            format.validate(bytes);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "source bytes do not match MIME");
        }
        Instant now = Instant.now();
        YearMonth month = YearMonth.from(now.atZone(ZoneOffset.UTC));
        String trace = "source-upload:" + projectId + ":" + idempotencyKey;
        var admissionRequest = new CommercialAdmissionRequest(
                PrincipalRef.tenantScoped(tenantId, PrincipalType.ORGANIZATION, tenantId),
                "artifact.source.upload", "artifact.source.upload", "artifact.source.upload",
                bytes.length, month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(), trace, now);
        var decision = commercial.decideForAcceptance(admissionRequest);
        // The quota authority performs the atomic limit check and recognizes retries.
        // A preliminary quota denial can include a previous successful use of this key.
        if (!decision.allowed() && decision.reason() != CommercialDecisionReason.QUOTA_EXCEEDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "source upload admission denied");
        }
        var quotaResult = quota.execute(new QuotaUsageCommand(
                admissionRequest.principal(), admissionRequest.quotaKey(),
                admissionRequest.periodStart(), admissionRequest.periodEnd(), bytes.length,
                quotaPolicies.getQuotaPolicy(admissionRequest.quotaKey()).limitValue().longValueExact(),
                trace, QuotaOperationKind.CONSUMPTION, decision.traceId(),
                "source media upload", now));
        if (!quotaResult.applied()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "source upload quota exceeded");
        }
        var owner = new StorageOwnershipScope(tenantId, projectId);
        var receiptKey = new IssuanceIdempotencyKey(trace);
        var written = storage.writeSource(owner, receiptKey, bytes, mime);
        var issuance = written.issuance();
        var placement = issuance.placement();
        var admission = new ArtifactUploadAdmission(
                ArtifactUploadAdmission.CURRENT_CONTRACT_VERSION,
                new ArtifactScope(tenantId, project.workspaceId(), projectId),
                new ArtifactStorageReference(issuance.objectId(), placement.replicaId(),
                        placement.location().providerId(), issuance.receipt().receiptId(), receiptKey.value()),
                placement.committedDigest(), placement.committedLength(),
                new ArtifactTechnicalMetadata(ArtifactKind.SOURCE_MEDIA, format.mediaType(),
                        Map.of("mimeType", mime)),
                new ArtifactAuditFacts(actor.actorId(), trace, now),
                new ArtifactLifecycleFacts(ArtifactState.AVAILABLE, now),
                new ArtifactLineageFacts(List.of(), "source-upload", 1),
                idempotencyKey, quotaResult.operationId());
        var result = artifacts.admit(admission);
        var acceptedFacts = sourceFacts.find(tenantId, projectId, result.artifactId(), placement.committedDigest())
                .orElseThrow(() -> new IllegalStateException("accepted source facts unavailable"));
        String displayName = displayName(file.getOriginalFilename(), result.artifactId().value());
        sourceCatalog.recordDisplayName(tenantId, projectId, result.artifactId(), placement.committedDigest(), displayName);
        return new UploadResult(result.artifactId().value(), result.artifactId().value(), displayName,
                placement.committedDigest().canonicalValue(), placement.committedLength(),
                acceptedFacts.duration().toString(), acceptedFacts.streams().stream()
                        .map(stream -> new SourceStream(stream.streamId(), stream.kind().name(),
                                stream.duration().toString())).toList());
    }

    @GetMapping
    public List<UploadResult> list(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestParam(defaultValue = "100") int limit) {
        authorization.requireRead(tenantId, projectId);
        return sourceCatalog.list(tenantId, projectId, limit).stream()
                .map(item -> new UploadResult(item.artifactId().value(), item.artifactId().value(), item.name(),
                        item.digest().canonicalValue(), item.byteLength(), item.facts().duration().toString(),
                        item.facts().streams().stream().map(stream -> new SourceStream(stream.streamId(),
                                stream.kind().name(), stream.duration().toString())).toList()))
                .toList();
    }

    private static String displayName(String raw, String artifactId) {
        if (raw == null || raw.isBlank()) return artifactId;
        String base = raw.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).trim();
        if (base.isBlank()) return artifactId;
        if (base.length() > 256 || base.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid source file name");
        }
        return base;
    }

    public record UploadResult(String artifactId, String mediaAssetId, String name, String contentDigest, long byteLength,
                               String duration, List<SourceStream> streams) {}
    public record SourceStream(String streamId, String kind, String duration) {}
}
