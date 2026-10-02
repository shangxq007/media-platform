package com.example.platform.coverimage;

import com.example.platform.contract.media.CoverImageContracts;

import com.example.platform.shared.web.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adaptation only: admission and status. Provider execution stays in the cover worker. */
@RestController
@RequestMapping("/api/projects/{projectId}/cover-images")
public class CoverImageController {

    private final CoverImageService service;

    public CoverImageController(CoverImageService service) {
        this.service = service;
    }

    public record CreateRequest(
            String subjectArtifactId,
            double timestampSeconds,
            String imageFormat,
            Integer width,
            Integer quality,
            String idempotencyKey) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CoverImageContracts.Result submit(
            @PathVariable String projectId, @RequestBody CreateRequest body) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("tenant context is required");
        }
        return service.submit(new CoverImageContracts.Request(
                tenantId,
                projectId,
                body.subjectArtifactId(),
                body.timestampSeconds(),
                body.imageFormat(),
                body.width(),
                body.quality(),
                body.idempotencyKey()));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<CoverImageContracts.Result> status(
            @PathVariable String projectId, @PathVariable String taskId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("tenant context is required");
        }
        return ResponseEntity.of(service.status(tenantId, projectId, taskId));
    }
}
