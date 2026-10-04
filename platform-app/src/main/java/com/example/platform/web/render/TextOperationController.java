package com.example.platform.web.render;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.app.operation.TextOperationService;
import com.example.platform.render.app.operation.TextOperationService.TextOperationPreview;
import com.example.platform.render.app.operation.TextOperationService.TextOperationResult;
import com.example.platform.render.app.operation.TimelineOperationException;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.net.URI;
import java.util.List;
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

/**
 * P2-5.5 canonical caption write path — HTTP boundary for the nine
 * timeline.text.* operations (add/remove/replace-content/set-style-range/
 * set-paragraph-style/set-font-selection/set-font-fallback-policy/
 * set-variable-font-axis/set-layout), each with a preview and an apply endpoint.
 *
 * <p>Translates transport fields into typed canonical intent and delegates all
 * mutation to {@link TextOperationService}. No provider/backend/storage internals
 * are exposed. No legacy migration flag applies to text ops.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/timeline-operations/text-elements")
public class TextOperationController {

    private final TextOperationService textOperationService;
    private final CanonicalActorResolver actorResolver;

    public TextOperationController(
            TextOperationService textOperationService,
            CanonicalActorResolver actorResolver) {
        this.textOperationService = textOperationService;
        this.actorResolver = actorResolver;
    }

    // ==================== add ====================

    @PostMapping("/add/preview")
    public TextOperationPreview previewAdd(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.AddTextElementRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/add/apply")
    public ResponseEntity<TextOperationResult> applyAdd(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.AddTextElementRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== remove ====================

    @PostMapping("/remove/preview")
    public TextOperationPreview previewRemove(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.RemoveTextElementRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/remove/apply")
    public ResponseEntity<TextOperationResult> applyRemove(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.RemoveTextElementRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== replace-content ====================

    @PostMapping("/replace-content/preview")
    public TextOperationPreview previewReplaceContent(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.ReplaceTextContentRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/replace-content/apply")
    public ResponseEntity<TextOperationResult> applyReplaceContent(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.ReplaceTextContentRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== set-style-range ====================

    @PostMapping("/set-style-range/preview")
    public TextOperationPreview previewSetStyleRange(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetTextStyleRangeRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/set-style-range/apply")
    public ResponseEntity<TextOperationResult> applySetStyleRange(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetTextStyleRangeRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== set-paragraph-style ====================

    @PostMapping("/set-paragraph-style/preview")
    public TextOperationPreview previewSetParagraphStyle(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetParagraphStyleRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/set-paragraph-style/apply")
    public ResponseEntity<TextOperationResult> applySetParagraphStyle(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetParagraphStyleRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== set-font-selection ====================

    @PostMapping("/set-font-selection/preview")
    public TextOperationPreview previewSetFontSelection(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetFontSelectionRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/set-font-selection/apply")
    public ResponseEntity<TextOperationResult> applySetFontSelection(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetFontSelectionRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== set-font-fallback-policy ====================

    @PostMapping("/set-font-fallback-policy/preview")
    public TextOperationPreview previewSetFontFallbackPolicy(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetFontFallbackPolicyRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/set-font-fallback-policy/apply")
    public ResponseEntity<TextOperationResult> applySetFontFallbackPolicy(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetFontFallbackPolicyRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== set-variable-font-axis ====================

    @PostMapping("/set-variable-font-axis/preview")
    public TextOperationPreview previewSetVariableFontAxis(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetVariableFontAxisRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/set-variable-font-axis/apply")
    public ResponseEntity<TextOperationResult> applySetVariableFontAxis(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetVariableFontAxisRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== set-layout ====================

    @PostMapping("/set-layout/preview")
    public TextOperationPreview previewSetLayout(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetTextLayoutRequest request) {
        return preview(tenantId, projectId, request);
    }

    @PostMapping("/set-layout/apply")
    public ResponseEntity<TextOperationResult> applySetLayout(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody TextOperationRequests.SetTextLayoutRequest request) {
        return apply(tenantId, projectId, request);
    }

    // ==================== shared ====================

    private TextOperationPreview preview(String tenantId, String projectId,
            TextOperationRequests.TextOperationRequestFields request) {
        requireTransportId(tenantId, "tenantId");
        requireTransportId(projectId, "projectId");
        if (request == null) {
            throw new TextTransportException("request required");
        }
        return textOperationService.preview(
                tenantId, projectId, request.toRequest(projectId), authenticatedActor(tenantId));
    }

    private ResponseEntity<TextOperationResult> apply(String tenantId, String projectId,
            TextOperationRequests.TextOperationRequestFields request) {
        requireTransportId(tenantId, "tenantId");
        requireTransportId(projectId, "projectId");
        if (request == null) {
            throw new TextTransportException("request required");
        }
        requireDigest(request.expectedPlanDigest(), "expectedPlanDigest");
        requireTransportId(request.applyCommandId(), "applyCommandId");
        var actor = authenticatedActor(tenantId);
        TextOperationResult applied = textOperationService.authorizeAndApply(
                tenantId, projectId, request.toRequest(projectId),
                request.expectedPlanDigest(), request.applyCommandId(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(applied);
    }

    private CanonicalActor authenticatedActor(String explicitTenantId) {
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        String ambientTenantId = TenantContext.get();
        requireBoundedText(actor.actorId(), "principalRef", 128);
        if (!java.util.Objects.equals(explicitTenantId, ambientTenantId)
                || !java.util.Objects.equals(explicitTenantId, actor.tenantId())) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("explicit, ambient and authenticated tenant must match"));
        }
        return actor;
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

    @ExceptionHandler(TextTransportException.class)
    ResponseEntity<ProblemDetail> malformedInput(TextTransportException failure) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_INPUT", failure.getMessage(), List.of());
    }

    private static void requireTransportId(String value, String field) {
        requireBoundedText(value, field, 64);
        if (!value.matches("[A-Za-z0-9._:-]+")) {
            throw new TextTransportException(field + " has invalid syntax");
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new TextTransportException(field + " must be a 64-character hex digest");
        }
    }

    private static void requireBoundedText(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new TextTransportException(field + " required and bounded to " + maximum);
        }
    }

    private static final class TextTransportException extends RuntimeException {
        private TextTransportException(String message) {
            super(message);
        }
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail, List<String> failures) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:media-platform:timeline-text-operation:" + code.toLowerCase()));
        problem.setTitle("Timeline text operation failed");
        problem.setProperty("errorCode", code);
        problem.setProperty("failures", failures);
        return ResponseEntity.status(status).body(problem);
    }
}
