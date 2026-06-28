package com.example.platform.render.api;

import com.example.platform.render.api.dto.*;
import com.example.platform.render.app.caption.CaptionTemplateRenderService;
import com.example.platform.render.app.caption.CaptionTemplateResultLookupService;
import com.example.platform.render.app.timeline.compile.RenderCorrelationContext;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.domain.caption.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for Caption Template Render API.
 *
 * <p>Internal-safe API boundary. Does not expose provider/backend/storage internals.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class CaptionTemplateRenderController {

    private static final Logger log = LoggerFactory.getLogger(CaptionTemplateRenderController.class);

    private final CaptionTemplateRenderService service;
    private final CaptionTemplateResultLookupService lookupService;
    private final CaptionTemplateRenderApiMapper mapper;
    private final RenderAuditRecorder auditRecorder;

    public CaptionTemplateRenderController(
            CaptionTemplateRenderService service,
            CaptionTemplateResultLookupService lookupService,
            CaptionTemplateRenderApiMapper mapper,
            RenderAuditRecorder auditRecorder) {
        this.service = service;
        this.lookupService = lookupService;
        this.mapper = mapper;
        this.auditRecorder = auditRecorder;
    }

    @PostMapping("/tenants/{tenantId}/projects/{projectId}/caption-template/render")
    public ResponseEntity<CaptionTemplateRenderApiResponse> render(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @Valid @RequestBody CaptionTemplateRenderApiRequest request) {

        log.info("Caption template render requested: project={}", projectId);

        // Create correlation context
        RenderCorrelationContext correlation = RenderCorrelationContext.create(
                projectId, null, "PLAN_BASED");

        // Map to domain request
        CaptionTemplateRenderRequest domainRequest = mapper.toDomainRequest(projectId, request);

        // Audit: requested
        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.CAPTION_TEMPLATE_RENDER_REQUESTED)
                .fromCorrelation(correlation)
                .projectId(projectId)
                .message("Caption template render requested")
                .build());

        // Validate and render
        CaptionTemplateRenderResult result = service.render(domainRequest);

        // Audit: result
        if (result.hasValidationErrors()) {
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.CAPTION_TEMPLATE_RENDER_VALIDATION_FAILED)
                    .fromCorrelation(correlation)
                    .projectId(projectId)
                    .message("Validation failed: " + result.validationErrors().size() + " error(s)")
                    .build());
        } else if (result.isSuccess()) {
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.CAPTION_TEMPLATE_RENDER_COMPLETED)
                    .fromCorrelation(correlation)
                    .projectId(projectId)
                    .renderJobId(result.renderJobId())
                    .outputProductId(result.outputProductId())
                    .message("Caption template render completed")
                    .build());
        } else {
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.CAPTION_TEMPLATE_RENDER_FAILED)
                    .severity(RenderAuditEventSeverity.ERROR)
                    .fromCorrelation(correlation)
                    .projectId(projectId)
                    .message("Caption template render failed: " + result.safeMessage())
                    .build());
        }

        // Map to API response
        CaptionTemplateRenderApiResponse response = mapper.toApiResponse(result);

        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else if (result.hasValidationErrors()) {
            return ResponseEntity.badRequest().body(response);
        } else {
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/tenants/{tenantId}/projects/{projectId}/caption-template/results/{outputProductId}")
    public ResponseEntity<CaptionTemplateRenderResultLookupResponse> lookupResult(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable String outputProductId) {

        RenderCorrelationContext correlation = RenderCorrelationContext.create(
                projectId, null, "PLAN_BASED");

        auditRecorder.record(RenderAuditEvent.builder()
                .eventType(RenderAuditEventType.CAPTION_TEMPLATE_RESULT_LOOKUP_REQUESTED)
                .fromCorrelation(correlation)
                .projectId(projectId)
                .outputProductId(outputProductId)
                .message("Result lookup requested")
                .build());

        CaptionTemplateRenderResultLookupResponse result = lookupService.lookup(outputProductId);

        if (result.status() == CaptionTemplateDeliveryStatus.READY) {
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.CAPTION_TEMPLATE_RESULT_LOOKUP_COMPLETED)
                    .fromCorrelation(correlation)
                    .projectId(projectId)
                    .outputProductId(outputProductId)
                    .message("Result lookup completed: READY")
                    .build());
            return ResponseEntity.ok(result);
        } else if (result.status() == CaptionTemplateDeliveryStatus.NOT_FOUND) {
            auditRecorder.record(RenderAuditEvent.builder()
                    .eventType(RenderAuditEventType.CAPTION_TEMPLATE_RESULT_LOOKUP_FAILED)
                    .severity(RenderAuditEventSeverity.WARN)
                    .fromCorrelation(correlation)
                    .projectId(projectId)
                    .outputProductId(outputProductId)
                    .message("Result lookup: not found")
                    .build());
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(result);
        }
    }
}
