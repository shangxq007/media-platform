package com.example.platform.ingest.preflight.persistence.read;

import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.context.annotation.Profile("dev")
@RequestMapping("/dev/tenants/{tenantId}/projects/{projectId}/ingest/preflight/safe-reports")
public class DevSafePreflightReportReadController {

    private final SafePreflightReportReadService service;

    public DevSafePreflightReportReadController(SafePreflightReportReadService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SafePreflightReportRecordListResponse> listRecords(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestParam(required = false) String rawMediaProductId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        if (limit < 1 || limit > 100) {
            limit = 50;
        }

        var response = service.listRecords(tenantId, projectId, rawMediaProductId, limit, offset);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{recordId}")
    public ResponseEntity<SafePreflightReportRecordDetailResponse> getRecord(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @PathVariable Long recordId) {

        Optional<SafePreflightReportRecordDetailResponse> record = service.getRecord(tenantId, projectId, recordId);
        return record.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
