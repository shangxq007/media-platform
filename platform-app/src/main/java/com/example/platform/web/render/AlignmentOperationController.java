package com.example.platform.web.render;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.operation.application.AlignmentOperationService;
import com.example.platform.operation.application.TimelineOperationException;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.semantics.relationship.AlignmentAttribute;
import com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * L5 lineage alignment operation projection (CREATE_ALIGNMENT). The controller translates transport
 * fields into the typed canonical intent and delegates all mutation to the application layer; the
 * sole apply boundary remains {@code OperationPlanApplyService}.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/alignment-operations")
public class AlignmentOperationController {

    private final AlignmentOperationService operationService;
    private final CanonicalActorResolver actorResolver;

    public AlignmentOperationController(AlignmentOperationService operationService,
                                        CanonicalActorResolver actorResolver) {
        this.operationService = operationService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/create/preview")
    public AlignmentOperationService.AlignmentPreview preview(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody CreateAlignmentRequest request) {
        requireTransportId(tenantId, "tenantId");
        requireTransportId(projectId, "projectId");
        return operationService.preview(tenantId, projectId, request.toCommand(), authenticatedActor(tenantId));
    }

    @PostMapping("/create/apply")
    public ResponseEntity<AlignmentOperationService.AlignmentResult> apply(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody ApplyAlignmentRequest request) {
        requireTransportId(tenantId, "tenantId");
        requireTransportId(projectId, "projectId");
        var actor = authenticatedActor(tenantId);
        AlignmentOperationService.AlignmentResult applied = operationService.authorizeAndApply(
                tenantId, projectId, request.request().toCommand(),
                request.expectedPlanDigest(), request.applyCommandId(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(applied);
    }

    private CanonicalActor authenticatedActor(String explicitTenantId) {
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        String ambientTenantId = TenantContext.get();
        requireBoundedText(actor.actorId(), "principalRef", 128);
        if (!Objects.equals(explicitTenantId, ambientTenantId)
                || !Objects.equals(explicitTenantId, actor.tenantId())) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("explicit, ambient and authenticated tenant must match"));
        }
        return actor;
    }

    /** Transport shape for a typed alignment attribute (explicit value kind; no free-form map). */
    public record AttributeDto(String tag, String kind, String value) {
        public AttributeDto {
            requireTransportId(tag, "attribute.tag");
            requireBoundedText(kind, "attribute.kind", 16);
            requireBoundedText(value, "attribute.value", 256);
        }

        AlignmentAttribute toAttribute() {
            AlignmentAttributeValue typed = switch (kind) {
                case "TEXT" -> new AlignmentAttributeValue.TextValue(value);
                case "NUMBER" -> new AlignmentAttributeValue.NumberValue(Long.parseLong(value));
                case "BOOLEAN" -> new AlignmentAttributeValue.BooleanValue(Boolean.parseBoolean(value));
                default -> throw new AlignmentTransportException("attribute.kind must be TEXT/NUMBER/BOOLEAN");
            };
            return new AlignmentAttribute(tag, typed);
        }
    }

    /**
     * Transport DTO for CREATE_ALIGNMENT. {@code baseContentHash} is the canonical Timeline content
     * digest (the revision's committed content hash), validated as bounded text — it is not a raw
     * 64-hex value.
     */
    public record CreateAlignmentRequest(
            String baseRevisionId,
            String baseContentHash,
            String alignmentId,
            List<String> sourceUnits,
            List<String> derivedUnits,
            List<AttributeDto> attributes) {

        public CreateAlignmentRequest {
            requireTransportId(baseRevisionId, "baseRevisionId");
            requireBoundedText(baseContentHash, "baseContentHash", 128);
            requireTransportId(alignmentId, "alignmentId");
            if (sourceUnits == null || sourceUnits.isEmpty()
                    || derivedUnits == null || derivedUnits.isEmpty()) {
                throw new AlignmentTransportException("sourceUnits and derivedUnits must be non-empty");
            }
            sourceUnits.forEach(u -> requireTransportId(u, "sourceUnit"));
            derivedUnits.forEach(u -> requireTransportId(u, "derivedUnit"));
        }

        AlignmentOperationService.CreateAlignmentCommand toCommand() {
            TreeSet<SourceUnitId> sources = new TreeSet<>();
            sourceUnits.forEach(u -> sources.add(SourceUnitId.of(u)));
            TreeSet<SourceUnitId> derived = new TreeSet<>();
            derivedUnits.forEach(u -> derived.add(SourceUnitId.of(u)));
            List<AlignmentAttribute> attrs = attributes == null
                    ? List.of()
                    : attributes.stream().map(AttributeDto::toAttribute).toList();
            return new AlignmentOperationService.CreateAlignmentCommand(
                    AlignmentId.of(alignmentId), sources, derived, attrs, baseRevisionId, baseContentHash);
        }
    }

    public record ApplyAlignmentRequest(
            CreateAlignmentRequest request,
            String expectedPlanDigest,
            String applyCommandId) {
        public ApplyAlignmentRequest {
            if (request == null) {
                throw new AlignmentTransportException("request required");
            }
            requireBoundedText(expectedPlanDigest, "expectedPlanDigest", 128);
            requireTransportId(applyCommandId, "applyCommandId");
        }
    }

    @ExceptionHandler(TimelineOperationException.class)
    ResponseEntity<ProblemDetail> operationFailure(TimelineOperationException failure) {
        HttpStatus status = switch (failure.code()) {
            case STALE_BASE_REVISION, STALE_TARGET_REF, PLAN_CHANGED -> HttpStatus.CONFLICT;
            case AUTHORIZATION_DENIED, AUTHORIZATION_CONTEXT_MISMATCH, TENANT_CONTEXT_MISMATCH ->
                    HttpStatus.FORBIDDEN;
            case BASE_REVISION_NOT_FOUND, TARGET_MISSING -> HttpStatus.NOT_FOUND;
            case IDEMPOTENCY_KEY_CONFLICT -> HttpStatus.CONFLICT;
            case SOURCE_REFERENCE_INVALID, CANDIDATE_INVALID, INVALID_PLAN,
                    UNSUPPORTED_TEMPORAL_STATE, UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR,
                    SYNC_ANCHOR_INVALIDATED, GROUP_CARDINALITY_CONFLICT,
                    PLACEMENT_CONFLICT, BATCH_CONFLICT, CANONICAL_INVARIANT_VIOLATION ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case PERSISTENCE_FAILURE, REF_UPDATE_FAILURE, APPLY_UNKNOWN_FAILURE ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return problem(status, failure.code().name(), failure.getMessage(), failure.failures());
    }

    @ExceptionHandler(AlignmentTransportException.class)
    ResponseEntity<ProblemDetail> malformedInput(AlignmentTransportException failure) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_INPUT", failure.getMessage(), List.of());
    }

    private static void requireTransportId(String value, String field) {
        requireBoundedText(value, field, 64);
        if (!value.matches("[A-Za-z0-9._:-]+")) {
            throw new AlignmentTransportException(field + " has invalid syntax");
        }
    }

    private static void requireBoundedText(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new AlignmentTransportException(field + " required and bounded to " + maximum);
        }
    }

    static final class AlignmentTransportException extends RuntimeException {
        AlignmentTransportException(String message) {
            super(message);
        }
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail, List<String> failures) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:media-platform:alignment-operation:" + code.toLowerCase()));
        problem.setTitle("Alignment operation failed");
        problem.setProperty("errorCode", code);
        problem.setProperty("failures", failures);
        return ResponseEntity.status(status).body(problem);
    }
}
